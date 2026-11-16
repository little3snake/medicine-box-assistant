package com.medicineboxassistant

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.URI

object LocalServerAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun isLocalServer(address: String): Boolean {
        val host = runCatching { URI(address).host?.lowercase()?.removePrefix("[")?.removeSuffix("]") }.getOrNull() ?: return false
        if (host == "localhost" || host == "::1" || host.endsWith(".local") || host.endsWith(".lan")) return true
        if (host.contains(':')) return host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:")
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size == 4 && parts.all { it in 0..255 }) return parts[0] == 10 || parts[0] == 127 ||
            (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 169 && parts[1] == 254)
        return !host.contains('.')
    }

    fun missing(context: Context, address: String): Boolean = Build.VERSION.SDK_INT >= 37 &&
        isLocalServer(address) && ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED
}
