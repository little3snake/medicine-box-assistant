# Android screens and real API

Open `android/` in Android Studio, sync, choose `app` and an emulator, then Run.
The project targets SDK 37, minimum API 26; Gradle uses a Java 25 daemon.

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

The connected tests need an emulator and clear this app's test preferences. Use a
dedicated emulator to preserve manual test data.

## Real mode

Start the backend with JDK 21+ and Maven:

```powershell
cd backend
mvn test
mvn spring-boot:run
```

JAVA_HOME points to the JDK directory, not its `bin`. Set GEMINI_API_KEY in the
environment or ignored root/backend `.env`: `GEMINI_API_KEY=your_value`, without
quotes or `export`. Restart the server after editing it. Never put keys in Android.
The answer component's local property source is shared by the Spring context,
including the vision client. Environment variables take precedence.

In the app's settings gear, turn off demo and save `http://10.0.2.2:8080` for the
Android Studio emulator. A physical phone needs the computer's reachable LAN IP.
HTTP is enabled only in debug; release endpoints must use HTTPS.

Android 17 (API 37) also requires runtime local-network permission for these development
addresses. Allow the system prompt when enabling API mode. If denied, open connection
settings and choose "Разрешить доступ к локальной сети". The app checks for denial before
sending local requests. Public API hostnames do not trigger this development permission.

Optional live Android transport test, with the backend running:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.medicineboxassistant.BackendConnectionTest -Pandroid.testInstrumentationRunnerArguments.medboxServer=http://10.0.2.2:8080
```

This test grants local-network permission through test automation and checks health plus
real manual identification. With no medboxServer argument it is skipped.

Current catalogue: six variants, five marked samples, and one real sourced leaflet:
`analgin-500-tab-bzmp`. It is not a complete medicine database. Sample records cannot
produce medical answers. The 16 November integration smoke check returned healthy
API and the expected real variant for manual fields, but `visionConfigured: false`:
the local GEMINI_API_KEY was empty. No successful live model call is claimed.

## Screen flow

Home → camera/gallery or manual input → candidates when needed → explicit packaging
confirmation → question shortcuts or chat → expandable quotations / full leaflet.

The cabinet persists product metadata and IDs in app-private preferences, scoped by
server and demo mode. It does not persist photos, questions or generated answers.
Opening a saved product reloads its data and requires confirmation again. Instructions
are loaded online; this is not an offline leaflet store.

Aa enlarges text in addition to system font scaling. Main actions are labelled and
at least 56 dp high. Long content scrolls; leaflet sections expand on demand. Changing
printed fields clears the old confirmation and answers. Provider/network failures
preserve input and offer retry. Photo input accepts JPEG/PNG/WebP, at most 10 MB.
Backend model timeout defaults to 150 seconds; Android waits 180 seconds and offers cancel.

## Verification

- Android debug build and unit test task passed.
- Device tests cover confirmation before chat, demo answer labelling, saved cabinet
  across activity recreation and re-confirmation of saved products.
- 37 backend tests passed, including four real Spring integration tests for API/core
  wiring, passage IDs, readable card, sample rejection and referral without a model.
- Actual photo → Gemini → confirmed product → answer quotation remains to be checked
  with a valid key/quota. Exact quotations alone do not establish medical correctness.

After adding the key, check `/api/health` for `visionConfigured: true`, then run that
complete flow on a matching package. Record model ID, latency and outcome separately
from deterministic tests. User testing and accessibility review still remain.
