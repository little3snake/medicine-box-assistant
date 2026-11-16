# Medicine Box Assistant

AI search for official medicine leaflets in Kazakhstan. CSS4007 Artificial Intelligence, final project.
Team: Subbotina Tatiana, Angelina Ivleva.

You photograph a medicine package. The app identifies the **exact** product (name, strength, dosage form, manufacturer), shows a readable card built from the official leaflet, and answers questions using only verbatim, cited passages from that product's leaflet. Not medical advice; intended for adults.

> **Checkpoint status (16 Nov):** backend works end to end on **SAMPLE data** (fictional products marked `"sample": true`, placeholder leaflet text). Real leaflets from register.ndda.kz are added with `tools/ingest_leaflet.py`. See DECISIONS.md.

## Repository layout
| Path | What |
|---|---|
| `backend/` | Spring Boot API: catalogue, vision identification, product matching, BM25 retrieval |
| `android/` | Android app (Kotlin, Compose), see its own section below |
| `data/` | `catalogue.json` + `leaflets/<variant-id>/<lang>.json` |
| `tools/ingest_leaflet.py` | Converts a leaflet copied as plain text into section JSON (verbatim) |
| `eval/` | Evaluation cases and runners |
| `agent-logs/` | Key sessions with our coding agents |
| `PLAN.md`, `AGENTS.md` (+ `CLAUDE.md`), `DECISIONS.md` | Plan, agent instructions, decisions |
| `docs/RESEARCH.md` | Existing solutions and the official leaflet format |
| `tools/check_secrets.py` | Secret scan, also the git pre-commit hook |

## Backend: run from a clean machine
Requirements: **Java 21**, **Maven 3.9+**, Python 3.10+ (only for tools and eval).

```bash
git clone <repo-url> && cd <repo>
python tools/check_secrets.py --install-hook   # once per clone
cd backend
export GEMINI_API_KEY=...        # Windows PowerShell: $env:GEMINI_API_KEY="..."
# optional: export GEMINI_MODEL=<model id from Google AI Studio>
mvn test                         # unit tests
mvn spring-boot:run              # http://localhost:8080
```
Without `GEMINI_API_KEY` everything works except photo identification (`/api/identify` returns 503 with a hint).
Every vision call is logged to `backend/logs/vision.jsonl` (model, latency, tokens, parsed fields; never the key).

### API
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Status, number of variants, whether vision is configured |
| GET | `/api/products?q=` | List / search products (manual choice) |
| GET | `/api/products/{id}` | One product + available leaflet languages |
| GET | `/api/products/{id}/leaflet?lang=ru` | All leaflet sections, verbatim |
| POST | `/api/identify` | multipart `image` → vision model → match result |
| POST | `/api/identify/fields` | JSON `{name, strength, form, manufacturer}` → match result (user corrections, evaluation) |
| POST | `/api/products/{id}/retrieve` | `{question, lang, k}` → top-k passages from this product's leaflet only |

Match result: `{extracted, normalized, candidates:[{variantId, label, score, breakdown}], needsClarification, reasons}`.
`needsClarification=true` means the app must ask the user to confirm (reasons: `LOW_SCORE`, `AMBIGUOUS`, `STRENGTH_NOT_READ`, `STRENGTH_MISMATCH`, `FORM_NOT_READ`, `FORM_MISMATCH`, `NO_CANDIDATES`).

### Try it end to end without the app
```bash
curl localhost:8080/api/health
curl -X POST localhost:8080/api/identify -F image=@photo.jpg
curl -X POST localhost:8080/api/identify/fields -H "Content-Type: application/json" \
  -d '{"name":"Парацетамол-Образец","strength":"500 мг","form":"таблетки","manufacturer":"Образец Фарм"}'
curl -X POST localhost:8080/api/products/sample-paracetamol-500-tab/retrieve -H "Content-Type: application/json" \
  -d '{"question":"Можно ли давать детям?","lang":"ru","k":3}'
```

### Add a real product
1. Add an entry to `data/catalogue.json` (`"sample": false`).
2. Copy the leaflet text from register.ndda.kz into a `.txt` file and run
   `python tools/ingest_leaflet.py --variant <id> --lang ru --source-url <url> --input leaflet.txt`
3. Check the printed section list, restart the backend.

### Backend evaluation
With the backend running, **from the repository root**: `python eval/run_backend_eval.py` (use `--split heldout` for held-out cases only).
Reports identification accuracy, confident wrong matches (main safety metric), top-1, field-level exact match for photo cases, and retrieval hit@1 / hit@3 next to the chance level — all with 95% Wilson intervals, per language; per-case CSV in `eval/results_backend.csv`.
Photo cases: put photos in `eval/photos/` and add cases with `"image"` and `"expectedFields"` (see `eval/cases_identify.json`).

## Android app
_(Tatiana: setup and run instructions.)_

## Third-party components
Spring Boot (Apache 2.0), Jackson (Apache 2.0), Google Gemini API (vision). Leaflet texts: State register of medicines of Kazakhstan, https://register.ndda.kz/ (source URL and retrieval date stored per leaflet).


## Android client

See [Android setup and current integration](docs/android-setup.md).
