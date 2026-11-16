# PLAN.md — Medicine Box Assistant

**AI search for official medicine leaflets in Kazakhstan**
CSS4007 Artificial Intelligence — Final Project
Team: Subbotina Tatiana, Angelina Ivleva
Written before implementation (16 Nov 2026). Changes to this plan are recorded in DECISIONS.md.

---

## 1. Problem and users

Every medicine registered in Kazakhstan has an official leaflet (instruction for medical use) in the state register (register.ndda.kz). One trade name exists in several strengths, dosage forms and from several manufacturers, and each variant has its own leaflet. Leaflets are long, use medical language and are hard to read on a phone. A general chatbot answers without evidence and may describe a different variant.

**Users:** adults in Kazakhstan holding a medicine package (including parents and caregivers checking a medicine for a child) who want, on their phone, a readable version of the official leaflet for **that exact product** and answers to questions like "Can children take it?", "How should I store it?", "What are the side effects?".
**Not for:** children using the app alone, diagnosis, personal treatment advice. The app says this on first launch.

## 2. What the product does (user flow)

1. User photographs the package.
2. A vision model reads the printed fields: name, strength, dosage form, manufacturer.
3. The backend matches the fields against our curated catalogue and returns ranked candidates.
4. If identification is uncertain, the app asks the user to confirm or correct (never guesses).
5. For the confirmed product the app shows a **readable card**: what it is for, who must not take it, use in children, main side effects, storage. Each line links to the verbatim leaflet passage.
6. User asks questions. The answer is built **only** from that product's leaflet, with verbatim quotes, section names and source link — or an explicit "not enough information" / "ask a pharmacist".

## 2a. Existing solutions (details: docs/RESEARCH.md)

The state register and manufacturer PDFs are official but hard to use on a phone; i-teka (Kazakhstan) is for prices and pharmacies; AI scanner apps (e.g. MedScan) give AI-written summaries, not official text; mojApteczka (Poland) matches packages against a national register, which is the design closest to ours. Our difference: the exact Kazakhstan variant, verbatim quotes from its official leaflet, Russian and Kazakh, and explicit abstention.

## 3. Scope

**MVP (checkpoint, 16 Nov):** the whole flow above works end to end, roughly, in Russian, on a small catalogue (5–12 variants incl. look-alike pairs), with the first 20 evaluation cases runnable by script.
**Full product (code freeze, 13 Dec):** 40+ variants, Russian and Kazakh, hybrid retrieval, full evaluation, baselines and free-vs-paid model comparison.
**Stretch goals:** (1) leaflet viewer that highlights the passages used in an answer; (2) comparison mode for two similar products.
**Out of scope:** production deployment, user accounts, collecting personal data, any dose calculation.

## 4. Architecture

```
Android app (Kotlin, Jetpack Compose, CameraX)
      │ HTTP/JSON (no API keys in the app)
      ▼
Spring Boot backend (Java 21, Maven)
  ├─ Catalogue        data/catalogue.json + data/leaflets/<variant>/<lang>.json
  ├─ Vision client    Gemini: photo → {name, strength, form, manufacturer}
  ├─ Product matcher  fields → ranked candidates + needsClarification
  ├─ Retriever        question → top-k sections of the CONFIRMED leaflet only
  ├─ Answer module    passages → LLM answer with verbatim quotes → quote check
  └─ Card module      leaflet → readable card, every line linked to a passage
Tools: tools/ingest_leaflet.py (leaflet text → section JSON)
Eval:  eval/ (cases, runners, results)
```

Design principle: **the model reads and writes text, the code makes the safety decisions.**
This is a fixed **workflow**, not an agent: the code decides the path (Lecture 7, slide 23: use the least autonomy that works). The vision model only extracts fields; the matcher decides the product. The LLM writes answers; the quote checker decides whether they are allowed.

## 5. API contract (fixed before parallel work; changes go to DECISIONS.md)

