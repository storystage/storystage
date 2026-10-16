# Contributing to StoryStage

StoryStage is a fork of [Alice 3](https://github.com/TheAliceProject/alice3). The plan of work is in [docs/WORKPLAN.md](docs/WORKPLAN.md), and each work package has an issue labelled `work-package`.

## Branches

- `storystage` is the default branch. All StoryStage work lands here.
- `develop`, `release` and `master` mirror the Alice project. Don't commit to them; they are updated only by merging from `upstream`.
- Work on a short-lived branch named after its work package, for example `wp2-ci` or `wp5-key-wizard`, and open a pull request into `storystage`.

## Pull requests

- Name the work package and the spec section the change implements (for example "WP8.2, spec 02-tools").
- Tick the matching boxes in `docs/WORKPLAN.md` in the same pull request.
- CI must pass before merging.
- Keep changes to Alice's own code small and clearly marked, so upstream releases still merge cleanly. New StoryStage features go in their own modules (for example `core/prompter`).

## Commits

- Write the subject in the imperative ("Add key wizard page"), 72 characters or fewer, with a body explaining why when it isn't obvious.
- Follow Alice's existing code style; `mvn checkstyle:check -Dcheckstyle.config.location=checkstyle.xml` runs in CI.

## Secrets

Never commit API keys, tokens or passwords. Secret scanning and push protection are enabled. Tests use a fake Claude server and fake keys.

## Licence

Contributions are made under the Alice 3 End User License Agreement in [License.txt](License.txt), and must keep the notices described in [NOTICE](NOTICE).
