package com.flightwatch.app.ui.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader

/**
 * Log reader - reads all app logs in one go.
 * Shows logs from logcat for debugging.
 */
@Composable
fun LogReader(modifier: Modifier = Modifier) {
    var logs by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        logs = readLogs()
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            "App Logs",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            logs.ifEmpty { "No logs available" },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private fun readLogs(): String {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-s", "FlightWatch", "-t", "200"))
        val reader = BufferedReader(process.inputStream.reader())
        val logs = reader.readText()
        reader.close()
        process.waitFor()
        logs
    } catch (e: Exception) {
        "Error reading logs: ${e.message}"
    }
}
