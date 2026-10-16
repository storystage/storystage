# 00 Architecture

Implements workplan WP1.1. Covers how the Prompter fits into the Alice 3 code base.

## 1. Modules

| Module | Status | Contents |
|---|---|---|
| `core/ide` | Alice, small hooks only | Service interface `org.alice.ide.prompter.PrompterProvider`; panel slot in both perspectives; menu and preference wiring that does nothing when no provider is present |
| `core/prompter` | **new** | Everything AI: Claude client, key store, wizard, context builders, tools and executor, panel UI, speech |
| `alice-ide` | Alice | Adds `core/prompter` as a dependency inside a Maven profile `includePrompter` (active unless `-DincludePrompter=false`) |

`core/prompter` depends on `core/ide`; `core/ide` never depends on `core/prompter`. The IDE finds the Prompter at run time with `java.util.ServiceLoader<PrompterProvider>`. With no provider on the classpath (a build without the module), the IDE behaves exactly like Alice.

```java
// core/ide: org.alice.ide.prompter.PrompterProvider
public interface PrompterProvider {
  /** Called once on the Swing thread after the IDE frame exists. */
  void install(PrompterHost host);
}

// core/ide: what the IDE offers the Prompter
public interface PrompterHost {
  ProjectApplication getApplication();            // project, document frame, activities
  void setPanel(Composite<?> panel);              // shown in the panel slot, or null to hide
  void addPreferencesMenuItems(List<StandardMenuItemPrepModel> items);
  boolean isLockedOff();                          // admin/exam lock (spec 02)
}
```

## 2. Hooks in Alice code (kept minimal and marked `// StoryStage:`)

| File | Change |
|---|---|
| `org/alice/stageide/perspectives/code/CodePerspectiveComposite.java` | Trailing composite becomes a split: declarations editor, then the Prompter panel slot (collapsed when empty) |
| `org/alice/stageide/perspectives/SetupScenePerspective.java` (its main composite) | Same panel slot to the right of the scene editor's side panel |
| `org/alice/ide/croquet/models/menubar/PreferencesMenuModel.java` | Appends items supplied by the provider |
| `org/alice/stageide/StageIDE.java` | Loads providers after the frame is created and calls `install` |
| `org/alice/ide/issue/*` (issue reporter) | Scrubs anything matching an Anthropic key pattern before sending (spec 02) |

## 3. Package layout of `core/prompter`

```
org.storystage.prompter
  PrompterModule            ServiceLoader entry (implements PrompterProvider)
  settings/                 preference states, admin policy reader
  key/                      KeyStore interface, OS keyring and DPAPI implementations, wizard
  claude/                   client factory, request builder, streaming loop, usage meter
  context/                  API catalog, gallery catalog, project-to-Tweedle, screenshot
  tools/                    tool definitions (JSON schema), executor, one class per tool
  ui/                       panel composite and views
  speech/in/                microphone capture, whisper.cpp transcriber, model download
  speech/out/               TTS interface, Windows/macOS/Linux/Flite providers, narration queue
```

## 4. Threads

| Work | Thread |
|---|---|
| Panel UI, applying edits, reading the project | Swing event thread (Alice's AST and Croquet are not thread-safe) |
| Claude requests and streaming | One single-thread executor per open world |
| Context building that reads the AST | Swing thread, then handed to the request thread as immutable text |
| Screenshot | Renderer's own capture call, result handed over as bytes |
| Speech capture and transcription | Dedicated audio thread; transcription on a background executor |
| Speech output | One narration thread feeding the voice provider |

A tool call arrives on the request thread. It is executed with `SwingUtilities.invokeAndWait`, and its result is returned to the request thread for the next request.

## 5. How a Prompter step becomes an undoable Alice edit

Alice's undo and Project History record edits committed on a `UserActivity` under the project activity (`ProjectApplication.getProjectUserActivity()`). `ProjectHistoryManager` listens for `EditCommittedEvent` and pushes each edit onto the `PROJECT_GROUP` history.

For each accepted tool call, the executor:

1. creates a child activity: `projectActivity.newChildActivity()`;
2. sets its completion model to a Prompter operation in `Application.PROJECT_GROUP` (one operation per tool, so Project History shows "Prompter: add cat");
3. builds the same edit class the Alice UI would build (`DeclareGalleryFieldEdit`, `InsertStatementEdit`, …), using the same helper code (`AddManagedFieldComposite`, `SetUpMethodGenerator`);
4. calls `activity.commitAndInvokeDo(edit)`.

Alice has no working composite edit (`UndoHistory.createDoIgnoringCompositeEdit` throws "todo"). So each step is its own undo entry, and the panel's **Undo this request** button undoes as many entries as the request created, provided nothing else was edited in between (otherwise the button is disabled and says why).

## 6. Data the Prompter keeps

| Data | Where | Notes |
|---|---|---|
| Preferences (on/off, voice, mode) | `java.util.prefs` via Croquet `PreferenceBooleanState` | No secrets |
| API key | OS credential store (spec 02) | Never in prefs, files, logs or projects |
| whisper models | `%LOCALAPPDATA%\StoryStage\models` (macOS `~/Library/Application Support/StoryStage/models`, Linux `~/.local/share/StoryStage/models`) | Checksum verified |
| Conversation | Memory only, per open world | Cleared when the world closes |
| Usage counter | Preferences (token totals per month) | Numbers only |

Nothing is written into `.a3p` project files by the Prompter except the ordinary blocks it builds.
