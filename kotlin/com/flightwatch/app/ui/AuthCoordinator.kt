package com.flightwatch.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.flightwatch.app.FlightWatchApp
import com.flightwatch.app.data.LetsfgApi
import com.flightwatch.app.data.LetsfgException
import com.flightwatch.app.data.LoopbackCallbackServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/**
 * Drives the LetsFG connect flow inside the app.
 *
 * The app registers its own OAuth client, sends the traveller to
 * letsfg.co/connect for a one-tap approval, and catches the redirect on a
 * loopback port (the same mechanism the official CLI uses). The
 * `flightwatch://oauth` scheme is registered too, in case the browser picks
 * that route instead.
 */
object AuthCoordinator {

    private var server: LoopbackCallbackServer? = null
    private var pending: LetsfgApi.OAuthStart? = null
    private var deferred: CompletableDeferred<Pair<String, String>>? = null

    suspend fun begin(context: Context, vm: MainViewModel) {
        android.util.Log.i("FlightWatch", "AuthCoordinator.begin() called")
        val app = context.applicationContext as FlightWatchApp
        vm.setAuthState(AuthState.Working("Preparing connection…"))

        try {
            server?.stop()
            val srv = LoopbackCallbackServer()
            server = srv
            val d = CompletableDeferred<Pair<String, String>>()
            deferred = d
            srv.start { code, state ->
                if (d.complete(code to state)) Unit
            }

            val redirectUri = "http://127.0.0.1:${srv.port}/callback"
            android.util.Log.i("FlightWatch", "Auth: redirectUri=$redirectUri")
            val start = app.api.beginAuth(redirectUri, listOf(DEEP_LINK_URI))
            android.util.Log.i("FlightWatch", "Auth: authorizeUrl=${start.authorizeUrl}")
            pending = start

            vm.setAuthState(AuthState.Working("Waiting for approval in your browser…"))
            android.util.Log.i("FlightWatch", "Auth: opening browser")
            openBrowser(context, start.authorizeUrl)
            android.util.Log.i("FlightWatch", "Auth: browser opened")

            val (code, state) = withTimeoutOrNull(300_000L) { d.await() } ?: throw LetsfgException("Connection timed out - please try again")
            if (state != start.state) {
                throw LetsfgException("State mismatch - connection aborted")
            }
            vm.setAuthState(AuthState.Working("Finishing connection…"))
            app.api.exchangeCode(start.clientId, start.redirectUri, code, start.verifier)
            vm.refreshConnectionState()
            vm.setAuthState(AuthState.Connected("Connected. Searching flights…"))
            vm.search(force = true)
        } catch (e: Exception) {
            android.util.Log.e("FlightWatch", "Auth failed: ${e.message}", e)
            vm.setAuthState(AuthState.Failed(e.message ?: "Connection failed"))
        } finally {
            server?.stop()
            server = null
            deferred = null
            pending = null
        }
    }

    /** Called when the browser redirects to `flightwatch://oauth`. */
    fun onRedirect(context: Context, uri: Uri) {
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state") ?: ""
        val error = uri.getQueryParameter("error")
        val d = deferred
        if (error != null) {
            d?.completeExceptionally(LetsfgException("Authorisation failed: $error"))
            return
        }
        if (code != null) {
            d?.complete(code to state)
        }
    }

    private fun openBrowser(context: Context, url: String) {
        android.util.Log.i("FlightWatch", "openBrowser: $url")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("FlightWatch", "openBrowser failed: ${e.message}", e)
        }
    }

    private const val DEEP_LINK_URI = "flightwatch://oauth"
}
