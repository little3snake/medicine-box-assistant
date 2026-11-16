# DECISIONS.md

Format: date — decision — why — who / which tool.
Also records where the agent was wrong and how we noticed.

## Backend

**16 Nov — Workflow, not agent.** Photo → field extraction → matching → retrieval is a fixed pipeline: the
code decides the path; no model chooses tools in a loop. Why: the path never changes, so an agent loop would
only add cost, latency and new ways to fail. We use the least autonomy that works and would add more only if
a measurement showed the pipeline failing.

**16 Nov — The vision model only reads; code decides.** Gemini returns four printed fields; `ProductMatcher`
chooses the product. Why: one call should make one decision, and a confident wrong match is our main safety
metric, so that decision must be inspectable and unit-tested code, not a model's guess.

**16 Nov — Vision prompt: plain task, schema, null instead of guess.** No persona, no "think step by step",
no tips: they do not make reading a package more accurate and add latency. JSON schema with nullable fields,
so an unreadable field comes back as null instead of an invented value (and we can count the nulls). If the
API rejects the schema (HTTP 400), we retry once in plain JSON mode and log it. *Known flaw (found 16 Nov
evening):* the fallback fires on ANY HTTP 400, including `API_KEY_INVALID`, so an invalid key was logged as
`"schemaUsed":false` — misleading. To fix: retry without schema only when Gemini's error message is about the
schema, and log the error message.

**16 Nov — Package text is untrusted input.** Text on a photo could contain instructions (prompt injection).
Mitigation by design, not by prompt: the vision call has no tools, its output is four strings that code
validates, and it cannot send anything anywhere. The backend has untrusted input but no sensitive data and no
outbound actions.

**16 Nov — Log every vision call.** `logs/vision.jsonl`: model, latency, tokens, schema used, parsed fields,
SHA-256 of the image. Why: with a vision model the only way to debug is to keep the input and the reply;
token counts give the cost per request. This log is how we found the outdated model id (see below).

**16 Nov — Matching = weighted score + strict confidence rule.** Score = 0.50 name + 0.25 strength + 0.15
form + 0.10 manufacturer. Confident only if score ≥ 0.85, margin to the next candidate ≥ 0.10, strength and
form were read AND agree, and a read manufacturer agrees. Why: look-alike variants differ mostly in strength
or form, and those change dosing. These are starting values, not tuned; tuning only on the tuning split.

**16 Nov — Known limitation: manufacturer-only look-alikes always ask.** On the first 10 identification cases
(SAMPLE data, run on the core matcher), the right product is ranked first in 10/10 and there are 0 confident
wrong matches, but the 2 cases differing only by manufacturer return `AMBIGUOUS` (margin 0.09 < 0.10).
Safe, but an extra confirmation step. With n = 10 this says little; revisit with real photos.

**16 Nov — Retrieval: BM25 over leaflet sections; embeddings later.** One chunk = one leaflet section of the
confirmed product, never across products. Section titles plus everyday words per section type are indexed, so
"Можно ли детям?" reaches "Применение в педиатрии". Light Russian suffix stripping, not a full stemmer. Why
BM25 first: no model, no cost, works on the exact words of the leaflet. Embeddings are weaker on Kazakh
paraphrases, so hybrid search must be measured per language before we assume it is better. Note for 23 Nov:
long sections must be split before embedding, or text beyond the model's input limit is silently cut.

