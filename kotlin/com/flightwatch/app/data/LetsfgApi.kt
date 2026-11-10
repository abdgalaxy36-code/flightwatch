package com.flightwatch.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Talks to the LetsFG PFS (free) API.
 *
 * Endpoints mirror the official letsfg Node client exactly:
 *   POST   https://letsfg.co/api/search              -> { search_id }
 *   GET    https://letsfg.co/api/results/{search_id} -> offers (pending/searching/completed)
 *   POST   https://letsfg.co/developers/api/oauth/register
 *   POST   https://letsfg.co/developers/api/oauth/token
 *   GET    https://letsfg.co/developers/api/.well-known/oauth-authorization-server
 */
class LetsfgApi(private val tokens: TokenStore, private val usage: ApiUsageStore? = null) {

    private val gson = Gson()

    // ---------------------------------------------------------------- search

    data class SearchParams(
        val origin: String,
        val destination: String,
        val dateFrom: String,
        val returnDate: String?,
        val adults: Int = 1,
        val children: Int = 0,
        val currency: String = FlightRules.CURRENCY,
        val cabin: String? = "M",
        val departureTimeFrom: String? = null,
        val departureTimeTo: String? = null,
        /** Only the airlines' own sites - no third-party sellers at all. */
        val airlineOnly: Boolean = true,
        /** End of an outbound date window (YYYY-MM-DD), for month/flexible modes. */
        val dateTo: String? = null,
        /** Start of a return date window (YYYY-MM-DD). */
        val returnFrom: String? = null,
        /** End of a return date window (YYYY-MM-DD). */
        val returnTo: String? = null,
    )

    /** Run a search and poll until the results are terminal. */
    suspend fun search(
        params: SearchParams,
        onProgress: (String) -> Unit = {},
    ): SearchResponse = withContext(Dispatchers.IO) {
        usage?.bump(ApiUsageStore.LETSFG)
        onProgress("Searching ${params.origin} → ${params.destination} on ${params.dateFrom}")
        val body = JsonObject().apply {
            addProperty("origin", params.origin.uppercase(Locale.US))
            addProperty("destination", params.destination.uppercase(Locale.US))
            addProperty("date_from", params.dateFrom)
            addProperty("adults", params.adults)
            addProperty("children", params.children)
            addProperty("currency", params.currency)
            addProperty("limit", 300)
            if (params.airlineOnly) addProperty("airline_only", true)
            if (!params.returnDate.isNullOrBlank()) addProperty("return_date", params.returnDate)
            if (!params.dateTo.isNullOrBlank()) addProperty("date_to", params.dateTo)
            if (!params.returnFrom.isNullOrBlank()) addProperty("return_from", params.returnFrom)
            if (!params.returnTo.isNullOrBlank()) addProperty("return_to", params.returnTo)
            if (!params.cabin.isNullOrBlank()) addProperty("cabin", params.cabin)
            if (!params.departureTimeFrom.isNullOrBlank()) addProperty("departure_time_from", params.departureTimeFrom)
            if (!params.departureTimeTo.isNullOrBlank()) addProperty("departure_time_to", params.departureTimeTo)
        }

        var token = bearer()
        val started = try {
            jsonPost("$BASE_URL/api/search", body.toString(), token)
        } catch (e: LetsfgException) {
            // The token can expire between the check and the call - refresh and retry once.
            if (e.status != 401) throw e
            token = refresh()
            jsonPost("$BASE_URL/api/search", body.toString(), token)
        }
        val searchId = started?.get("search_id")?.takeIf { !it.isJsonNull }?.asString
            ?: throw LetsfgException("Search did not return a search_id")

        var terminal: JsonObject? = null
        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + POLL_TIMEOUT_MS
        var poll = 0
        while (System.currentTimeMillis() < deadline) {
            poll++
            val r = jsonGet("$BASE_URL/api/results/$searchId", token)
            val status = r?.get("status")?.takeIf { !it.isJsonNull }?.asString ?: "pending"
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000
            if (status == "expired") {
                throw LetsfgException("The search expired before it finished - pulling it again")
            }
            if (status != "pending" && status != "searching") {
                terminal = r
                onProgress("Fares ready (${elapsed}s)")
                break
            }
            onProgress("Waiting for airlines to price the fares… (${elapsed}s)")
            delay(POLL_INTERVAL_MS)
        }
        if (terminal == null) throw LetsfgException("Search timed out after ${POLL_TIMEOUT_MS / 1000}s")

        // Late split-ticket merge: the server may still be combining legs.
        val lateDeadline = System.currentTimeMillis() + LATE_MERGE_GRACE_MS
        while (System.currentTimeMillis() < lateDeadline && isStillMerging(terminal)) {
            onProgress("Combining split tickets…")
            delay(LATE_MERGE_POLL_MS)
            val merged = jsonGet("$BASE_URL/api/results/$searchId", token)
            if (merged != null) {
                val st = merged.get("status")?.asString ?: "pending"
                if (st != "pending" && st != "searching") terminal = merged
            }
        }

        val json = terminal!!
        val response = gson.fromJson(json, SearchResponse::class.java)
        if (response.status == "failed" || response.error != null) {
            throw LetsfgException(response.error ?: "Search failed")
        }
        response
    }

