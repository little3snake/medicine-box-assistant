# Decisions and outstanding choices

## 16 November 2026 — implementation update

- Android Studio and SDK are now installed; the Android debug build passes.
- Work is isolated on `feature/android`. Existing main backend core is preserved.
- Local `.env` loading is supported because the user explicitly requested file-based
  key storage. The file remains ignored and Android never receives its contents.
- Answer prose is assembled from exact validated quotations for this checkpoint;
  model-selected evidence must belong to the requested product and retrieved passages.
- The first Cyrillic personal-question test exposed incorrect regex word boundaries.
  Unicode character classes fixed the observed failure; this heuristic is not a
  validated medical classifier.
- Current sample records cannot support genuine medicine answers. Integration needs
  real sourced leaflets, passage identifiers and Catalogue/Retriever Spring beans.
- Confirmation currently lives in the Android flow; server-side confirmed-session
  enforcement remains an integration/design consideration.

## 16 November 2026 — checkpoint planning

- **Observed baseline:** GitHub commit `4f6e42b` contains `Proposal.md` only. Application code, a corpus and API access have not yet been verified.
- **Scope authority:** use the current GitHub proposal and teacher discussion as inputs. The latest proposal promises native Android, hybrid retrieval, Kazakh and a larger final evaluation; these differ from the earlier local draft. Do not silently replace these commitments.
- **Roles confirmed:** Tatiana confirmed that the GitHub roles stand: Java backend belongs to Angelina; Android and answer generation belong to Tatiana. Angelina starts later. Begin Tatiana's work against agreed example responses and fixed passages, then integrate the real backend before calling the checkpoint flow complete.
- **Model candidate:** Gemini 3.6 Flash is documented as a stable image/text model with structured output and a free API tier. Recommend it for the first prototype, subject to account quota and an actual test request. No API key or paid upgrade has been verified or authorized. Keep the provider/model configurable.
- **Language pending confirmation:** both members previously said they do not know Kazakh. The proposal promises native-speaker review. Confirm access to a reviewer or discuss changing the milestone with the instructor.
- **Platform dependency:** Android SDK, adb and Android Studio were not found at standard local locations. Check the teammate's environment or install the required tools. A browser fallback would deviate from the proposal and needs explicit agreement.
- **Model correction:** an earlier agent recommendation treated a specific Gemini version as selected before a working account/model call had been tested. Treat model names, eligibility, quotas and costs as unverified until a successful request. Record the exact provider/model used in the demo.
- **Clinical scope:** answer general leaflet questions with evidence and qualifications. A question about headache or a child does not by itself establish individual suitability. Preserve confirmation, evidence and uncertainty requirements across model tiers.
- **Checkpoint evidence:** mock responses may support parallel development but must be labelled. The assessed end-to-end flow should use a real API and real official leaflet passages.
