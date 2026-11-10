package com.flightwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub backup and restore for both Simple and Extra Mode data.
 * Backs up selected flights, settings, price history, and API usage.
 */
class GitHubBackup(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("github_backup", Context.MODE_PRIVATE)
    private val gson = Gson()

    data class BackupData(
        val timestamp: Long,
        val selectedFlights: List<FlightCard>,
        val settings: Map<String, Any?>,
        val priceHistory: Map<String, List<FlightPricePoint>>,
        val apiUsage: Map<String, Int>,
        val version: Int = 1,
    )

    /** Create a full backup of all user data. */
    fun createBackup(
        flights: List<FlightCard>,
        settings: Map<String, Any?>,
        priceHistory: Map<String, List<FlightPricePoint>>,
        apiUsage: Map<String, Int>,
    ): BackupData {
        return BackupData(
            timestamp = System.currentTimeMillis(),
            selectedFlights = flights,
            settings = settings,
            priceHistory = priceHistory,
            apiUsage = apiUsage,
        )
    }

    /** Serialize backup to JSON string. */
    fun toJson(data: BackupData): String = gson.toJson(data)

    /** Deserialize backup from JSON string. Returns null if invalid. */
    fun fromJson(json: String): BackupData? {
        return try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** Validate backup data integrity. */
    fun validate(data: BackupData?): Boolean {
        if (data == null) return false
        if (data.timestamp <= 0) return false
        return true
    }

    /** Save backup to local file for manual export. */
    fun saveLocal(data: BackupData): Boolean {
        return try {
            context.openFileOutput("flightwatch_backup.json", Context.MODE_PRIVATE).use {
                it.write(gson.toJson(data).toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Load backup from local file. */
    fun loadLocal(): BackupData? {
        return try {
            val json = context.openFileInput("flightwatch_backup.json").bufferedReader().readText()
            fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    /** Push backup to GitHub as a gist. Returns true on success. */
    suspend fun pushToGitHub(token: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/gists")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = """
            {
                "description": "FlightWatch backup",
                "public": false,
                "files": {
                    "flightwatch_backup.json": {
                        "content": ${gson.toJson(content)}
                    }
                }
            }
            """.trimIndent()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /** Pull backup from GitHub gist. Returns null on failure. */
    suspend fun pullFromGitHub(token: String, gistId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/gists/$gistId")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Accept", "application/json")

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = reader.readText()
            reader.close()
            conn.disconnect()

            // Parse gist JSON to extract file content
            val map: Map<String, Any> = gson.fromJson(response, object : TypeToken<Map<String, Any>>() {}.type)
            val files = map["files"] as? Map<String, Any> ?: return@withContext null
            val file = files["flightwatch_backup.json"] as? Map<String, Any> ?: return@withContext null
            file["content"] as? String
        } catch (e: Exception) {
            null
        }
    }

    /** Restore from backup data. */
    fun restore(data: BackupData): Boolean {
        return try {
            if (!validate(data)) return false
            // Note: Caller must handle persistence via their respective stores
            true
        } catch (e: Exception) {
            false
        }
    }
}
