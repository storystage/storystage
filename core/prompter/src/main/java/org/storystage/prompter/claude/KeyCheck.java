/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.models.ModelInfo;
import org.storystage.prompter.key.ApiKeys;

/**
 * Checks a key by looking up the model the Prompter uses, which costs no tokens. See
 * docs/spec/02-keys-privacy-switches.md §1.
 */
public final class KeyCheck {
  /** What the check found, with the message shown in the set-up wizard. */
  public enum Result {
    OK("Your key works."),
    NOT_A_KEY("That doesn't look like a Claude API key. Keys start with sk-ant-."),
    REJECTED("That key wasn't accepted. Check you copied all of it, and that it hasn't expired, or create a new one."),
    NO_MODEL_ACCESS("That key works but can't use the Claude model StoryStage needs. Check the key's workspace."),
    UNREACHABLE("Couldn't reach Anthropic. Check the internet connection or ask about your school's proxy."),
    FAILED("Something went wrong checking the key. Try again in a minute.");

    private final String message;

    Result(String message) {
      this.message = message;
    }

    public String getMessage() {
      return this.message;
    }
  }

  private KeyCheck() {
    throw new AssertionError();
  }

  /** Runs the check. Blocking; call it off the Swing thread. */
  public static Result check(String key) {
    if (!ApiKeys.looksLikeKey(key)) {
      return Result.NOT_A_KEY;
    }
    AnthropicClient client = ClaudeClients.forKeyCheck(key);
    try {
      ModelInfo model = client.models().retrieve(ClaudeClients.MODEL);
      return ClaudeClients.MODEL.equals(model.id()) ? Result.OK : Result.NO_MODEL_ACCESS;
    } catch (UnauthorizedException e) {
      return Result.REJECTED;
    } catch (PermissionDeniedException | NotFoundException e) {
      return Result.NO_MODEL_ACCESS;
    } catch (AnthropicIoException e) {
      return Result.UNREACHABLE;
    } catch (RuntimeException e) {
      return Result.FAILED;
    } finally {
      client.close();
    }
  }
}
