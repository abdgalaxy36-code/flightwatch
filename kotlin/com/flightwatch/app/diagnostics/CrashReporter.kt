package com.flightwatch.app.diagnostics

import android.content.Context
import android.util.Log
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private var crashDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private var lastEvents = mutableListOf<String>()
    private const val MAX_EVENTS = 50
    
    fun init(context: Context) {
        crashDir = File(context.filesDir, "crashes").apply { mkdirs() }
        
        // Set uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(thread, throwable, defaultHandler)
            } catch (e: Exception) {
                // Never crash from crash handler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        AppLogger.i("CrashReporter initialized", "crash")
    }
    
    private fun saveCrash(thread: Thread, throwable: Throwable, defaultHandler: Thread.UncaughtExceptionHandler?) {
        try {
            val ts = dateFormat.format(Date())
            val crashFile = File(crashDir ?: return, "crash_$ts.txt")
            
            val sb = StringBuilder()
            sb.appendLine("=== CRASH REPORT ===")
            sb.appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            sb.appendLine("Thread: ${thread.name} (id=${thread.id})")
            sb.appendLine("Exception: ${throwable.javaClass.name}")
            sb.appendLine("Message: ${throwable.message}")
            sb.appendLine()
            sb.appendLine("=== STACK TRACE ===")
            sb.appendLine(Log.getStackTraceString(throwable))
            sb.appendLine()
            sb.appendLine("=== DEVICE INFO ===")
            sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
            sb.appendLine("Model: ${Build.MODEL}")
            sb.appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            sb.appendLine("Build: ${Build.DISPLAY}")
            sb.appendLine()
            sb.appendLine("=== LAST EVENTS ===")
            synchronized(lastEvents) {
                lastEvents.forEach { sb.appendLine(it) }
            }
            
            crashFile.writeText(sb.toString())
            AppLogger.e("Crash saved: ${crashFile.name}", throwable, "crash")
        } catch (e: Exception) {
            // Never crash from crash handler
        }
    }
    
    fun logEvent(event: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        synchronized(lastEvents) {
            lastEvents.add("$ts: $event")
            if (lastEvents.size > MAX_EVENTS) lastEvents.removeAt(0)
        }
    }
    
    fun getCrashFiles(): List<File> {
        return crashDir?.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    fun readCrash(file: File): String = try { file.readText() } catch (_: Exception) { "" }
    
    fun clearCrashes() {
        crashDir?.listFiles()?.forEach { it.delete() }
    }
}
