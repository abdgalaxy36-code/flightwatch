package com.flightwatch.app.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

object AppLogger {
    private const val TAG = "FW_DIAG"
    private val executor = Executors.newSingleThreadExecutor()
    private var logDir: File? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    
    // Live log buffer (last 200 entries)
    private val liveLogs = CopyOnWriteArrayList<String>()
    private const val MAX_LIVE = 200
    
    private val sensitiveKeys = listOf("password", "api_key", "apikey", "token", "secret", "credential", "authorization", "bearer")
    
    fun init(context: Context) {
        logDir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        i("AppLogger initialized", "system")
    }
    
    fun v(msg: String, cat: String = "general") = log("V", cat, msg)
    fun d(msg: String, cat: String = "general") = log("D", cat, msg)
    fun i(msg: String, cat: String = "general") = log("I", cat, msg)
    fun w(msg: String, cat: String = "general") = log("W", cat, msg)
    fun e(msg: String, t: Throwable? = null, cat: String = "general") = log("E", cat, msg, t)
    
    private fun log(level: String, cat: String, msg: String, t: Throwable? = null) {
        val safeMsg = sanitize(msg)
        val ts = dateFormat.format(Date())
        val thread = Thread.currentThread().name
        val tStr = t?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
        val line = "$ts [$level] [$cat] [$thread] $safeMsg$tStr"
        
        when (level) {
            "V" -> Log.v(TAG, line)
            "D" -> Log.d(TAG, line)
            "I" -> Log.i(TAG, line)
            "W" -> Log.w(TAG, line)
            "E" -> Log.e(TAG, line, t)
        }
        
        // Add to live buffer
        synchronized(liveLogs) {
            liveLogs.add(line)
            if (liveLogs.size > MAX_LIVE) liveLogs.removeAt(0)
        }
        
        // Async file write
        executor.execute {
            try {
                val dir = logDir ?: return@execute
                val file = File(dir, "app_log.txt")
                if (file.exists() && file.length() > 5 * 1024 * 1024) rotate(dir)
                file.appendText(line + "\n")
            } catch (_: Exception) {}
        }
    }
    
    private fun rotate(dir: File) {
        try {
            val ts = fileDateFormat.format(Date())
            File(dir, "app_log.txt").renameTo(File(dir, "app_log_$ts.txt"))
            dir.listFiles { f -> f.name.startsWith("app_log_") && f.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(3)
                ?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
    
    private fun sanitize(msg: String): String {
        var r = msg
        for (k in sensitiveKeys) {
            r = Regex("($k)[\"']?\\s*[:=]\\s*[\"']?[^\\s,\"'}]+", RegexOption.IGNORE_CASE)
                .replace(r) { "${it.groupValues[1]}=***" }
        }
        return r
    }
    
    fun getLiveLogs(): String = synchronized(liveLogs) { liveLogs.joinToString("\n") }
    fun getLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles { f -> f.name.startsWith("app_log") && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    fun readLogs(maxLines: Int = 500): String {
        val f = File(logDir ?: return "", "app_log.txt")
        return try { f.readLines().takeLast(maxLines).joinToString("\n") } catch (_: Exception) { "" }
    }
    fun clearLogs() { executor.execute { logDir?.listFiles()?.forEach { it.delete() } } }
}
