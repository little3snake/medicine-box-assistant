# Agent logs

3–5 key sessions with our coding agents (exported transcripts). Before committing:
search the file for API keys / tokens (e.g. `grep -nE "AIza|sk-|key" file`) and remove them.

| File | Who | Tool | What the session did |
|---|---|---|---|
| | | | |
| 01-gemini-reliability.txt | Angelina | Claude Code (Opus 5.5) | Gemini client: retry on 503/429, schema fallback only for schema errors, error/timeout logging, tests. Agent found the real cause of HTTP 500 on slow photos, asked about the ambiguous "3 times", fixed its own python→python3 mistake; we added AQ.-key redaction to its plan. |
