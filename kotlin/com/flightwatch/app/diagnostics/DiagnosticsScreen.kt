package com.flightwatch.app.diagnostics

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var deviceInfo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var crashHistory by remember { mutableStateOf("") }
    var logFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var crashFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(Unit) {
        deviceInfo = DeviceDiagnostics.collect(context)
        crashFiles = CrashReporter.getCrashFiles()
        crashHistory = crashFiles.joinToString("\n\n") { f ->
            "=== ${f.name} ===\n" + CrashReporter.readCrash(f).take(2000)
        }
    }

    // Auto-refresh logs every 1 second


    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Back + Title
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(12.dp))
            Text("Diagnostics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        // Device Info
        Section("Device Information") {
            deviceInfo.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("$k:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
                    Text(v, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // App Info
        Section("Application Information") {
            Text("Version: ${deviceInfo["App Version"] ?: "unknown"}", fontSize = 12.sp)
            Text("Build: ${deviceInfo["App Version Code"] ?: "unknown"}", fontSize = 12.sp)
            Text("Package: ${deviceInfo["Package"] ?: "unknown"}", fontSize = 12.sp)
            Text("Native Libs: ${deviceInfo["Native Libs"] ?: "unknown"}", fontSize = 12.sp)
        }



        // Crash History
        Section("Crash History (${crashFiles.size} crashes)") {
            Text(crashHistory.ifEmpty { "No crashes recorded" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Error Summary
        Section("Error Summary") {
            Text("Crash files: ${crashFiles.size}", fontSize = 12.sp)
            Text("Log files: ${logFiles.size}", fontSize = 12.sp)
            val totalSize = logFiles.sumOf { it.length() } + crashFiles.sumOf { it.length() }
            Text("Total size: ${totalSize / 1024}KB", fontSize = 12.sp)
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exportReport(context) }) { Text("Export Report", fontSize = 13.sp) }
            OutlinedButton(onClick = { 
                AppLogger.clearLogs()
                CrashReporter.clearCrashes()
                crashHistory = ""
                logFiles = emptyList()
                crashFiles = emptyList()
            }) { Text("Clear Logs", fontSize = 13.sp) }
            OutlinedButton(onClick = {
                logFiles = AppLogger.getLogFiles()
                crashFiles = CrashReporter.getCrashFiles()
            }) { Text("Refresh", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

private fun exportReport(context: Context) {
    try {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val zipFile = File(context.getExternalFilesDir(null), "flightwatch_diagnostics_$timestamp.zip")
        
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            // Device info
            val deviceInfo = DeviceDiagnostics.collect(context)
            zip.putNextEntry(ZipEntry("device_info.txt"))
            zip.write(deviceInfo.entries.joinToString("\n") { "${it.key}: ${it.value}" }.toByteArray())
            zip.closeEntry()
            
            // Logs
            zip.putNextEntry(ZipEntry("app_log.txt"))
            zip.write(AppLogger.readLogs(1000).toByteArray())
            zip.closeEntry()
            
            // Crash files
            CrashReporter.getCrashFiles().forEach { crashFile ->
                zip.putNextEntry(ZipEntry("crashes/${crashFile.name}"))
                zip.write(CrashReporter.readCrash(crashFile).toByteArray())
                zip.closeEntry()
            }
        }
        
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "application/zip"
        intent.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile))
        context.startActivity(Intent.createChooser(intent, "Export Diagnostic Report"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun readAllLogs(): String {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "300"))
        val reader = process.inputStream.bufferedReader()
        val logs = reader.readText()
        reader.close()
        process.waitFor()
        logs.lines().filter { it.isNotBlank() }.takeLast(150).joinToString("\n")
    } catch (e: Exception) {
        "Error reading logs: ${e.message}"
    }
}
