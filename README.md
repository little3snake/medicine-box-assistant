# Medicine Box Assistant

Android application for identifying a medicine package, confirming its exact variant,
and reading leaflet answers with verified quotations. This is a student prototype.

## Current status

The Android client builds and has separate **Demo** and **API** modes. Demo uses
clearly labelled synthetic responses and does not call a model. API mode implements
the contract in `PLAN.md`. A complete real-data flow is not yet verified.

The answer module provides `/api/products/{id}/ask` and `/card`. It needs Spring
`Catalogue` and `Retriever` beans from the backend integration. Without them it
returns HTTP 503. Sample catalogue entries are rejected for leaflet answers.

## Android

Install Android Studio and its Android SDK. Open `android/` and let Gradle sync.
The project uses SDK 37, minimum Android 8 (API 26), and a Java 25 Gradle daemon.
On a clean machine Gradle needs internet access to download its dependencies and JDK.

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Run the `app` configuration on an emulator or Android device. In the app settings,
choose API mode and use `http://10.0.2.2:8080` for a backend on the emulator host.
A physical phone needs the computer's reachable LAN address. HTTP is enabled only
in debug builds; production endpoints must use HTTPS. No API key belongs in Android.

Select/take a photo or enter printed fields, search for variants, select the exact
package, then explicitly confirm it before asking a question.

## Backend

Install JDK 21 or later and Maven. The build targets Java 21.

```powershell
cd backend
mvn test
mvn spring-boot:run
```

Set `GEMINI_API_KEY` in the backend environment. As requested for local development,
the answer client also reads a git-ignored `.env` in the repository root or backend
working directory. Its syntax is Java properties: `GEMINI_API_KEY=your_value`, with
no `export` or surrounding quotes. Environment variables take precedence.
`GEMINI_MODEL` is configurable; the default is `gemini-3.6-flash`. Availability,
quota and an actual provider call still need verification for your account.

Runtime model metadata goes to ignored `logs/answer-calls.jsonl`. Do not commit
keys, personal photos or runtime logs.

## Integration handoff

Angelina's backend supplies the identify/fields, identify/photo, product, leaflet and
retrieval endpoints from `PLAN.md`, and registers `Catalogue` and `Retriever` beans.
Every leaflet section needs a unique, nonempty `passageId`; source URLs must use
HTTPS, and production records must contain real sourced text with `sample: false`.
Tatiana's isolated implementation is in `kz.medbox.answers` and `android/`.

The answer model selects evidence; application code validates exact quotations and
constructs the answer from those quotations. This intentionally limits generated
prose for the checkpoint. Personal-treatment detection is a heuristic and still
requires evaluation. Exact quote validation alone does not prove relevance.

Before submitting the checkpoint: test a real photo through the actual backend and
model, confirm a variant, ask a question, inspect its source, and record the tested
commit. A demo-only run is not evidence that this integration works.

## Checks

```powershell
python tools/check_secrets.py --all
```

Backend tests currently cover matching, retrieval, quote validation, mixed variants,
sample rejection and personal-treatment referral. Provider connectivity and the
complete Android-to-backend flow are separate pending integration checks.
