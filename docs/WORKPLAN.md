# StoryStage workplan

From the agreed proposal ([Claude in Alice](https://claude.ai/artifact/PinTBu8FKteWAW1MCiFz94)) to a product ready to test.

- **Status:** active, started 16 Oct 2026.
- **Code base:** this repository (`storystage` branch), forked from Alice 3.9.1.0 (`8d34d16`).
- **Owner key:** **C** is Claude (development sessions), **U** is the project owner. U tasks are kept to the ones only a person can do.

## 1. Goal and the first test release

StoryStage is a free, non-commercial fork of Alice 3. It adds the optional **Prompter**, a Claude-powered assistant. The Prompter builds scenes and code from typed or spoken requests, using ordinary undoable Alice edits, and reads each step aloud.

**Test Release 1 (TR1)** is the "product to test" this plan delivers. It contains:

- StoryStage branding, with the "based on Alice 3" credits, as a portable Windows app built by CI.
- The settings switches, a school/exam lock, and a set-up wizard for the user's own API key.
- The Prompter panel: explains code and errors, finds blocks, and builds scenes (models, placement, properties) and simple scripts. Every change is undoable and listed in Project History.
- Push-to-talk speech input (whisper.cpp) and spoken, captioned steps (system voice, Flite fallback).
- Automated tests running in CI against a fake Claude server, plus a manual test script.

**Later releases** add writing whole procedures and event handlers through the Prompter, teacher mode, the Tweedle text import path, macOS/Linux bundles, and the public website release.

## 2. Fixed decisions (from the proposal)

| Area | Decision |
|---|---|
| Distribution | Free, non-commercial; website `storystage.org.uk` on the owner's Bluehost account. |
| Code | Fork on GitHub; `develop`/`release`/`master` mirror upstream; work on `storystage`. |
| Claude access | User's own API key via a set-up wizard; stored in the OS credential store; no StoryStage server. |
| Model | `claude-opus-5-5`, streaming, adaptive thinking, strict tools, prompt caching, server-side refusal fallback. |
| AI off switches | User preference (off by default); machine-wide admin/exam lock; build without the AI module. |
| Edits | Claude calls typed tools; each accepted call becomes a normal Croquet edit via `UserActivity.commitAndInvokeDo`. |
| Context sent | API catalog from `classinfos.json` (PRIME_TIME only), gallery list, project as Tweedle text, optional scene screenshot. |
| Speech in | whisper.cpp on the PC; push-to-talk; editable transcript; audio never leaves the PC. |
| Speech out | OS system voices behind one interface; Flite fallback; captions always shown. |
| Name | StoryStage; assistant "the Prompter"; theatre vocabulary; credits in NOTICE. |

## 3. Constraints that shape the plan

1. **Builds run on GitHub Actions.** The development PC has 4 GB RAM, 2 cores and about 7 GB free disk. Full Maven builds and Windows packaging run on CI (`windows-latest`, Java 21); local work is limited to editing and small module builds.
2. **Windows first.** JavaFX artifacts are OS-specific, so the Windows bundle is built on a Windows runner with `jpackage` (no paid Install4J).
3. **Sims 2 assets (555 MB of LFS bundles).** CI caches them so they aren't downloaded on every run. Builds without them (`-DincludeSims=false`) are the fallback while CMU's confirmation is pending.
4. **No live API key in CI or in chat.** Automated tests use a fake Claude server (recorded responses, SDK base URL override). Live tests are run by the owner, who enters their own key in the wizard on their own PC.
5. **Upstream compatibility.** Changes stay in new modules or small, well-marked hooks, so Alice releases can still be merged.

## 4. Work packages

Each package lists its objective, tasks, deliverables and the check that closes it ("done when"). Packages are delivered in the order of section 5.

### WP0: Project set-up and governance
**Objective:** a repository ready for systematic delivery.
- [x] 0.1 Fork to `storystage/storystage`; `storystage` default branch; description, topics, Issues. (C)
- [x] 0.2 Secret scanning and push protection on. (C)
- [x] 0.3 README, NOTICE (CMU, WUSTL, EA credits), original Alice README kept in `docs/`. (C)
- [x] 0.4 This workplan committed; one GitHub milestone per release and one issue per work package. (C)
- [x] 0.5 Labels (`work-package`, `type:bug`, `type:task`, `needs-owner`) and a CONTRIBUTING note on branches and commit style. (C)
- [ ] 0.6 Organisation settings: require 2FA, confirm the Free plan (it currently shows Team), profile name and logo. (U)

**Done when:** every WP has an issue, and work is tracked against milestones.

### WP1: Technical specification
**Objective:** a specification precise enough to build and test against.
- [x] 1.1 Architecture: new Maven module `core/prompter` (AI code only), and the hooks it needs in `core/ide` (panel slot, preferences, menu items). (C)
- [x] 1.2 Tool contract: JSON schemas (`strict: true`, enums from the catalog), with error and result formats for `add_model`, `set_property`, `insert_statement`, `declare_procedure`, `add_event_listener`, `explain`, and narration fields. (C)
- [x] 1.3 Context contract: catalog extraction, gallery list, Tweedle project text, screenshot format, cache breakpoints, token budget. (C)
- [x] 1.4 Claude request design: system prompt, "show me how" vs "build it" modes, model and effort, streaming, the stop button, refusal and fallback handling, error and retry rules. (C)
- [x] 1.5 Key and privacy design: wizard pages, credential store library, what is stored where, consent text, data-flow description for the website privacy notice. (C)
- [x] 1.6 Speech design: whisper.cpp binding and model download, audio capture, the TTS interface, the Windows/macOS/Linux voices, the Flite fallback, captions. (C)
- [x] 1.7 Off switches: preference keys, the admin lock file location and format, module-absent behaviour. (C)
- [x] 1.8 UX specification: panel layout, states, wording in theatre vocabulary, accessibility (keyboard, captions). (C)
- [x] 1.9 Test strategy: unit, fake-server integration, an evaluation set of 40+ student-style requests, the manual test script. (C)
- [ ] 1.10 Review of the spec. (U, optional: work continues unless the owner objects)

**Deliverable:** `docs/spec/` (one file per section). **Done when:** each later WP can cite the section it implements.

### WP2: Build and packaging pipeline
**Objective:** every push produces a tested, downloadable Windows build.
- [ ] 2.1 GitHub Actions workflow: Java 21, Maven cache, LFS cache, `mvn -Dinstall4j.skip install` on `windows-latest`. (C)
- [ ] 2.2 A no-Sims build job (`-DincludeSims=false`) to keep the fallback working. (C)
- [ ] 2.3 Portable Windows app with `jpackage`: main class `org.alice.stageide.EntryPoint`, `-Dorg.alice.ide.rootDirectory=$APPDIR/distribution`, the Alice VM options, the bundled runtime; zipped as a workflow artifact. (C)
- [ ] 2.4 Smoke test in CI: the app starts headless far enough to load the class catalog (or the nearest feasible check). (C)
- [ ] 2.5 Versioning: `StoryStage 0.x` with the Alice base version recorded in About. (C)

**Done when:** a zip from the latest `storystage` commit runs on the owner's Windows PC and opens a world. (U test: download, unzip, run.)

### WP3: Rebranding
**Objective:** the app presents itself as StoryStage while crediting Alice.
- [ ] 3.1 App name, window title, splash, icons, About dialog (credits and licences from NOTICE). (C)
- [x] 3.2 Separate user data folder (`StoryStage`, not `Alice3`), so an existing Alice install is untouched. (C)
- [ ] 3.3 English strings in theatre vocabulary where agreed (Rehearse, Backdrops, Cast and props); other locales keep the Alice wording until translated. (C)
- [ ] 3.4 A logo and splash artwork. (C draft; U approves)

**Done when:** no "Alice" appears as the product name in the UI, and "based on Alice 3" plus the credits appear in About.

### WP4: Settings, off switches and admin lock
- [x] 4.1 Preferences → "StoryStage Prompter" on/off, voice input, spoken steps; persisted with Croquet `PreferenceBooleanState`. (C)
- [x] 4.2 Machine-wide lock: a policy file or JVM option forces the Prompter off and hides the switches. (C)
- [ ] 4.3 When off: no panel, no microphone, no Claude client, no network. Verified by test. (C)
- [x] 4.4 Build without `core/prompter` still compiles and runs. (C, CI job)

**Done when:** all three levels are covered by tests.

### WP5: API key wizard and secure storage
- [x] 5.1 Croquet wizard: what is sent, cost and adult-account notes, Console steps (account, workspace with spend limit, key), paste key, check key, options. (C)
- [x] 5.2 Credential store integration (Windows Credential Manager first, then macOS Keychain and Linux Secret Service). (C)
- [x] 5.3 Key check with the Models API (no tokens used); clear error messages. (C)
- [ ] 5.4 Usage counter from response token counts; "Remove key". (C)
- [ ] 5.5 Key excluded from logs, project files and the issue reporter; tests prove it. (C)

**Done when:** the owner completes the wizard on their PC with their own key. (U: needs an Anthropic Console account and key.)

### WP6: Claude client
- [x] 6.1 Add `com.anthropic:anthropic-java`; client factory using the stored key. (C)
- [x] 6.2 Streaming requests off the Swing thread; cancel/stop; timeouts and retries. (C)
- [x] 6.3 Prompt caching of the catalog; refusal handling; server-side fallback. (C)
- [x] 6.4 Fake Claude server for tests (recorded responses), wired by base URL. (C)

**Done when:** integration tests pass against the fake server in CI.

### WP7: Context builders
- [x] 7.1 API catalog extractor (classinfos + annotations, PRIME_TIME only), built at start-up. (C)
- [x] 7.2 Gallery catalog (resource enums: class names, display names, categories). (C)
- [x] 7.3 Project-to-Tweedle text using the existing encoder, with size limits. (C)
- [ ] 7.4 Scene screenshot via the renderer's image capture, downscaled. (C)

**Done when:** a sample world's context fits the token budget defined in 1.3.

### WP8: Prompter tools and executor
- [x] 8.1 Tool executor framework: validate input, look up nodes by UUID, build the edit, commit through `UserActivity`, return a result or error. (C)
- [x] 8.2 `add_model` and `set_property` (scene setup via `DeclareGalleryFieldEdit` and setup statements). (C)
- [x] 8.3 `insert_statement` (procedures with keyed details such as duration). (C)
- [ ] 8.4 `declare_procedure` and `add_event_listener`. (C; TR1 covers simple cases) *`add_event_listener` done; `declare_procedure` after TR1.*
- [x] 8.5 `explain` and block highlighting. (C)
- [x] 8.6 Multi-step request handling: one Project History group per request, so a whole request can be undone. (C)

**Done when:** every tool has unit tests, and the evaluation set's scene requests build valid worlds against recorded responses.

### WP9: Prompter panel UI
- [x] 9.1 Docked panel in code and scene editors: transcript, input box, push-to-talk button, stop button, mode switch ("Show me how" / "Build it"). (C)
- [x] 9.2 Progress, captions, error states; keyboard access. (C)
- [ ] 9.3 Links from Prompter messages to the blocks or objects they mention. (C)

**Done when:** the UX spec's states are all reachable, and the manual test script passes.

### WP10: Speech input
- [x] 10.1 whisper.cpp binding (Java JNI library or bundled executable) with Windows natives in the bundle. (C)
- [x] 10.2 Model download on first use (tiny/base), with a progress bar and a checksum. (C)
- [x] 10.3 Microphone capture at 16 kHz mono while push-to-talk is held; transcript placed in the input box for editing. (C)

**Done when:** a spoken request on the owner's PC is transcribed with no network use apart from the one-off model download. (U test)

*Checked on 16 Oct 2026 on the development PC (2 cores, 4 GB):* the tiny model downloaded and passed its checksum in 24 s, loaded in 7 s and transcribed a 4.5 s request in 4 s. "Send when I stop talking" waits until after TR1.

### WP11: Speech output
- [x] 11.1 TTS interface; Windows voice provider (System.Speech/SAPI, UK English voice preferred). (C)
- [x] 11.2 macOS `say` and Linux speech-dispatcher providers (compile-tested; full test later). (C)
- [x] 11.3 Flite fallback provider. (C)
- [x] 11.4 Narration queue tied to tool execution; stop; captions. (C)

**Done when:** each step of a multi-step request is spoken as it is applied, and can be stopped.

### WP12: Safety, privacy and teaching controls
- [x] 12.1 Consent and data notice in the wizard and the Prompter panel. (C)
- [x] 12.2 "Explain only" mode, and a teacher setting to restrict to it. (C)
- [x] 12.3 System prompt guidance for a young audience, plus refusal handling surfaced politely. (C)
- [ ] 12.4 Privacy notice text for the website. (C draft; U approves) *Draft in docs/privacy.md, waiting for approval.*

### WP13: Testing and quality
- [x] 13.1 Unit tests (executor, schemas, key store, switches, TTS queue). (C)
- [x] 13.2 Fake-server integration tests in CI. (C)
- [x] 13.3 Evaluation set (40+ requests) with expected outcomes; scored on recorded and, by the owner, live runs. (C builds; U runs live) *47 requests in docs/eval; recorded-response scoring follows the first live run.*
- [x] 13.4 Manual test script for TR1 on Windows. (C) *docs/test/TR1-manual.md*
- [ ] 13.5 Performance check on a 4 GB PC: start-up, model load, Prompter latency. (C via CI where possible; U on the PC)

### WP14: Website and distribution
- [ ] 14.1 Register `storystage.org.uk` on Bluehost; hosting set up. (U)
- [ ] 14.2 Static site files: home, download, set-up guide (API key), privacy notice, credits and licences. (C)
- [ ] 14.3 Upload to Bluehost. (U, or C with guidance)
- [ ] 14.4 GitHub Release for TR1 with the zip and checksums. (C)
- [ ] 14.5 Verify the domain with GitHub (DNS TXT record on Bluehost). (U)

### WP15: Legal and licensing
- [ ] 15.1 Email to the Alice team at CMU: Sims 2 assets in a renamed fork, and the "based on Alice 3" strapline. (C drafts; U sends)
- [ ] 15.2 Trade mark attorney clearance for StoryStage; EUIPO if used outside the UK. (U)
- [ ] 15.3 Check Anthropic's terms for products used by under-18s; reflect in the wizard and privacy notice. (C research; U decides)

### WP16: Pilot
- [ ] 16.1 Recruit 2–3 teachers/classes. (U)
- [ ] 16.2 Feedback form and issue templates. (C)
- [ ] 16.3 Triage feedback into the next release. (C)

## 5. Sequence and milestones

| Milestone | Contents | Gate |
|---|---|---|
| **M0 Set-up** | WP0 | Issues and milestones exist |
| **M1 Spec** | WP1 | Spec committed; owner may comment |
| **M2 Branded build** | WP2, WP3 | Owner runs the CI zip on Windows |
| **M3 Prompter alpha (Stage 1)** | WP4, WP5, WP6, WP7.1–7.3, WP9.1–9.2, WP8.5 (explain) | Wizard and explain work with the owner's key |
| **M4 Scene building and voice (Stage 2) = TR1** | WP7.4, WP8.1–8.6, WP9.3, WP10, WP11, WP12, WP13 | Manual test script passes; owner tests TR1 |
| **M5 Release** | WP14, WP15, WP16 | Legal checks done; site live; pilot starts |

WP1 and WP2 run side by side, because the build pipeline doesn't depend on the spec. WP15.1 (the CMU email) should go out early, since the reply can take weeks.

## 6. Where the owner is needed

| When | What | Why only the owner |
|---|---|---|
| M0 | 2FA and billing check on the organisation | Account settings |
| M1 | Optional spec review | Product decisions |
| M2 | Run the CI zip on Windows and report | Needs the target PC |
| M3 | Create an Anthropic Console account, workspace with spend limit, and API key; enter it in the wizard | Personal account, payment and secret |
| M3–M4 | Run live tests and the evaluation set with their key | Costs and key stay with the owner |
| Early | Send the CMU email | Correspondence in the owner's name |
| M5 | Bluehost domain and hosting; DNS record; trade mark attorney; pilot schools | Purchases, accounts and relationships |

## 7. Risks

| Risk | Effect | Response |
|---|---|---|
| Upstream build fails on CI or without Sims | Blocks M2 | Fix in small, upstreamable patches; keep both CI jobs |
| `jpackage` bundle misses JOGL natives or resources | App fails to start | Mirror the Install4J layout; smoke test in CI |
| Edits behave differently when not driven by the mouse | Wrong or broken worlds | Executor builds edits exactly as the UI's own operations do; unit tests per tool |
| Model output invalid for the project | Failed steps | Strict schemas, catalog enums, error tool results, retry limit |
| 4 GB PCs too slow (model load, whisper) | Poor experience | tiny whisper model, lazy loading, measure in WP13.5 |
| CMU declines the Sims assets in a renamed fork | Asset gap | Ship the no-Sims build; Alice's own models remain |
| Trade mark objection | Rename | Keep the branding in one place (WP3) so a rename is cheap |

## 8. Ways of working

- Work happens on short-lived branches (`wp2-ci`, `wp3-branding`, …), merged into `storystage` by pull request after CI passes.
- Each pull request names its work package and spec section, and updates the checkboxes in this file.
- Upstream Alice releases are merged into `develop`/`release`, then into `storystage`.
- Nothing secret is committed: push protection is on, and test keys are fakes.
