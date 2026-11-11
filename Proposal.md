# Medicine Box Assistant
**AI Search for Official Medicine Leaflets in Kazakhstan**  
CSS4007 Artificial Intelligence — Final Project Proposal  
**Team:** Subbotina Tatiana and Angelina Ivleva

## 1. Subject area
Every medicine registered in Kazakhstan has an official leaflet (instruction for medical use) published
in the state medicines register. A leaflet follows fixed sections: composition, dosage form, indications,
contraindications, use in children, pregnancy, dosing, side effects, interactions, storage, manufacturer.
One trade name can exist in several strengths, dosage forms and from several manufacturers, and each
variant has its own leaflet. Leaflets are long, written in medical language, and hard to read on a phone.

## 2. Problem and users
People looking up a medicine can struggle to locate the official leaflet for the exact product they have.
Similar packaging can represent different strengths, dosage forms, or manufacturers, so searching the
register by name is not enough, and a general chatbot answers without evidence and may describe a
different variant. Our users are people in Kazakhstan holding a medicine package who want, on their phone,
a readable version of the leaflet for that exact product and answers to questions such as "Can children
take it?", "Which kind of headache is it for?", "How should I store it?" or "What are the side effects?".
The app is intended for adults, including parents and caregivers checking a medicine for a child; it is
not designed for children to use on their own, and it states this on first launch.

## 3. AI feature and evaluation
**Mobile app.** The user takes a photo of the package with the phone camera. A vision model extracts the
medicine name, strength, dosage form, and manufacturer. The application matches these fields against a
curated catalogue and asks the user to confirm the product or supply missing details.

**Readable leaflet.** For the confirmed product the app shows a short plain-language card: what it is
for, who must not take it, use in children, main side effects, storage. Every line links to the
verbatim leaflet passage it is based on.

**Questions (RAG).** Retrieval-augmented generation finds relevant passages within the confirmed
product's leaflet only, using chunks that follow the leaflet's sections and hybrid search (BM25 + a
multilingual embedding model). The app works in Russian and Kazakh; quotations come from the Kazakh leaflet
when one is available, otherwise from the Russian one. Answers include verbatim quotations, section names,
and source links. Ambiguous identification triggers clarification. Questions unsupported by the leaflet
receive an explicit insufficient-information response. Dosing, including doses for children, is only
quoted verbatim, never paraphrased or calculated. Personal treatment questions are detected and directed to
a pharmacist or clinician.

**Evaluation.** 40 packaging photos and 100 manually annotated questions (50 in Russian, 50 in Kazakh,
the Kazakh ones checked by a native speaker), including similar products, unclear photos,
out-of-catalogue products, use-in-children and indication questions, unanswerable questions, and personal
treatment questions. We measure exact-product identification accuracy, incorrect confident matches (main
safety metric), supporting-passage retrieval (hit@k), quote validity (quotations found verbatim in the
leaflet, checked automatically), answer support and readable-card correctness (judged against a written
rubric), and appropriate clarification or abstention. Each result is reported with a 95 % Wilson interval,
separately per language. 70 % of the cases are held out from prompt tuning.

**Comparisons.** (1) Our pipeline against two baselines with the same model: no documents, and the full
leaflet in the prompt. (2) **Free weak model against paid strong model:** the same pipeline with a free
Flash-tier model and with a stronger paid model, comparing accuracy, cost per answer and response time,
to show whether the stronger model is worth paying for.

## 4. Agentic development tools
We will use paid access to OpenAI Codex and Claude Code to plan, write, run, and debug code in our
repository, using the stronger model for planning and the cheaper one for routine edits. We will write
PLAN.md before the code, keep shared agent instructions in AGENTS.md, retain key agent sessions, and
document decisions, corrections, and tool switches in DECISIONS.md.

## 5. MVP and stretch goals
**MVP by 16 November:** an Android app (Kotlin, Jetpack Compose, CameraX) connected to our Spring Boot
backend with a curated catalogue of 8–12 product
variants (including at least 3 look-alike pairs): camera photo, editable recognition results, product
confirmation, the readable leaflet card, Russian-language questions, and answers with leaflet evidence
or a clarification/abstention message. The first 20 evaluation cases run end to end.
**By 23 November:** Kazakh interface, questions and answers.
**By code freeze:** 40+ variants, the full evaluation in both languages, and the free/paid model comparison.

**Stretch goals:** a leaflet viewer highlighting the passages used in each answer; a comparison mode for
two similar products.

## 6. Data and external APIs
We will manually collect official leaflets from Kazakhstan's [state medicines register](https://register.ndda.kz/) and
publicly accessible manufacturer sources, using Russian- and Kazakh-language leaflets where available, and
recording product identifiers, source URLs, and retrieval dates. We will photograph our own packaging; no
personal data is collected. Reuse conditions of the register will be verified before the checkpoint.
Models: Google's Gemini Developer API with Gemini 3.6 Flash on the free tier as the main model, and a
stronger paid model with a small capped budget for the comparison; free quotas and prices will be
verified this week. Documents are indexed on our backend; API keys are stored in environment variables
and never in the mobile app.

## 7. Responsibilities
**Subbotina Tatiana:** Android app in Kotlin with Jetpack Compose (camera, confirmation screen, questions),
evidence-backed answer generation (prompts, verbatim quotations, abstention, dosing rule), the readable
leaflet card, quote checking, and tests and metrics of answer quality.

**Angelina Ivleva:** Java backend (Spring Boot) and REST API, catalogue storage, vision-model
integration, product matching and confirmation, leaflet ingestion and the retrieval index (section
chunking, BM25 + embeddings), and tests and metrics of identification and retrieval.

**Both:** collect and cross-check source documents, agree on module interfaces, build and annotate
the evaluation set, run baseline and model comparisons and integration tests, contribute commits and
agent logs, and prepare the reflection and defense.
