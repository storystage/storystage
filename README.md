# StoryStage

**Direct your own 3D stories, then code them. Based on Alice 3.**

StoryStage is a free, non-commercial fork of [Alice 3](https://www.alice.org/), the block-based 3D programming environment from Carnegie Mellon University. Students build scenes with a cast of characters and props, then bring them to life with code made of drag-and-drop blocks.

StoryStage is planned to add an optional assistant, **the Prompter**, powered by Claude. Students will type or say what they want, and the Prompter will build it from ordinary blocks, one undoable step at a time, reading each step aloud. It will use each user's own Claude API key, and it can be switched off completely.

> **Status:** early setup. The `storystage` branch is currently Alice 3.9.1.0 plus these project documents. The Prompter has not been built yet.

Website: [storystage.org.uk](https://storystage.org.uk) (coming soon)

## Branches

| Branch | Purpose |
|---|---|
| `storystage` | StoryStage development. Start new work from here. |
| `develop`, `release`, `master` | Kept matching the Alice project, so later Alice releases can be merged in. |

## Getting the code

You need [Git LFS](https://git-lfs.com/) for the Sims 2 art assets.

```bash
git clone --recurse-submodules --branch storystage https://github.com/storystage/storystage.git
cd storystage
git remote add upstream https://github.com/TheAliceProject/alice3.git
git lfs install
git lfs pull
```

The Sims 2 asset bundles add several hundred MB. To skip them, clone with `GIT_LFS_SKIP_SMUDGE=1` and build without them (below).

## Building

Requirements: Java 21 and Maven 3.9.9 or later.

```bash
mvn compile install
```

To build without the Sims 2 assets (marked experimental by the Alice project):

```bash
mvn -DincludeSims=false clean install
```

To run the IDE:

```bash
cd alice-ide
mvn exec:java -Dalice-ide
```

The original Alice 3 build and IDE set-up notes, including IntelliJ settings, are in [docs/Alice3-README.md](docs/Alice3-README.md).

## Licence and credits

StoryStage is distributed under the Alice 3 End User License Agreement in [License.txt](License.txt). The required notices and credits are in [NOTICE](NOTICE).

- This product includes software developed by Carnegie Mellon University.
- This product includes software developed by Washington University in St. Louis.
- This program/product includes art and animations developed by Electronic Arts Inc.

StoryStage is not affiliated with or endorsed by Carnegie Mellon University, Washington University in St. Louis, Electronic Arts Inc. or Anthropic.
