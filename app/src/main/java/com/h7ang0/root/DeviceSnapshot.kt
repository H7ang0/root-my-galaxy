package com.h7ang0.root

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.util.Locale

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val device: String,
    val kernelRelease: String,
    val buildId: String,
    val fingerprint: String,
    val androidRelease: String,
    val sdk: Int,
    val abi: String,
    val pageSize: Long,
    val totalMemBytes: Long,
    val uptimeMillis: Long,
) {
    val targetLabel: String
        get() = "$kernelRelease / $buildId"

    val kernelVersion: String
        get() = kernelRelease.takeWhile { it.isDigit() || it == '.' }

    val totalMemLabel: String
        get() = String.format(Locale.US, "%.2f GB", totalMemBytes / 1024.0 / 1024.0 / 1024.0)

    val uptimeLabel: String
        get() {
            val totalMinutes = uptimeMillis / 60_000
            val days = totalMinutes / (60 * 24)
            val hours = (totalMinutes / 60) % 24
            val minutes = totalMinutes % 60
            return buildString {
                if (days > 0) append("${days}d ")
                append("${hours}h ")
                append("${minutes}m")
            }.trim()
        }

    companion object {
        fun current(context: Context): DeviceSnapshot {
            val uname = Os.uname()
            val memInfo = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .getMemoryInfo(memInfo)
            return DeviceSnapshot(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                device = Build.DEVICE,
                kernelRelease = uname.release,
                buildId = Build.DISPLAY,
                fingerprint = Build.FINGERPRINT,
                androidRelease = Build.VERSION.RELEASE,
                sdk = Build.VERSION.SDK_INT,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                pageSize = Os.sysconf(OsConstants._SC_PAGESIZE),
                totalMemBytes = memInfo.totalMem,
                uptimeMillis = SystemClock.elapsedRealtime(),
            )
        }
    }
}