| Method | Path | Request | Response | Owner |
|---|---|---|---|---|
| GET | `/api/health` | – | status, variant count, vision configured | Angelina |
| GET | `/api/products?q=` | text | list of variants | Angelina |
| GET | `/api/products/{id}/leaflet?lang=` | – | verbatim sections | Angelina |
| POST | `/api/identify` | multipart `image` | `{extracted, candidates[{variantId, label, score}], needsClarification, reasons}` | Angelina |
| POST | `/api/identify/fields` | `{name, strength, form, manufacturer}` | same as above | Angelina |
| POST | `/api/products/{id}/retrieve` | `{question, lang, k}` | `{usedLang, langFallback, passages[{passageId, sectionType, title, text, sourceUrl, score}]}` | Angelina |
| GET | `/api/products/{id}/card?lang=` | – | `{lines[{field, text, passageId}]}` | Tatiana |
| POST | `/api/products/{id}/ask` | `{question, lang}` | `{status: ANSWERED│CLARIFY│INSUFFICIENT│REFER_TO_PHARMACIST, answer, quotes[{passageId, section, text, sourceUrl}]}` | Tatiana |

## 6. AI components

| Component | Method (MVP) | Method (final) |
|---|---|---|
| Field extraction | Gemini vision, JSON schema, unreadable → null, every call logged | same; field-level accuracy; compare free vs paid model |
| Product matching | weighted score: 0.50 name + 0.25 strength + 0.15 form + 0.10 manufacturer; confident only if score ≥ 0.85, margin ≥ 0.10, strength and form read and agree | thresholds tuned on the tuning split only |
| Retrieval | BM25 over the sections of the confirmed leaflet; section titles + everyday keywords added | hybrid: BM25 + multilingual embeddings, scores combined |
| Answer generation | LLM answers only from retrieved passages, must quote verbatim | same + baselines (section 8) |
| Readable card | selected leaflet sections shown directly | LLM plain-language summary, each line linked to a passage, checked by rubric |

## 7. Safety rules (enforced in code and covered by tests)

1. Answers use only passages from the **confirmed** product's leaflet.
2. Dosing (including for children) is **quoted verbatim, never paraphrased or calculated**.
3. Every quote must appear verbatim in the leaflet (automatic check); otherwise status `INSUFFICIENT`.
4. Questions not supported by the leaflet → `INSUFFICIENT`.
5. Personal treatment questions → `REFER_TO_PHARMACIST`.
6. Uncertain identification → clarification. A **confident wrong match** is the main safety metric.
7. Kazakh leaflet used when available; otherwise Russian, and the app says so.

## 8. Evaluation

**Data:** 40 package photos (own packages) and 100 annotated questions (50 RU, 50 KK; Kazakh checked by a native speaker). Includes look-alike products, unclear photos, out-of-catalogue products, use-in-children and indication questions, unanswerable questions, personal treatment questions.
**Split:** 30% tuning, 70% held out (fixed in the case files before tuning; prompts and thresholds are tuned only on the tuning split).

| Metric | Measures | How |
|---|---|---|
| Exact-product identification accuracy | vision + matcher | automatic |
| Incorrect confident matches | safety of identification | automatic (lower is better) |
| hit@k of the supporting passage | retrieval | automatic |
| Quote validity | quotes exist verbatim | automatic |
| Answer support, card correctness | answer quality | written rubric, manual |
| Correct clarification / abstention | safety behaviour | automatic from status |
| Cost per answer, latency | practicality | logged |

All rates reported with a **95% Wilson interval**, separately for RU and KK; retrieval next to its chance level; field-level exact match for extraction (Lectures 4–6).

**Comparisons:**
1. Our pipeline vs two baselines with the same model: (a) no documents; (b) full leaflet in the prompt.
2. Free Flash-tier model vs a stronger paid model (small capped budget): accuracy, cost per answer, latency — is the paid model worth it?

## 9. Data and external services

