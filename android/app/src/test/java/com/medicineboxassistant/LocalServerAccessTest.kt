package com.medicineboxassistant

import org.junit.Assert.*
import org.junit.Test

class LocalServerAccessTest {
    @Test fun emulatorLanAndLocalNamesRequireLocalAccess() {
        listOf("http://10.0.2.2:8080", "http://192.168.1.3", "http://172.16.0.1", "http://172.31.2.3",
            "http://127.0.0.1", "http://[::1]", "http://[fd00::1]", "http://server.local", "http://medbox")
            .forEach { assertTrue(it, LocalServerAccess.isLocalServer(it)) }
    }
    @Test fun publicEndpointsDoNotTriggerLocalPermission() {
        listOf("https://api.example.com", "https://8.8.8.8", "http://172.32.0.1", "http://192.169.1.1", "invalid")
            .forEach { assertFalse(it, LocalServerAccess.isLocalServer(it)) }
    }
}
