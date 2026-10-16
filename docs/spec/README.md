# StoryStage technical specification

Covers Test Release 1 (see [docs/WORKPLAN.md](../WORKPLAN.md)). Decisions come from the agreed proposal.

| File | Covers | Workplan |
|---|---|---|
| [00-architecture.md](00-architecture.md) | Modules, hooks in Alice, threads, how steps become undoable edits, stored data | WP1.1 |
| [01-prompter-protocol.md](01-prompter-protocol.md) | Claude request shape, context (catalogs, project text, screenshot), tools, system prompt, errors | WP1.2–1.4 |
| [02-keys-privacy-switches.md](02-keys-privacy-switches.md) | Set-up wizard, key storage, usage meter, off switches and policy file, privacy notice | WP1.5, 1.7 |
| [03-speech.md](03-speech.md) | whisper.cpp input, voice providers, narration queue | WP1.6 |
| [04-ux.md](04-ux.md) | Branding, Prompter panel, states, accessibility, wording | WP1.8 |
| [05-testing.md](05-testing.md) | Test levels, evaluation set, recordings, definition of done | WP1.9 |

Changes to the spec go through pull requests like code. Each implementation pull request cites the section it implements.