**16 Nov — Ingestion follows the real leaflet formats.** Research (docs/RESEARCH.md §3) showed that
leaflets come in at least three layouts: the current KZ format ("Перечень сведений, необходимых до начала
применения", "Рекомендации по применению", "Описание нежелательных реакций"), the older KZ format ("Торговое
название", "Фармакологические свойства", "Способ применения и дозы", "Побочные действия") and the EAEU patient
leaflet ("Листок-вкладыш – информация для пациента", numbered sections "4. Возможные нежелательные реакции").
`ingest_leaflet.py` knows all three (leading numbers are stripped, the longest matching heading wins), keeps
text verbatim, and drafts the catalogue entry from the leaflet header. The draft must be checked by hand: for
"Таблетки 500 мг" (no comma) it did not split strength from form, and it took the full address as manufacturer.

**16 Nov — Evaluation reports chance level, per-language results and field-level accuracy.** Retrieval is
compared with a random ranking (hit@1 by chance = 1 / number of sections), photo extraction is checked field by
field against a truth file, and every rate has a 95% Wilson interval. Why: with small n, differences inside
the interval are not evidence, and a score means little without the chance level next to it.

**16 Nov — Scope cut for the checkpoint.** The proposal promised 8–12 real variants by 16 Nov. The checkpoint
has 1 real variant (Анальгин 500 мг, see below) and 5 SAMPLE variants (fictional names, placeholder leaflet
text, `"sample": true`) with look-alikes by strength, by form and by manufacturer. Why: the pipeline had to
work end to end first, and we will not put invented medical text in the repo. Next: more real leaflets via
`tools/ingest_leaflet.py`, including real look-alike pairs.

**16 Nov — Framework-free core.** Matching, normalisation and BM25 are in `kz.medbox.core` with no Spring
imports. Why: fast unit tests and code we can explain line by line at the defense.

**16 Nov — `/api/identify/fields`.** Same matcher without the vision call: user corrections on the
confirmation screen, evaluation without spending API quota, fallback when the vision API is down.

**16 Nov — Secret check as a pre-commit hook.** `tools/check_secrets.py`. Why: a rule in AGENTS.md is only
advice to the agent; a check that runs before every commit actually blocks a leak, and a key in git history
costs points.

**16 Nov — AGENTS.md shared, CLAUDE.md imports it.** One short rule file for both tools (Claude Code, Codex),
kept under 200 lines so the agent actually follows it.

**16 Nov — Maven instead of Gradle.** Simpler single-module build.

## Mistakes found during development
- **Duplicate stop words** ("ли" three times) → `Set.of` threw at class load and every test failed. Found by
  running the unit tests; fixed.
- **Ingestion heading clash:** "Описание нежелательных реакций" (side effects) matched the shorter heading
  "Описание" (form). Found by reviewing headings; fixed with longest-prefix matching and tested.
- **First ingestion version only knew the older leaflet headings.** Found by looking at real current
  leaflets during research; fixed and covered by a retrieval test with current headings.
- **Eval runner paths** are relative to the repo root; running it from `eval/` fails. Documented in README.
- **Other manufacturer matched confidently** (found by Tatiana in code review, 16 Nov). The same name +
  strength + form from a manufacturer NOT in the catalogue scored ≈ 0.91 with no close competitor →
  `needsClarification=false` → the wrong leaflet would be shown. Fix: `MANUFACTURER_MISMATCH` when a
  manufacturer was read and its similarity to the top candidate is < 0.6; legal forms (ТОО, АО, LLC …) and
  country names are ignored, and shared words are counted against the shorter name. An unread manufacturer
  does not block a match. 3 unit tests added.

## Tools used
**16 Nov (proposal → checkpoint): Claude (claude.ai with an agentic workspace).** Used the way the course
expects an agentic tool to be used — the agent plans, writes, runs and fixes code; we direct, review and decide:
1. *Plan before code.* The agent turned our proposal and the course requirements into PLAN.md and the shared
   agent rules in AGENTS.md; we reviewed and adjusted them before any code was written.
2. *Research before design.* It checked existing apps and real Kazakhstan leaflets (docs/RESEARCH.md); what
   it found changed the design (ingestion of three leaflet formats, verbatim quotes as our differentiator).
3. *Write → run → fix loop.* It wrote the backend in small parts, compiled the core and ran the unit tests in
   its workspace, and fixed the failures it found itself (duplicate stop words, heading clash).
4. *Debugging on the real machine.* We ran every step locally and gave the agent the real output (Java 11,
   Python 3.6, model 404/503, invalid key, 92 s latency); it diagnosed each problem and proposed the fix,
   we applied, tested and committed it.
5. *Human review and decisions.* We chose the real product and its Kazakhstan source (KZ register number from
   our package instead of a Belarusian website), Tatiana's code review found the manufacturer bug, and we
   decided what went into each commit.

**Limits, stated honestly:** the agent's workspace could not reach our repository or download Spring Boot, so
its files came into the repo as downloaded archives/patches committed by us, and the Spring layer was first
compiled and run on our machine.

**From the checkpoint on: Claude Code inside the repository** as the main tool, so the agent edits, runs
`mvn test` and fixes code directly in the repo, and its sessions are exported to `agent-logs/`.
Codex: _(add if used)_.

## Found when running locally (16 Nov, evening)
- **System Java 11 / Maven 3.6.0 (Ubuntu 18.04)** are too old for Spring Boot 3. Installed Java 21 and
  Maven 3.9 with SDKMAN (no root, system Java untouched). Then `mvn test`: 24 tests, 0 failures.
- **Python 3.6** on the system: `check_secrets.py` used `capture_output` (Python 3.7+). Fixed to
  `stdout=subprocess.PIPE`; the pre-commit hook calls `python3` only.
- **Outdated default model.** `gemini-2.5-flash` (the agent's default, from its own knowledge) returned
  HTTP 404 "no longer available to new users". Found through `logs/vision.jsonl` (status 404) and a direct
  curl to the API. `gemini-3.8-flash` then returned 503 "high demand". Listed the models available to our key
  and set the default to `gemini-3.6-flash`, the model named in our proposal. Not using `*-latest` aliases:
  the model behind them can change and break reproducibility of the evaluation.
  Lesson: check model ids against the provider's model list, not against memory.

## First real photo (16 Nov, ~21:30)
- **Real product added:** Анальгин 500 мг таблетки, Борисовский ЗМП, KZ registration РК-ЛС-5№011176,
  leaflet text from MedElement (Kazakhstan drug directory). `/identify/fields` → `analgin-500-tab-bzmp`,
  `/retrieve "Как хранить?"` → section "Условия хранения" with the verbatim leaflet text.
- **Gemini HTTP 400 was API_KEY_INVALID** (a wrong value in the shell variable). The log stored only the status
  code, so we found the reason with a direct curl. Key now loaded from a git-ignored `.env`; `check_secrets.py`
  extended to the `AQ.` key format and tested (blocked a test commit).
- **Latency:** gemini-3.6-flash needed 92 s for one package photo (one direct call, HTTP 200; n = 1, not a
  general figure). Our 30 s timeout cut the request, and timeouts were not logged at all. Timeout now
  configurable (`GEMINI_TIMEOUT_SECONDS`, default 150 s). If typical, ~90 s is too slow for a phone app: to
  measure on the photo set: gemini-3.5-flash / flash-lite, lower thinking, smaller image.
- **Brand vs legal manufacturer name:** the package says "Borimed", the leaflet says "Борисовский завод
  медицинских препаратов". Our matcher treats them as different → MANUFACTURER_MISMATCH → asks the user.
  Safe, but an extra step. Planned: `manufacturerAliases` in catalogue.json.
- **Not a finding yet:** in the direct test (an English one-line prompt, not our production prompt) the answer
  was in English ("Tablets"). Our production prompt asks to copy text as printed; field-level accuracy on the
  photo set will show whether it does.
