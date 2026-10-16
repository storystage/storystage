# StoryStage privacy notice (draft)

*Draft for storystage.org.uk, WP12.4. Needs the owner's approval before it is published. Last updated 16 October 2026.*

*Before publishing: the TR1 manual test (docs/test/TR1-manual.md, step N1) confirms that StoryStage makes no network connections with the Prompter off.*

StoryStage is a free 3D storytelling and coding app for learning, based on Alice 3. This notice explains what happens to your information when you use it.

## The short version

- StoryStage runs on your computer. The StoryStage project runs no servers and receives none of your data.
- The **Prompter** (the optional AI helper) is off until an adult sets it up with their own Claude account.
- When the Prompter is on, your request and your world's code (and a small picture of the scene, if you allow it) go from your computer to Anthropic, under that Claude account and Anthropic's terms.
- Speech is turned into text on your computer. Recordings of your voice are never sent anywhere.
- Schools can turn the Prompter off for everyone on a computer.

## Without the Prompter

StoryStage works like Alice 3. Your worlds are saved where you choose on your computer. StoryStage doesn't collect usage statistics, show adverts or use tracking.

StoryStage only goes online when you ask it to: for example, to open this website, or to report a problem. A problem report opens a new issue on GitHub in your browser. You choose what to include, and any Claude key is removed from the report first.

## With the Prompter

The Prompter is switched off when StoryStage is installed. To switch it on, an adult (18 or over) completes the set-up and enters an API key from their own Claude account at Anthropic. That adult agrees to the sending described below.

**What is sent to Anthropic, each time you ask the Prompter something:**
- your request, as text (typed, or turned into text from speech on your computer);
- your world's code, with the names of the characters and objects in it;
- the conversation so far in this session, so the Prompter can follow on;
- only if you tick **Prompter can see a picture of the scene** (off unless you choose it): a small picture of the scene, as the camera sees it.

**What is never sent:** recordings of your voice, your other files, or anything about you that you don't type or say in a request.

Anthropic handles this data under the account holder's agreement with Anthropic. See Anthropic's [Commercial Terms](https://www.anthropic.com/legal/commercial-terms) and [Privacy Policy](https://www.anthropic.com/legal/privacy). The StoryStage project can't see your requests or your account.

**Your Claude key** is kept in your computer's secure credential store (Windows Credential Manager, macOS Keychain or the Linux Secret Service). It is sent only to Anthropic. **Remove key** in the Prompter panel deletes it and switches the Prompter off.

**Cost:** requests are charged to the Claude account. StoryStage shows an estimate; the Claude Console shows the real amount. We recommend a monthly spend limit.

## Voice

Voice input is optional. The microphone is used only while you hold **Hold to talk**, for at most 30 seconds. The recording stays in your computer's memory, is turned into text on your computer, and is then discarded.

The speech model (about 78 MB) is downloaded once from Hugging Face, after you agree, and checked before use. That download is the only time voice input uses the internet.

Spoken steps use your computer's own voices. Nothing is sent anywhere to read text aloud.

## Children

StoryStage is made for learners, including children. The Prompter is told to use friendly, age-appropriate language, never to ask for personal information, and to suggest talking to a trusted adult if a learner seems upset. Learners should not type names, addresses or other personal details into requests.

## Schools

A school can set, for every user on a computer:
- `prompter.enabled=false`: the Prompter can't be switched on;
- `prompter.explainOnly=true`: the Prompter can explain but not change worlds;
- `prompter.voiceInput=false`: voice input can't be switched on.

These go in `policy.properties` (on Windows, in `%PROGRAMDATA%\StoryStage`). See the StoryStage documentation for details.

## Contact

Questions about this notice: [owner to add a contact address].
