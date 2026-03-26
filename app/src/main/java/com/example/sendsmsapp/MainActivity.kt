package com.example.sendsmsapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.sendsmsapp.logic.DiagnosticResult
import com.example.sendsmsapp.logic.GpsEngine
import com.example.sendsmsapp.model.Device
import com.example.sendsmsapp.ui.theme.SendSmsAppTheme
import com.example.sendsmsapp.util.Csv
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var engine: GpsEngine
    private var devices: List<Device> = emptyList()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted.values.all { it }
        Toast.makeText(
            this,
            if (ok) "Permissions granted" else "SMS permissions required",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load data & engine once
        ensurePermissions()
        devices = Csv.loadDevices(this, R.raw.data)
        engine = GpsEngine(this)

        setContent {
            SendSmsAppTheme {
                // --- UI state ---
                var log by remember { mutableStateOf("") }
                val ctx = LocalContext.current
                val scrollState = rememberScrollState()

                fun append(line: String) {
                    // Hide raw SMS bodies; keep concise status lines
                    val t = line.trim()
                    val isRawReply =
                        t.startsWith("Reply:", ignoreCase = true) ||
                                t.startsWith("STATUS Reply:", ignoreCase = true) ||
                                t.startsWith("GPRSSET Reply:", ignoreCase = true) ||
                                t.startsWith("HC Reply:", ignoreCase = true) ||
                                t.startsWith("CORNER Reply:", ignoreCase = true)
                    if (isRawReply) return

                    // Prepend newest at the top
                    log = if (log.isBlank()) line else "$line\n$log"
                }

                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // Title
                        Text(
                            text = "Devices loaded: ${devices.size}",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(16.dp))

                        // --- Buttons row ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!hasSmsPerms()) {
                                        Toast.makeText(ctx, "Grant SMS permissions first", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    if (devices.isEmpty()) {
                                        Toast.makeText(ctx, "No device in CSV", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    lifecycleScope.launch {
                                        try {
                                            append("== Diagnostic started ==")
                                            val result = engine.runDiagnostic(devices.first()) { append(it) }
                                            when (result) {
                                                DiagnosticResult.OK ->
                                                    Toast.makeText(ctx, "IMEI correct ✅", Toast.LENGTH_LONG).show()
                                                DiagnosticResult.FIX_SENT_MISMATCH ->
                                                    Toast.makeText(ctx, "IMEI mismatch → fix command sent", Toast.LENGTH_LONG).show()
                                                DiagnosticResult.FIX_SENT_MISSING ->
                                                    Toast.makeText(ctx, "IMEI missing → fix command sent", Toast.LENGTH_LONG).show()
                                                DiagnosticResult.FIX_SENT_TIMEOUT ->
                                                    Toast.makeText(ctx, "No reply → fix command sent", Toast.LENGTH_LONG).show()
                                            }
                                            append("== Diagnostic finished ==")
                                        } catch (t: Throwable) {
                                            // absolutely swallow any crash so the app never force-closes
                                            append("ERROR: ${t.message ?: t::class.java.simpleName}")
                                            Toast.makeText(ctx, "Error: ${t.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Diagnostic") }

                            Button(
                                onClick = {
                                    if (!hasSmsPerms()) {
                                        Toast.makeText(ctx, "Grant SMS permissions first", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    if (devices.isEmpty()) {
                                        Toast.makeText(ctx, "No device in CSV", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    lifecycleScope.launch {
                                        try {
                                            append("== Flashing started ==")
                                            engine.runFlashing(devices.first()) { append(it) }
                                            append("== Flashing finished ==")
                                        } catch (t: Throwable) {
                                            append("ERROR: ${t.message ?: t::class.java.simpleName}")
                                            Toast.makeText(ctx, "Error: ${t.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Flashing") }

                            Button(
                                onClick = { log = "" },
                                modifier = Modifier.weight(1f)
                            ) { Text("Clear") }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text("Log:", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))

                        // Scrollable plain text log
                        Text(
                            text = log.ifBlank { "No activity yet." },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(scrollState)
                        )
                    }
                }
            }
        }
    }

    // ---- Permissions helpers ----
    private fun hasSmsPerms(): Boolean {
        val perms = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun ensurePermissions() {
        if (!hasSmsPerms()) {
            requestPerms.launch(
                arrayOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )
            )
        }
    }
}
