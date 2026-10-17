# 02 Keys, privacy and off switches

Implements workplan WP1.5 (key and privacy) and WP1.7 (off switches).

## 1. Set-up wizard

A Croquet wizard (`WizardDialogCoreComposite` with `WizardPageComposite` pages), opened from **Preferences → StoryStage Prompter → Set up…** or from the panel's **Set up** button. Completing it is the only way to switch the Prompter on.

| Page | Content | Leaves when |
|---|---|---|
| 1 What the Prompter does | Plain-language summary; what is sent to Anthropic (the world's code, scene pictures when allowed, the typed or transcribed request); what is never sent (audio, files, the key to anyone but Anthropic); typical cost per request (10 to 30 US cents on the default model, up to about 50 cents for the first request in a session, as measured in TR1); "The Claude account must belong to an adult (18 or over): a parent, teacher or school." | **Next** |
| 2 Create your Claude account | Steps with the Console address shown as copyable text: sign up; add credit or billing; create a workspace called "StoryStage" and set a monthly spend limit; create an API key in that workspace (choosing **Continue with an API key** when the Console offers identity federation, which is for cloud workloads); copy it at once, as it is shown only once | **Next** |
| 3 Enter your key | Password-style field (masked, with a show toggle), paste allowed. **Check key** lists models with the key (no tokens used) | The check succeeds |
| 4 Options | Mode default ("Show me how" or "Build it"); voice input on/off; spoken steps on/off; "let the Prompter see the scene" on/off | **Finish** |

Key check results: success; "That key wasn't accepted" (401); "That key works but can't use Claude models" (403/404); "Couldn't reach Anthropic: check the internet connection or school proxy" (network). The key is stored only after a successful check.

## 2. Key storage

```java
interface KeyStore {
  Optional<String> read();
  void write(String key);   // replaces any existing key
  void delete();
}
```

| Platform | Implementation | Location |
|---|---|---|
| Windows | `com.github.javakeyring:java-keyring` (Windows Credential Manager) | Credential "StoryStage/anthropic-api-key" |
| macOS | same library (Keychain) | Service "StoryStage", account "anthropic-api-key" |
| Linux | same library (Secret Service) | same |
| Fallback when the keyring is unavailable | Windows: DPAPI (`Crypt32Util.cryptProtectData` via JNA) to `%APPDATA%\StoryStage\key.bin`; other platforms: no storage, and the key is asked for each session | Encrypted to the Windows user account |

The licence of `java-keyring` and its dependencies (JNA, jkeychain, secret-service) is confirmed before it's added, and recorded in NOTICE.

**Rules (each has a test):**
- The key is held in memory only while a request is being built, and is never logged. `toString()` of any object holding it returns `sk-ant-…(hidden)`.
- It never appears in preferences, projects, backups, crash reports or the issue reporter. The reporter scrubs `sk-ant-[A-Za-z0-9_-]{20,}` from all text before sending.
- The `ANTHROPIC_API_KEY` environment variable is ignored. The client is built with the stored key only.
- **Remove key** in Preferences deletes it from the store and switches the Prompter off.

## 3. Usage meter

Each response's `usage` (input, cache read, cache write, output tokens) is added to a per-month total in preferences. The panel shows "This month: N requests, about £X" using prices from `prompter-prices.json`, which is shipped with the app and updated each release. The figure is labelled an estimate, with a pointer to the Console for exact billing.

## 4. Off switches

| Level | Mechanism | Effect |
|---|---|---|
| User | `PrompterEnabledState` (Croquet `PreferenceBooleanState`, default **false**); also `VoiceInputState`, `SpokenStepsState`, `SceneImagesState`, `ExplainOnlyState` | Off: panel hidden, no Claude client created, no microphone opened, no network calls |
| Administrator / exam | Policy file, read at start-up: Windows `%PROGRAMDATA%\StoryStage\policy.properties`; macOS `/Library/Application Support/StoryStage/policy.properties`; Linux `/etc/storystage/policy.properties`. Keys: `prompter.enabled=false`, `prompter.explainOnly=true`, `prompter.voiceInput=false`. Also a JVM option `-Dstorystage.prompter.enabled=false` for packaged launchers | Overrides user preferences; the related menu items are shown disabled with "Turned off by your school" |
| Build | No `core/prompter` on the classpath (`-DincludePrompter=false`) | No provider found; no Prompter menus or panel |

The policy file is plain text so a school can deploy it with its usual tools. Unknown keys are ignored, and a malformed file means "Prompter off".

## 5. Privacy notice (website and wizard page 1)

Draft points for WP12.4:
- StoryStage runs on your computer. The StoryStage project runs no servers and receives none of your data.
- If you switch the Prompter on, your requests and your world's code (and scene pictures, if allowed) go from your computer to Anthropic under your own Claude account and its terms. Link to Anthropic's commercial terms and privacy policy.
- Speech is turned into text on your computer; audio is never sent.
- Your Claude key is kept in your computer's secure credential store and sent only to Anthropic.
- Schools can turn the Prompter off for all users.
