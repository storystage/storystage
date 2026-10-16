/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.ui;

import com.anthropic.client.AnthropicClient;
import org.alice.ide.IDE;
import org.alice.ide.ProjectDocument;
import org.alice.ide.project.ProjectDocumentState;
import org.lgna.croquet.Application;
import org.lgna.croquet.edits.Edit;
import org.lgna.croquet.undo.UndoHistory;
import org.lgna.project.Project;
import org.storystage.prompter.claude.ClaudeClients;
import org.storystage.prompter.claude.PrompterConversation;
import org.storystage.prompter.claude.SystemPrompt;
import org.storystage.prompter.claude.ToolExecutor;
import org.storystage.prompter.context.ApiCatalog;
import org.storystage.prompter.context.NodeIds;
import org.storystage.prompter.context.ProjectText;
import org.storystage.prompter.key.KeyStores;
import org.storystage.prompter.settings.PrompterPolicy;
import org.storystage.prompter.tools.GalleryCatalog;
import org.storystage.prompter.tools.ToolSchemas;
import org.storystage.prompter.tools.WorldTools;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Connects the panel to the open world: builds the system prompt and tools once, keeps one
 * conversation per world, sends requests on a background thread and reports back on the Swing
 * thread. See docs/spec/00-architecture.md §4.
 */
final class PrompterSession {
  /** What the panel shows. All calls arrive on the Swing thread. */
  interface View {
    void prompterText(String delta);

    void stepFinished(String toolName, ToolExecutor.Result result);

    void requestFinished(PrompterConversation.Outcome outcome, String message);

    void usageChanged(String summary);
  }

  // Claude Opus 5.5 list prices in US dollars per million tokens (spec 02 §3). Estimates only.
  private static final double INPUT = 4.0;
  private static final double OUTPUT = 20.0;
  private static final double CACHE_READ = 0.20;
  private static final double CACHE_WRITE = 5.0;

  private final PrompterPolicy policy;
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "StoryStage Prompter");
    t.setDaemon(true);
    return t;
  });
  private String systemPrompt;
  private GalleryCatalog gallery;
  private Project project;
  private NodeIds ids;
  private WorldTools tools;
  private AnthropicClient client;
  private PrompterConversation conversation;
  private int requests;
  private double dollars;

  PrompterSession(PrompterPolicy policy) {
    this.policy = policy;
  }

  /** Sends a request. Call on the Swing thread. Returns false if there is no key or no world. */
  boolean send(String request, PrompterConversation.Mode mode, View view) {
    Optional<String> key = KeyStores.get().read();
    Project current = IDE.getActiveInstance() != null ? IDE.getActiveInstance().getProject() : null;
    if (key.isEmpty() || current == null) {
      return false;
    }
    if (this.conversation == null || current != this.project) {
      this.start(current, key.get());
    }
    PrompterConversation.Mode effective = this.policy.isExplainOnly() ? PrompterConversation.Mode.EXPLAIN_ONLY : mode;
    String projectText = ProjectText.render(current, this.ids);
    this.tools.clearCommittedEdits();
    this.tools.setRequestExplainOnly(effective == PrompterConversation.Mode.EXPLAIN_ONLY);
    PrompterConversation c = this.conversation;
    this.worker.submit(() -> c.send(request, projectText, null, effective, new PrompterConversation.Listener() {
      @Override
      public void textDelta(String text) {
        SwingUtilities.invokeLater(() -> view.prompterText(text));
      }

      @Override
      public void toolStarted(String toolName) {
      }

      @Override
      public void toolFinished(String toolName, ToolExecutor.Result result) {
        SwingUtilities.invokeLater(() -> view.stepFinished(toolName, result));
      }

      @Override
      public void finished(PrompterConversation.Outcome outcome, String message) {
        SwingUtilities.invokeLater(() -> view.requestFinished(outcome, message));
      }

      @Override
      public void usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        double cost = (inputTokens * INPUT + outputTokens * OUTPUT + cacheReadTokens * CACHE_READ + cacheWriteTokens * CACHE_WRITE) / 1_000_000.0;
        SwingUtilities.invokeLater(() -> {
          PrompterSession.this.dollars += cost;
          view.usageChanged(PrompterSession.this.usageSummary());
        });
      }
    }));
    this.requests++;
    return true;
  }

  private void start(Project current, String key) {
    this.close();
    if (this.systemPrompt == null) {
      this.gallery = GalleryCatalog.load();
      this.systemPrompt = SystemPrompt.build(ApiCatalog.render(), this.gallery.render());
    }
    this.project = current;
    this.ids = new NodeIds();
    this.tools = new WorldTools(this.ids, this.gallery, this.policy.isExplainOnly());
    this.client = ClaudeClients.forRequests(key);
    List<com.anthropic.models.messages.Tool> toolList = ToolSchemas.tools(this.policy.isExplainOnly());
    this.conversation = new PrompterConversation(this.client, this.systemPrompt, toolList, this.tools);
  }

  void stop() {
    if (this.conversation != null) {
      this.conversation.stop();
    }
  }

  void newConversation() {
    if (this.conversation != null) {
      this.conversation.clear();
    }
  }

  /** True when the last request's edits are still the newest in the undo history. */
  boolean canUndoLastRequest() {
    return this.undoableCount() > 0;
  }

  /** Undoes the last request's edits, newest first. Returns how many were undone. */
  int undoLastRequest() {
    int count = this.undoableCount();
    UndoHistory history = history();
    for (int i = 0; i < count; i++) {
      history.performUndo();
    }
    if (count > 0) {
      this.tools.clearCommittedEdits();
    }
    return count;
  }

  private int undoableCount() {
    if (this.tools == null) {
      return 0;
    }
    List<Edit> edits = this.tools.getCommittedEdits();
    UndoHistory history = history();
    if (edits.isEmpty() || history == null) {
      return 0;
    }
    int index = history.getInsertionIndex();
    for (int i = edits.size() - 1; i >= 0; i--) {
      index--;
      if (index < 0 || history.getStack().get(index) != edits.get(i)) {
        return 0;
      }
    }
    return edits.size();
  }

  private static UndoHistory history() {
    ProjectDocument document = ProjectDocumentState.getInstance().getValue();
    return document != null ? document.getUndoHistory(Application.PROJECT_GROUP) : null;
  }

  String usageSummary() {
    return String.format("This session: %d request%s, about $%.2f", this.requests, this.requests == 1 ? "" : "s", this.dollars);
  }

  void close() {
    if (this.client != null) {
      this.client.close();
      this.client = null;
    }
    this.conversation = null;
  }
}