    private fun isStillMerging(json: JsonObject?): Boolean {
        if (json == null) return false
        val split = json.get("split_ticket_pending")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val gf = json.get("gf_enrich_pending")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        return split || gf
    }

    // ------------------------------------------------------------------ auth

    data class OAuthStart(
        val clientId: String,
        val redirectUri: String,
        val verifier: String,
        val challenge: String,
        val state: String,
        val authorizeUrl: String,
    )

    /**
     * Register a client and build the URL the traveller has to approve.
     * A loopback redirect is registered (the proven path) and the app also
     * listens on the `flightwatch://oauth` scheme in case the browser prefers it.
     */
    suspend fun beginAuth(redirectUri: String, extraRedirectUris: List<String> = emptyList()): OAuthStart =
        withContext(Dispatchers.IO) {
            val meta = discover()
            val verifier = randomBase64Url(32)
            val challenge = sha256Base64Url(verifier)
            val state = randomBase64Url(16)

            val regBody = JsonObject().apply {
                addProperty("client_name", "FlightWatch Android")
                add("redirect_uris", gson.toJsonTree(listOf(redirectUri) + extraRedirectUris))
                add("grant_types", gson.toJsonTree(listOf("authorization_code", "refresh_token")))
                add("response_types", gson.toJsonTree(listOf("code")))
                addProperty("token_endpoint_auth_method", "none")
            }
            val reg = httpJson("POST", meta.registration, regBody.toString(), null)
            val clientId = reg?.get("client_id")?.asString
                ?: throw LetsfgException("Could not register with LetsFG (HTTP ${reg?.get("error")})")
            val effectiveRedirect = reg.get("redirect_uris")?.asJsonArray?.firstOrNull()?.asString ?: redirectUri

            val url = buildString {
                append(meta.authorization)
                append("?response_type=code")
                append("&client_id=").append(enc(clientId))
                append("&redirect_uri=").append(enc(effectiveRedirect))
                append("&code_challenge=").append(enc(challenge))
                append("&code_challenge_method=S256")
                append("&state=").append(enc(state))
                append("&scope=flights:search")
            }
            OAuthStart(clientId, effectiveRedirect, verifier, challenge, state, url)
        }

