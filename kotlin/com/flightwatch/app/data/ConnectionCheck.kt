package com.flightwatch.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Checks that the phone can reach the places the app needs.
 *
 * The app talks to two hosts: LetsFG for the prices and OctoTrip for the
 * booking links. When something does not come back, this says which of the
 * two is the problem instead of leaving the traveller with a blank screen.
 */
object ConnectionCheck {

    data class Result(
        val dnsOk: Boolean,
        val dnsNote: String,
        val httpsOk: Boolean,
        val httpsNote: String,
    ) {
        val ok: Boolean get() = dnsOk && httpsOk
    }

    suspend fun run(): Result = withContext(Dispatchers.IO) {
        val hosts = listOf("letsfg.co", "mcp.octotrip.app")
        val resolved = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (host in hosts) {
            val address = try {
                InetAddress.getAllByName(host).first().hostAddress
            } catch (e: Exception) {
                null
            }
            if (address == null) failed += host else resolved += "$host ($address)"
        }
        val dnsNote = when {
            failed.isEmpty() -> resolved.joinToString(", ")
            resolved.isEmpty() -> "no answer for ${failed.joinToString(", ")}"
            else -> "${resolved.joinToString(", ")}; no answer for ${failed.joinToString(", ")}"
        }

        val note = try {
            var conn: HttpURLConnection? = null
            try {
                conn = URL("https://letsfg.co/api/locations").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "FlightWatch/1.0")
                val code = conn.responseCode
                "letsfg.co answered HTTP $code"
            } finally {
                conn?.disconnect()
            }
        } catch (e: Exception) {
            "letsfg.co could not be reached: ${e.message}"
        }

        Result(
            dnsOk = failed.isEmpty(),
            dnsNote = dnsNote,
            httpsOk = note.startsWith("letsfg.co answered"),
            httpsNote = note,
        )
    }
}
