# 05 Testing

Implements workplan WP1.9. No live API key is ever used in CI.

## 1. Levels

| Level | What | Where |
|---|---|---|
| Unit | Tool input validation; each tool's edit builder against a test world; id assignment in project text; catalog extraction; key-store rules (no logging, scrubbing); policy file parsing; narration sanitiser; usage arithmetic | `core/prompter/src/test`, JUnit 5, runs in CI |
| Fake-server integration | The full request loop against a local HTTP server (JDK `com.sun.net.httpserver`) that replays recorded streaming responses, including tool calls, errors (401, 429, 529), refusals and truncated tool input. The SDK client's `baseUrl` points at it | CI |
| Headless world tests | Load a sample `.a3p`, run tool sequences through the executor, then check the resulting AST and that undo restores the original | CI (no rendering needed) |
| Packaged smoke test | The Windows bundle starts, and stays running after start-up (WP2) | CI, `windows-latest` |
| Evaluation set | `docs/eval/requests.jsonl`: 40+ learner-style requests with checks. Run against recorded responses in CI; run live by the owner with their key | CI (recorded), owner (live) |
| Manual test script | `docs/test/TR1-manual.md`: install, wizard, each mode, voice in and out, off switches, policy lock, undo | Owner's Windows PC |
| Performance | Start-up time, first-model load, request latency, memory on a 4 GB PC | CI timings and the owner's PC |

## 2. Evaluation set format

```json
{"id": "scene-01", "mode": "build", "request": "Put a cat and a dog on the grass, facing each other.",
 "checks": [{"type": "has_object", "class": "SQuadruped", "min": 2},
            {"type": "facing", "a": "$1", "b": "$2"}]}
```

Check types: `has_object`, `object_count`, `facing`, `near`, `method_has_call` (procedure, target), `method_order`, `event_listener`, `no_errors`, `explain_mentions`. Categories (at least 5 each): scene set-up, simple animation, do together, counting loop, events, explain code, explain error, refusal (an unsuitable request), ambiguous request (expects a clarifying question).

Scores are recorded per run in `docs/eval/results/`, so changes to the system prompt or tools can be compared.

## 3. Recording responses

`PrompterRecorder` (developer option, off in releases): runs a live request with the developer's key and writes the streamed response to `src/test/resources/recordings/<id>.sse` with the key and account ids removed. Recordings are reviewed before committing, and push protection is a second guard against secrets.

## 4. Definition of done for TR1

- CI green: unit, fake-server and headless world tests; the no-Sims and no-Prompter builds; the packaged smoke test.
- Evaluation set: at least 85% of checks pass on recorded responses, and at least 75% on a live run by the owner.
- Manual test script passes on the owner's Windows PC.
- No key, token or personal data in logs (checked by a test that scans log output after a full fake-server session).