    suspend fun exchangeCode(clientId: String, redirectUri: String, code: String, verifier: String): Unit =
        withContext(Dispatchers.IO) {
            val form = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "code_verifier" to verifier,
                "access_token_ttl" to ACCESS_TOKEN_TTL,
            )
            val data = postForm("$DEV_ROOT/oauth/token", form)
                ?: throw LetsfgException("Token exchange failed")
            val token = data.get("access_token")?.asString
                ?: throw LetsfgException("Token exchange failed: ${data.get("error_description")}")
            tokens.accessToken = token
            tokens.refreshToken = data.get("refresh_token")?.takeIf { !it.isJsonNull }?.asString ?: tokens.refreshToken
            tokens.clientId = data.get("client_id")?.takeIf { !it.isJsonNull }?.asString ?: clientId
            tokens.expiresAt = System.currentTimeMillis() +
                (data.get("expires_in")?.takeIf { !it.isJsonNull }?.asLong ?: 3600L) * 1000L
        }

    /** Refresh the access token using the stored refresh token. */
    suspend fun refresh(): String = withContext(Dispatchers.IO) {
        val rt = tokens.refreshToken
            ?: throw LetsfgException("No refresh token - reconnect your account")
        val cid = tokens.clientId
            ?: throw LetsfgException("No client id - reconnect your account")
        val data = postForm(
            "$DEV_ROOT/oauth/token",
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to rt,
                "client_id" to cid,
                "access_token_ttl" to ACCESS_TOKEN_TTL,
            ),
        ) ?: throw LetsfgException("Token refresh failed")
        val token = data.get("access_token")?.asString
            ?: throw LetsfgException("Token refresh failed: ${data.get("error_description")}")
        tokens.accessToken = token
        tokens.refreshToken = data.get("refresh_token")?.takeIf { !it.isJsonNull }?.asString ?: rt
        tokens.clientId = data.get("client_id")?.takeIf { !it.isJsonNull }?.asString ?: cid
        tokens.expiresAt = System.currentTimeMillis() +
            (data.get("expires_in")?.takeIf { !it.isJsonNull }?.asLong ?: 3600L) * 1000L
        token
    }

    /** A usable bearer token, refreshing it when needed. */
    suspend fun bearer(): String {
        tokens.currentToken()?.let { return it }
        if (!tokens.refreshToken.isNullOrBlank()) return refresh()
        throw LetsfgException("Not connected - tap Connect in Settings")
    }

    private data class OAuthMeta(val authorization: String, val token: String, val registration: String)

    private fun discover(): OAuthMeta {
        val fallback = OAuthMeta(
            authorization = "$BASE_URL/connect",
            token = "$DEV_ROOT/oauth/token",
            registration = "$DEV_ROOT/oauth/register",
        )
        return try {
            val r = httpJson("GET", "$DEV_ROOT/.well-known/oauth-authorization-server", null, null)
            if (r == null) fallback
            else OAuthMeta(
                authorization = r.get("authorization_endpoint")?.takeIf { !it.isJsonNull }?.asString ?: fallback.authorization,
                token = r.get("token_endpoint")?.takeIf { !it.isJsonNull }?.asString ?: fallback.token,
                registration = r.get("registration_endpoint")?.takeIf { !it.isJsonNull }?.asString ?: fallback.registration,
            )
        } catch (e: Exception) {
            fallback
        }
    }

    // --------------------------------------------------------------- plumbing

    private fun jsonPost(path: String, body: String, token: String): JsonObject? =
        httpJson("POST", path, body, token)

    private fun jsonGet(path: String, token: String? = null): JsonObject? =
        httpJson("GET", path, null, token)

    private fun httpJson(method: String, url: String, body: String?, token: String?): JsonObject? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code in 200..299) {
                return if (text.isBlank()) JsonObject() else gson.fromJson(text, JsonObject::class.java)
            }
            throw LetsfgException("HTTP $code: ${text.take(300)}", code)
        } catch (e: LetsfgException) {
            throw e
        } catch (e: Exception) {
            throw LetsfgException(e.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }

    private fun postForm(url: String, form: Map<String, String>): JsonObject? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("X-Client-Type", "js-sdk")
            val encoded = form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
            conn.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code in 200..299) {
                return if (text.isBlank()) JsonObject() else gson.fromJson(text, JsonObject::class.java)
            }
            throw LetsfgException("HTTP $code: ${text.take(300)}", code)
        } catch (e: LetsfgException) {
            throw e
        } catch (e: Exception) {
            throw LetsfgException(e.message ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "https://letsfg.co"
        const val DEV_ROOT = "$BASE_URL/developers/api"
        private const val POLL_INTERVAL_MS = 3_000L
        private const val POLL_TIMEOUT_MS = 600_000L
        private const val LATE_MERGE_POLL_MS = 3_000L
        private const val LATE_MERGE_GRACE_MS = 90_000L
        private const val ACCESS_TOKEN_TTL = "7776000"

        private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

        private fun randomBase64Url(bytes: Int): String {
            val buf = ByteArray(bytes)
            SecureRandom().nextBytes(buf)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
        }

        private fun sha256Base64Url(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }
    }
}

/** A tiny loopback HTTP server that catches the OAuth redirect on 127.0.0.1. */
class LoopbackCallbackServer {

    private val result = AtomicReference<Pair<String, String>?>(null)
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null

    val port: Int get() = serverSocket?.localPort ?: -1

    fun start(onCode: (code: String, state: String) -> Unit) {
        val socket = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        thread = Thread {
            try {
                while (true) {
                    val client = socket.accept()
                    client.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        val requestLine = reader.readLine() ?: return@use
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val target = requestLine.split(" ").getOrNull(1) ?: ""
                        val query = target.substringAfter('?', "")
                        val params = parseQuery(query)
                        val code = params["code"]
                        val state = params["state"] ?: ""
                        val error = params["error"]
                        val ok = code != null && error == null
                        val page = if (ok) {
                            "<html><body style='font-family:sans-serif;padding:40px'><h2>Connected</h2>" +
                                "<p>You can close this tab and return to FlightWatch.</p></body></html>"
                        } else {
                            "<html><body style='font-family:sans-serif;padding:40px'><h2>Not connected</h2>" +
                                "<p>${error ?: "No code returned"}. Nothing was charged.</p></body></html>"
                        }
                        s.getOutputStream().use { out ->
                            out.write("HTTP/1.1 ${if (ok) 200 else 400} OK\r\n".toByteArray())
                            out.write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            out.write("Content-Length: ${page.toByteArray().size}\r\n".toByteArray())
                            out.write("Connection: close\r\n\r\n".toByteArray())
                            out.write(page.toByteArray())
                        }
                        if (code != null) {
                            result.set(code to state)
                            onCode(code, state)
                        }
                    }
                }
            } catch (e: Exception) {
                // socket closed - expected on stop()
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }.toMap()
    }
}

class LetsfgException(message: String, val status: Int = 0) : Exception(message)
