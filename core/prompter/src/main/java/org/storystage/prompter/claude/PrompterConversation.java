/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import org.storystage.prompter.key.ApiKeys;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * One Prompter conversation for an open world: streams Claude's replies, runs the tools it calls,
 * sends the results back, and repeats until Claude has finished or the learner presses Stop. See
 * docs/spec/01-prompter-protocol.md §1.
 */
public final class PrompterConversation {
  /** How the Prompter should work for a request. */
  public enum Mode {
    SHOW_ME_HOW(OutputConfig.Effort.MEDIUM),
    BUILD_IT(OutputConfig.Effort.MEDIUM),
    EXPLAIN_ONLY(OutputConfig.Effort.LOW);

    private final OutputConfig.Effort effort;

    Mode(OutputConfig.Effort effort) {
      this.effort = effort;
    }
  }

  /** Progress reported while a request runs. Called on the request thread. */
  public interface Listener {
    void textDelta(String text);

    void toolStarted(String toolName);

    void toolFinished(String toolName, ToolExecutor.Result result);

    void finished(Outcome outcome, String message);

    /** Token counts for one reply, for the usage meter. */
    default void usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
    }
  }

  /** How a request ended; the message for each comes from spec 01 §5. */
  public enum Outcome {
    DONE, STOPPED, REFUSED, TOO_MANY_STEPS, OUT_OF_ROOM, KEY_REJECTED, NO_ACCESS, BUSY, CLAUDE_TROUBLE, OFFLINE, FAILED
  }

  static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
  static final int MAX_TOOL_CALLS = 25;
  static final long MAX_TOKENS = 32_000L;

  private final AnthropicClient client;
  private final String systemPrompt;
  private final List<Tool> tools;
  private final ToolExecutor executor;
  private final List<MessageParam> history = new ArrayList<>();
  private volatile boolean stopRequested;

  public PrompterConversation(AnthropicClient client, String systemPrompt, List<Tool> tools, ToolExecutor executor) {
    this.client = client;
    this.systemPrompt = systemPrompt;
    this.tools = tools;
    this.executor = executor;
  }

  /** Asks the running request to stop at the next opportunity. */
  public void stop() {
    this.stopRequested = true;
  }

  /** Starts again with an empty history. */
  public synchronized void clear() {
    this.history.clear();
  }

  public synchronized List<MessageParam> getHistory() {
    return Collections.unmodifiableList(new ArrayList<>(this.history));
  }

  /**
   * Sends one learner request with the current world and, optionally, a JPEG scene picture.
   * Blocking: call it off the Swing thread.
   */
  public synchronized Outcome send(String request, String projectText, byte[] sceneJpeg, Mode mode, Listener listener) {
    this.stopRequested = false;
    List<ContentBlockParam> content = new ArrayList<>();
    content.add(ContentBlockParam.ofText(TextBlockParam.builder()
        .text("Current world (Tweedle; /*#id*/ marks each block):\n```\n" + projectText + "\n```")
        .build()));
    if (sceneJpeg != null) {
      content.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
          .source(Base64ImageSource.builder()
              .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
              .data(Base64.getEncoder().encodeToString(sceneJpeg))
              .build())
          .build()));
    }
    content.add(ContentBlockParam.ofText(TextBlockParam.builder()
        .text("Mode: " + mode.name().toLowerCase().replace('_', ' ') + "\nRequest: " + request)
        .build()));
    this.history.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(content).build());

    int toolCalls = 0;
    while (true) {
      if (this.stopRequested) {
        return this.finish(listener, Outcome.STOPPED, "Stopped.");
      }
      Message message;
      try {
        message = this.stream(mode, listener);
      } catch (UnauthorizedException e) {
        return this.finish(listener, Outcome.KEY_REJECTED, "Your Claude key isn't working. It may have expired or been removed: create a new key in the Claude Console and run Set up again.");
      } catch (PermissionDeniedException e) {
        return this.finish(listener, Outcome.NO_ACCESS, "Your Claude key can't use the Prompter's model, or the account has run out of credit.");
      } catch (RateLimitException e) {
        return this.finish(listener, Outcome.BUSY, "The Prompter is busy. Try again in a minute.");
      } catch (InternalServerException e) {
        return this.finish(listener, Outcome.CLAUDE_TROUBLE, "Claude is having trouble right now. Try again in a minute.");
      } catch (AnthropicIoException e) {
        return this.finish(listener, this.stopRequested ? Outcome.STOPPED : Outcome.OFFLINE,
            this.stopRequested ? "Stopped." : "You're offline, so the Prompter can't help right now. Everything else in StoryStage still works.");
      } catch (AnthropicServiceException e) {
        return this.finish(listener, Outcome.CLAUDE_TROUBLE, "Claude couldn't answer (" + ApiKeys.scrub(e.getMessage()) + ").");
      } catch (RuntimeException e) {
        return this.finish(listener, Outcome.FAILED, "Something went wrong: " + ApiKeys.scrub(String.valueOf(e.getMessage())));
      }
      if (this.stopRequested) {
        return this.finish(listener, Outcome.STOPPED, "Stopped.");
      }
      this.history.add(message.toParam());

      StopReason reason = message.stopReason().orElse(StopReason.END_TURN);
      if (StopReason.REFUSAL.equals(reason)) {
        return this.finish(listener, Outcome.REFUSED, "The Prompter can't help with that one. Try a different idea.");
      }
      if (StopReason.MAX_TOKENS.equals(reason)) {
        return this.finish(listener, Outcome.OUT_OF_ROOM, "The Prompter ran out of room for this answer. Try asking for less at once.");
      }
      List<ToolUseBlock> toolUses = new ArrayList<>();
      for (ContentBlock block : message.content()) {
        block.toolUse().ifPresent(toolUses::add);
      }
      if (toolUses.isEmpty()) {
        return this.finish(listener, Outcome.DONE, null);
      }
      List<ContentBlockParam> results = new ArrayList<>();
      for (ToolUseBlock use : toolUses) {
        ToolExecutor.Result result;
        if (this.stopRequested) {
          result = ToolExecutor.Result.error("stopped", "The learner pressed Stop.");
        } else if (toolCalls >= MAX_TOOL_CALLS) {
          result = ToolExecutor.Result.error("limit_reached", "Too many steps in one request.");
        } else {
          toolCalls++;
          listener.toolStarted(use.name());
          result = this.runTool(use);
          listener.toolFinished(use.name(), result);
        }
        results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
            .toolUseId(use.id())
            .content(result.getJson())
            .isError(!result.isOk())
            .build()));
      }
      this.history.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build());
      if (toolCalls >= MAX_TOOL_CALLS) {
        return this.finish(listener, Outcome.TOO_MANY_STEPS, "The Prompter stopped after " + MAX_TOOL_CALLS + " steps. Ask it to carry on if you want more.");
      }
    }
  }

  private ToolExecutor.Result runTool(ToolUseBlock use) {
    JsonValue input = use._input();
    try {
      return this.executor.execute(use.name(), input);
    } catch (RuntimeException e) {
      return ToolExecutor.Result.error("would_break_world", "The step failed inside StoryStage: " + e.getClass().getSimpleName());
    }
  }

  private Message stream(Mode mode, Listener listener) {
    MessageCreateParams.Builder builder = MessageCreateParams.builder()
        .model(ClaudeClients.MODEL)
        .maxTokens(MAX_TOKENS)
        .systemOfTextBlockParams(Collections.singletonList(TextBlockParam.builder()
            .text(this.systemPrompt)
            .cacheControl(CacheControlEphemeral.builder().build())
            .build()))
        .messages(this.history)
        .outputConfig(OutputConfig.builder().effort(mode.effort).build())
        // A request Claude declines is retried by the server on a suitable model (spec 01 §1).
        .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    for (Tool tool : this.tools) {
      builder.addTool(tool);
    }
    MessageAccumulator accumulator = MessageAccumulator.create();
    try (StreamResponse<RawMessageStreamEvent> response = this.client.messages().createStreaming(builder.build())) {
      response.stream().forEach(event -> {
        if (this.stopRequested) {
          throw new StopException();
        }
        accumulator.accumulate(event);
        event.contentBlockDelta().flatMap(d -> d.delta().text()).ifPresent(t -> listener.textDelta(t.text()));
      });
    } catch (StopException e) {
      throw new AnthropicIoException("stopped", null);
    }
    Message message = accumulator.message();
    com.anthropic.models.messages.Usage usage = message.usage();
    listener.usage(usage.inputTokens(), usage.outputTokens(), usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
    return message;
  }

  private Outcome finish(Listener listener, Outcome outcome, String message) {
    listener.finished(outcome, message);
    return outcome;
  }

  private static final class StopException extends RuntimeException {
    StopException() {
      super(null, null, false, false);
    }
  }
}
