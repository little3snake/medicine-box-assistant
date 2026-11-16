package com.medicineboxassistant

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertTrue

/** Opt-in live transport check: pass -Pandroid.testInstrumentationRunnerArguments.medboxServer=URL. */
class BackendConnectionTest {
    @Test fun manualIdentificationReachesBackend() = runBlocking<Unit> {
        val server = InstrumentationRegistry.getArguments().getString("medboxServer")
        assumeTrue("Live backend URL not supplied", !server.isNullOrBlank())
        if (android.os.Build.VERSION.SDK_INT >= 37) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, LocalServerAccess.PERMISSION)
        }
        val api = MedboxApi(server!!)
        val started = System.currentTimeMillis()
        try {
            assertTrue(api.health())
            val result = api.identifyFields(PackageFields("Анальгин", "500 мг", "таблетки", "Борисовский завод медицинских препаратов"))
            assertTrue(result.candidates.any { it.variantId == "analgin-500-tab-bzmp" })
            Log.i("MedboxTransport", "Live health and manual identification passed in ${System.currentTimeMillis() - started}ms")
        } catch (exception: Exception) {
            Log.e("MedboxTransport", "Live transport failed after ${System.currentTimeMillis() - started}ms", exception)
            throw exception
        }
    }
}
