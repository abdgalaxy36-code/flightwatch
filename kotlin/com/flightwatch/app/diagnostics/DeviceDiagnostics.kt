package com.flightwatch.app.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

object DeviceDiagnostics {
    
    fun collect(context: Context): Map<String, String> {
        val info = mutableMapOf<String, String>()
        
        // Device
        info["Manufacturer"] = Build.MANUFACTURER ?: "unknown"
        info["Model"] = Build.MODEL ?: "unknown"
        info["Device"] = Build.DEVICE ?: "unknown"
        info["Brand"] = Build.BRAND ?: "unknown"
        info["Product"] = Build.PRODUCT ?: "unknown"
        
        // Android
        info["Android Version"] = Build.VERSION.RELEASE ?: "unknown"
        info["SDK Level"] = Build.VERSION.SDK_INT.toString()
        info["Build ID"] = Build.DISPLAY ?: "unknown"
        info["Build Fingerprint"] = Build.FINGERPRINT ?: "unknown"
        info["Kernel"] = System.getProperty("os.version") ?: "unknown"
        
        // CPU
        info["CPU ABI"] = Build.SUPPORTED_ABIS.joinToString(", ")
        info["CPU ABI (primary)]"] = Build.CPU_ABI ?: "unknown"
        
        // Memory
        val memInfo = getMemoryInfo()
        info["Total RAM"] = memInfo.first
        info["Available RAM"] = memInfo.second
        
        // Storage
        val storageInfo = getStorageInfo()
        info["Internal Storage"] = storageInfo.first
        info["Available Storage"] = storageInfo.second
        
        // App info
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            info["App Version"] = pkgInfo.versionName ?: "unknown"
            info["App Version Code"] = if (android.os.Build.VERSION.SDK_INT >= 28) pkgInfo.longVersionCode.toString() else pkgInfo.versionCode.toString()
            info["Package"] = context.packageName
        } catch (e: Exception) {
            info["App Version"] = "unknown"
        }
        info["Target SDK"] = if (android.os.Build.VERSION.SDK_INT >= 24) context.applicationInfo.targetSdkVersion.toString() else "unknown"
        info["Min SDK"] = context.applicationInfo.minSdkVersion.toString()
        
        // Native libs
        info["Native Libs"] = getNativeLibStatus(context)
        
        return info
    }
    
    private fun getMemoryInfo(): Pair<String, String> {
        return try {
            val runtime = Runtime.getRuntime()
            val total = runtime.totalMemory()
            val free = runtime.freeMemory()
            val max = runtime.maxMemory()
            Pair(
                "${(max / 1024 / 1024)}MB max / ${(total / 1024 / 1024)}MB allocated",
                "${(free / 1024 / 1024)}MB free / ${((total - free) / 1024 / 1024)}MB used"
            )
        } catch (e: Exception) {
            Pair("unknown", "unknown")
        }
    }
    
    private fun getStorageInfo(): Pair<String, String> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            Pair(
                "${(total / 1024 / 1024 / 1024)}GB",
                "${(free / 1024 / 1024 / 1024)}GB free / ${((total - free) / 1024 / 1024 / 1024)}GB used"
            )
        } catch (e: Exception) {
            Pair("unknown", "unknown")
        }
    }
    
    private fun getNativeLibStatus(context: Context): String {
        return try {
            val libDir = File(context.applicationInfo.nativeLibraryDir)
            if (libDir.exists()) {
                val libs = libDir.listFiles()?.map { it.name } ?: emptyList()
                if (libs.isEmpty()) "No native libs" else libs.joinToString(", ")
            } else {
                "No native lib dir"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    fun getDeviceInfoText(): String {
        return collect(com.flightwatch.app.FlightWatchApp()).entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }
}
