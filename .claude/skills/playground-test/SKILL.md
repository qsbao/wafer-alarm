---
name: playground-test
description: "Tests skills in the Skill Web IDE playground using a browser. Use this skill whenever the user asks to test a model (e.g. GLM-4.7, deepseek, qwen) against a skill in the playground, do a playground test, or run a chat test with a specific model config. Also trigger when the user provides model config (ANTHROPIC_BASE_URL, ANTHROPIC_AUTH_TOKEN, ANTHROPIC_MODEL) and wants to test it in the playground."
---

# Playground Model Testing Skill

Automates testing a model against a skill in the Skill Web IDE playground via browser automation. This skill depends on the `playwright-cli` skill for browser control.

## When to use

- User wants to test a model (GLM, deepseek, qwen, etc.) in the playground
- User provides model config env vars and a skill to test
- User says "do a playground test" or "test X model with Y skill"

## Required inputs

Gather these from the user before starting:

1. **Skill ID** — which skill to test (e.g. `@local/data-pipeline`). Used to build the playground URL.
2. **Prompt** — what to send in the chat (e.g. "run data-pipeline skill first step only")
3. **Model config** (optional, if different from current backend):
   - `ANTHROPIC_BASE_URL`
   - `ANTHROPIC_AUTH_TOKEN`
   - `ANTHROPIC_MODEL`

## Step-by-step workflow

### 1. Check if backend needs model env vars

If the user provides model config env vars, the backend must be restarted with those env vars so the `claude` CLI subprocess inherits them. The `.env` file in the project root is NOT auto-loaded by the backend.

```bash
# Find the backend process
netstat -ano | findstr ":3001 " | findstr "LISTEN"
# Kill it (use cmd on Windows to avoid flag parsing issues)
cmd //c "taskkill /PID <PID> /F"
# Start backend with env vars
cd <project-root>/backend
export ANTHROPIC_BASE_URL=<url>
export ANTHROPIC_AUTH_TOKEN=<token>
export ANTHROPIC_MODEL=<model>
npx tsx watch src/index.ts
```

Run the backend start command in the background. Wait ~5 seconds, then verify with:
```bash
curl -s --noproxy localhost http://localhost:3001/ -o /dev/null -w "%{http_code}"
```
A 404 response means the backend is up (no root route defined).

### 2. Open the playground in a headed browser

Always use `--headed` so the user can see the browser. Build the URL with the encoded skill ID.

```bash
playwright-cli open --headed "http://localhost:5173/playground?skillId=<encoded-skill-id>"
```

For example, `@local/data-pipeline` encodes to `%40local%2Fdata-pipeline`.

Wait 2-3 seconds after page load for the WebSocket connection to establish. The initial "WebSocket closed before connection established" warning in console is normal — the WS auto-reconnects.

### 3. Send the prompt

Take a snapshot to find the chat textbox ref, then fill and send:

```bash
playwright-cli snapshot
playwright-cli fill <textbox-ref> "<prompt>"
playwright-cli press Enter
```

The textbox typically has the placeholder "Type a message... (Enter to send, Shift+Enter for newline)".

### 4. Wait for the response

The response streams via WebSocket. Poll with snapshots at intervals:
- First check after **30 seconds** (models behind custom endpoints can be slow to start)
- Then check every **30-60 seconds**
- The response is **complete** when:
  - The send button is no longer `[disabled]`
  - OR the textbox is no longer `[active]`
  - OR response text has appeared and stopped changing

Look for the assistant's response in the snapshot as `paragraph` or `generic` elements below the user message bubble.

**Important**: Some models (especially via proxy endpoints) can take 2-5 minutes for the first response. Be patient. If no response after 5 minutes, check backend logs for errors.

### 5. Take a screenshot

Once the response is complete:

```bash
playwright-cli screenshot --filename=<descriptive-name>.png
```

Use a descriptive filename like `glm47-data-pipeline-test.png`.

### 6. Report results

Summarize for the user:
- Whether the model responded successfully
- The content of the response (from the snapshot text)
- Any issues observed (duplicated text, errors, timeouts)
- The screenshot path

## Known issues

- **Duplicate response text**: The backend's `run.service.ts` may emit text twice — once from `stream_event` (streaming deltas) and once from `result` (final result). This causes the response to appear duplicated in the UI. This is a backend bug, not a model issue.
- **Proxy interference**: On machines with HTTP proxy configured, `curl` commands to localhost will fail. Always use `--noproxy localhost` with curl, or test via the browser directly.
- **Proxy in spawned claude**: The backend now clears `HTTP_PROXY`/`HTTPS_PROXY` env vars when spawning `claude` CLI, so custom model endpoints connect directly without going through the proxy. This was fixed in `run.service.ts` and `test-runner.service.ts`.
- **Backend env vars**: The `.env` file uses `export` syntax but is never sourced by the backend process. Env vars must be set in the shell that starts the backend.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Spinner forever, no text | Backend doesn't have model env vars | Restart backend with correct exports |
| WebSocket errors in console | Backend was restarted | Reload the page, WS auto-reconnects |
| `curl` returns 000 | HTTP proxy intercepting localhost | Use `--noproxy localhost` |
| Response text duplicated | Both stream_event and result handlers emit text | Known backend issue, not blocking |
