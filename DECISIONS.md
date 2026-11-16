# DECISIONS.md

Format: date — decision — why (with the course source where there is one) — who / which tool.
Also records where an agent was wrong and how we noticed. Lecture references use the slide number printed
on the slide (Lecture 4 is an HTML page: section numbers).

## Backend (Angelina)

**16 Nov — Workflow, not agent.** Photo → field extraction → matching → retrieval is a fixed pipeline: the
code decides the path; no model chooses tools in a loop. Why: Lecture 7 slide 23 "use the least autonomy
that works" — one call with good context or a fixed workflow before an agent; slide 20 measured a workflow at
0.54× an agent's cost with the same accuracy on that task. Our path never varies, so an agent would add cost
and new failure modes and nothing else.

**16 Nov — The vision model only reads; code decides.** Gemini returns four printed fields; `ProductMatcher`
chooses the product. Why: Lecture 4 §08 "one call, one decision", and a confident wrong match is our main
safety metric, so that decision must be inspectable and testable code.

**16 Nov — Vision prompt: plain task, schema, null instead of guess.** No persona, no "think step by step",
no tips. Why: Lecture 4 §13 (personas do not improve accuracy) and §07 (CoT gives little on current models
and costs latency). JSON schema with nullable fields: Lecture 6 slide 19, "a field invented instead of null —
ask for null; count the nulls". If the API rejects the schema (HTTP 400), we retry once in plain JSON mode and
log it.

**16 Nov — Package text is untrusted input.** Text on a photo could contain instructions (prompt injection,
Lecture 4 §13 no. 5; Lecture 8 slide 25). Mitigation by design, not by prompt: the vision call has no tools,
its output is four strings that code validates, and it cannot send anything anywhere. In Rule-of-Two terms
(Lecture 8 slide 25) the backend has untrusted input but no sensitive data and no outbound actions.

**16 Nov — Log every vision call.** `logs/vision.jsonl`: model, latency, tokens, schema used, parsed fields,
SHA-256 of the image. Why: Lecture 6 slide 17 (for a vision model "keep the image and the reply" to debug);
Lecture 8 slide 23 ("if you did not log it, it did not happen"); token counts give cost per request (Lecture 2).

**16 Nov — Matching = weighted score + strict confidence rule.** Score = 0.50 name + 0.25 strength + 0.15
form + 0.10 manufacturer. Confident only if score ≥ 0.85, margin to the next candidate ≥ 0.10, and strength
and form were read AND agree. Why: look-alike variants differ mostly in strength or form, and those change
dosing. These are starting values, not tuned; tuning only on the tuning split.

**16 Nov — Known limitation: manufacturer-only look-alikes always ask.** On the first 10 identification cases
(SAMPLE data, run on the core matcher), the right product is ranked first in 10/10 and there are 0 confident
wrong matches, but the 2 cases differing only by manufacturer return `AMBIGUOUS` (margin 0.09 < 0.10).
Safe, but an extra confirmation step. With n = 10 this says little (Lecture 4 §12); revisit with real photos.

**16 Nov — Retrieval: BM25 over leaflet sections; embeddings later.** One chunk = one leaflet section of the
confirmed product. Section titles plus everyday words per section type are indexed, so "Можно ли детям?"
reaches "Применение в педиатрии". Light Russian suffix stripping, not a full stemmer. Why BM25 first: no
model, no cost, exact words of the leaflet; Lecture 5 slide 20 shows paraphrase recall is the weak point of
embeddings in Kazakh (0.21 recall@1), so hybrid search must be measured per language, not assumed better.
Note for 23 Nov: long sections must be split before embedding — Lecture 5 slide 23 shows chunks that
overflow the embedding model's window are cut silently.

**16 Nov — Ingestion follows the real leaflet format.** Research (docs/RESEARCH.md §3) showed current
Kazakhstan leaflets use headings like "Перечень сведений, необходимых до начала применения", "Рекомендации
по применению", "Описание нежелательных реакций", "Сведения о производителе". `ingest_leaflet.py` knows both
current and older headings, keeps text verbatim, and drafts the catalogue entry from the leaflet header.

**16 Nov — Evaluation reports chance level, per-language results and field-level accuracy.** Why: Lecture 5
slide 32 (decision 5: "is the score above chance for your index?"), Lecture 6 slide 19 (field-level exact
match against a truth file), Lecture 4 §10–12 (Wilson intervals; small n cannot show small differences).

**16 Nov — Scope cut for the checkpoint.** The proposal promised 8–12 real variants by 16 Nov. The checkpoint
has 5 SAMPLE variants (fictional names, placeholder leaflet text, `"sample": true`) with look-alikes by
strength, by form and by manufacturer. Why: the pipeline had to work end to end first, and we will not put
invented medical text in the repo. Next: real leaflets via `tools/ingest_leaflet.py`.

**16 Nov — Framework-free core.** Matching, normalisation and BM25 are in `kz.medbox.core` with no Spring
imports. Why: fast unit tests and code we can explain line by line at the defense.

**16 Nov — `/api/identify/fields`.** Same matcher without the vision call: user corrections on the
confirmation screen, evaluation without spending API quota, fallback when the vision API is down.

**16 Nov — Secret check as a pre-commit hook.** `tools/check_secrets.py`. Why: an instruction in AGENTS.md
is advice, not law (Lecture 8 slide 12); a check before the action is part of the harness (slide 27); a key
in git history costs points.

**16 Nov — AGENTS.md shared, CLAUDE.md imports it.** One rule file for both tools, under 200 lines
(Lecture 8 slide 12: longer files reduce adherence).

**16 Nov — Maven instead of Gradle.** Simpler single-module build.

## Mistakes found during development
- **Duplicate stop words** ("ли" three times) → `Set.of` threw at class load and every test failed. Found by
  running the unit tests; fixed.
- **Ingestion heading clash:** "Описание нежелательных реакций" (side effects) matched the shorter heading
  "Описание" (form). Found by reviewing headings; fixed with longest-prefix matching and tested.
- **First ingestion version only knew the older leaflet headings.** Found by looking at real current
  leaflets during research; fixed and covered by a retrieval test with current headings.
- **Eval runner paths** are relative to the repo root; running it from `eval/` fails. Documented in README.

## Tools used
- Backend first version drafted with Claude (agentic session that wrote files, compiled and ran the core
  tests in a sandbox). Spring layer and JUnit tests then run locally with `mvn test`.
  _(Add: Codex / Claude Code sessions in the repo, and any tool switch.)_

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