- **Leaflets:** collected manually from register.ndda.kz and public manufacturer sources, RU and KK where available; we store source URL and retrieval date per leaflet. Reuse conditions of the register are checked and recorded in DECISIONS.md.
- **Photos:** our own packages only; no personal data.
- **Models:** Gemini Developer API (free tier main model; paid stronger model for the comparison). Free quotas and current model ids checked before use.
- **Secrets:** API keys only in environment variables on the backend. Never in the app, the repo or the agent logs.

## 10. Working with agentic tools

- Tools: OpenAI Codex and Claude Code (paid). Stronger model for planning and hard bugs, cheaper model for routine edits.
- AGENTS.md holds shared rules for both tools (safety rules, no secrets, tests with every change, plan before big tasks).
- One focused task per agent session; run tests before accepting; review every diff ourselves.
- Save 3–5 key sessions to `agent-logs/` (secrets removed). Record agent mistakes and tool switches in DECISIONS.md.

## 11. Timeline

| Week | Dates | Angelina (backend, identification, retrieval) | Tatiana (Android, answers, card) | Together |
|---|---|---|---|---|
| 1 | 11–16 Nov | Spring Boot skeleton, catalogue, vision + matcher, BM25 retrieval, tests | Android camera → confirm → question screens; `/ask` with quote check | PLAN.md, AGENTS.md, API contract, first 20 eval cases. **Checkpoint 16 Nov** |
| 2 | 17–23 Nov | Kazakh retrieval, real leaflets via ingestion (12+ variants) | Kazakh UI and answers; readable card v1 | Collect leaflets, start photo set |
| 3 | 24–30 Nov | Hybrid retrieval (embeddings); grow catalogue to 25+ | Card with linked passages; abstention and referral rules | Annotate questions; baselines |
| 4 | 1–7 Dec | Catalogue 40+; tune matcher on tuning split | Stretch goal (leaflet viewer) if on track | Full evaluation; free vs paid comparison |
| 5 | 8–13 Dec | README from a clean machine, cleanup, secret scan | Final UI fixes, answer tests | REFLECTION.md, tag **v1.0 by 13 Dec** |
| – | 16–19 Dec | | | Defense: live demo, 2–3 min backup video, tools discussion |

## 12. Division of work

- **Tatiana:** Android app (camera, confirmation, card, questions), answer generation (prompts, verbatim quotes, abstention, dosing rule, referral), readable card, quote checker, answer-quality tests and metrics.
- **Angelina:** Spring Boot backend and REST API, catalogue storage, vision-model integration, product matching and confirmation, leaflet ingestion and retrieval index, identification and retrieval tests and metrics.
- **Both:** collect and cross-check leaflets, agree on interfaces, build and annotate the evaluation set, baselines and model comparison, integration tests, commits and agent logs from each of us, REFLECTION.md, defense.

## 13. Risks

| Risk | Mitigation |
|---|---|
| Free API quota runs out | `/identify/fields` and cached responses for evaluation; cheap model for routine agent edits |
| Look-alike products confused | strength/form must agree for a confident match; otherwise ask the user |
| Kazakh leaflet missing | fall back to Russian and tell the user |
| Register reuse conditions unclear | check before using; store only what is allowed, with URL and date |
| Vision misreads text | user can correct fields on the confirmation screen |
| Partner unavailable | tell instructors at once (per course rules) |
| Integration breaks late | fixed API contract (section 5); integrate every week, not at the end |

## 14. Definition of done (code freeze)

- [ ] Flow works end to end on a clean machine following README.md
- [ ] 40+ variants, RU + KK
- [ ] Unit tests for matcher, retrieval, quote checker, answer rules
- [ ] Full evaluation with Wilson intervals per language, baselines, free vs paid comparison
- [ ] PLAN.md, AGENTS.md, agent-logs/ (3–5 sessions), DECISIONS.md, REFLECTION.md
- [ ] No secrets in the repository or its history
- [ ] Commits from both members; tag `v1.0`
