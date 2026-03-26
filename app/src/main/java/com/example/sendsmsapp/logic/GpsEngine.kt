package com.example.sendsmsapp.logic

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.example.sendsmsapp.model.Device
import com.example.sendsmsapp.sms.SmsBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class GpsEngine(private val ctx: Context) {


    private val PACE_MS = 2000L

    private val EXPECTED_IP = "41.226.24.13"
    private val EXPECTED_PORT = 81
    private val EXPECTED_PROTOCOL = "3,1"
    private val EXPECTED_HC_TEXT = "HC,60,7200,7200"
    private val EXPECTED_CORNER = 20

    /** Time check tolerance (seconds) */
    private val MAX_TIME_DRIFT_SEC = 300L // 5 minutes


    private val DIAG_TRIGGER_RX =
        Regex("""\b(?:ID|IMEI)\s*[:=]\s*\d{8,20}\b""", RegexOption.IGNORE_CASE)
    private val IMEI_FROM_MSG_RX =
        Regex("""\b(?:ID|IMEI)\s*[:=]\s*(\d{8,20})\b""", RegexOption.IGNORE_CASE)
    private val IP_PORT_RX = Regex("""(\d{1,3}(?:\.\d{1,3}){3}):(\d{1,5})""")
    private val PROTOCOL_RX =
        Regex("""\bPROTOCOL\s*[:=]\s*(\d+\s*,\s*\d+)\b""", RegexOption.IGNORE_CASE)
    private val STATUS_TRIGGER_RX = Regex("""\bGPS\s*:""", RegexOption.IGNORE_CASE)
    private val GPS_FIXED_RX = Regex("""\bGPS\s*:\s*fixed\b""", RegexOption.IGNORE_CASE)
    private val STATUS_TIME_RX = Regex("""(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})""")
    private val HC_TRIGGER_RX =
        Regex("""\bHC\s*,\s*\d+\s*,\s*\d+\s*,\s*\d+\b""", RegexOption.IGNORE_CASE)
    private val HC_VALUES_RX =
        Regex("""\bHC\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val CORNER_RX =
        Regex("""\bCORNER\s*[:=]\s*(\d+)\b""", RegexOption.IGNORE_CASE)


    private val OK_RX = Regex("""\b(OK|SUCCESS)\b""", RegexOption.IGNORE_CASE)
    private val ERR_RX = Regex("""\b(ERROR|CMD\s*ERROR|FAIL)\b""", RegexOption.IGNORE_CASE)

    /** Flashing report types */
    private enum class StepStatus { OK, ERROR, TIMEOUT, UNKNOWN }
    private data class StepReport(
        val idx: Int,
        val cmd: String,
        val label: String,
        val status: StepStatus,
        val reply: String?
    )

    /** Toast on main */
    private fun toast(msg: String, long: Boolean = true) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                ctx.applicationContext,
                msg,
                if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Send SMS safely */
    private fun sendSms(number: String, text: String): Boolean = try {
        val mgr: SmsManager? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
                try { SmsManager.getSmsManagerForSubscriptionId(subId) }
                catch (_: Throwable) { ctx.getSystemService(SmsManager::class.java) }
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

        if (mgr == null) {
            toast("No SmsManager available on this device"); false
        } else { mgr.sendTextMessage(number, null, text, null, null); true }
    } catch (_: SecurityException) {
        toast("SMS permission denied"); false
    } catch (t: Throwable) {
        toast("Failed to send SMS: ${t.message}"); false
    }

    private fun digits(s: String?) = s?.filter(Char::isDigit).orEmpty()
    private fun sameSender(a: String?, b: String?): Boolean {
        val da = digits(a); val db = digits(b)
        if (da.isEmpty() || db.isEmpty()) return false
        if (da.endsWith(db) || db.endsWith(da)) return true
        val n = minOf(8, da.length, db.length)
        return da.takeLast(n) == db.takeLast(n)
    }

    /** Wait for matching reply from same sender or null on timeout */
    private suspend fun waitFor(from: String, rx: Regex, timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) {
            SmsBus.messages
                .filter { sameSender(it.first, from) && rx.containsMatchIn(it.second) }
                .first()
                .second
        }

    // ------------------------------------------------------------
    // DIAGNOSTIC (unchanged behavior; hides raw replies from UI)
    // ------------------------------------------------------------

    suspend fun runDiagnostic(dev: Device, log: (String) -> Unit): DiagnosticResult {
        fun l(s: String) = log(s)

        l("Target: ${dev.phone} (IMEI=${dev.imei})")

        // Step 1: *11*4#
        val cmd = "*11*4#"
        if (!sendSms(dev.phone, cmd)) {
            l("Cannot send diagnostic command.")
            return DiagnosticResult.FIX_SENT_TIMEOUT
        }
        l("Sent: $cmd")

        val reply = waitFor(dev.phone, DIAG_TRIGGER_RX, 60_000)
        if (reply == null) {
            l("No reply within 60s → sending IMEI fix")
            val fix = "*77*6*${dev.imei}#"
            sendSms(dev.phone, fix)
            l("Sent: $fix")
            delay(PACE_MS)
            return DiagnosticResult.FIX_SENT_TIMEOUT
        }

        // 1a) IMEI check
        val imeiFromMsg = IMEI_FROM_MSG_RX.find(reply)?.groupValues?.getOrNull(1)
        val diagResult = when {
            imeiFromMsg == null -> {
                val fix = "*77*6*${dev.imei}#"
                sendSms(dev.phone, fix)
                l("IMEI missing → Sent: $fix")
                delay(PACE_MS); DiagnosticResult.FIX_SENT_MISSING
            }
            imeiFromMsg != dev.imei -> {
                val fix = "*77*6*${dev.imei}#"
                sendSms(dev.phone, fix)
                l("IMEI mismatch ($imeiFromMsg) → Sent: $fix")
                delay(PACE_MS); DiagnosticResult.FIX_SENT_MISMATCH
            }
            else -> { l("IMEI OK ✅ ($imeiFromMsg)"); DiagnosticResult.OK }
        }

        // 1b) IP/Port check
        IP_PORT_RX.find(reply)?.let { m ->
            val ip = m.groupValues[1]
            val port = m.groupValues[2].toIntOrNull()
            if (port != null && (ip != EXPECTED_IP || port != EXPECTED_PORT)) {
                val fix = "IP,$EXPECTED_IP,$EXPECTED_PORT,1#"
                sendSms(dev.phone, fix)
                l("Server mismatch (ip=$ip, port=$port) → Sent: $fix")
                delay(PACE_MS)
            } else if (port != null) {
                l("Server IP/Port OK ✅ ($ip:$port)")
            }
        } ?: l("Server IP/Port not found in reply")

        // Step 2: GPRSSET# (PROTOCOL)
        val gprsCmd = "GPRSSET#"
        sendSms(dev.phone, gprsCmd); l("Sent: $gprsCmd")
        val gprsReply = waitFor(dev.phone, PROTOCOL_RX, 60_000)
        if (gprsReply == null) {
            l("No GPRSSET reply within 60s")
        } else {
            val proto = PROTOCOL_RX.find(gprsReply)?.groupValues?.getOrNull(1)
                ?.replace("\\s+".toRegex(), "")
            if (proto == null) {
                l("Protocol not found in reply")
            } else if (proto != EXPECTED_PROTOCOL) {
                val fix = "PROTOCOL,$EXPECTED_PROTOCOL#"
                sendSms(dev.phone, fix)
                l("Protocol mismatch ($proto) → Sent: $fix")
                delay(PACE_MS)
            } else l("Protocol OK ✅ ($proto)")
        }

        // Step 3: STATUS# (GPS fixed? & time check)
        val statusCmd = "STATUS#"
        sendSms(dev.phone, statusCmd); l("Sent: $statusCmd")
        val statusReply = waitFor(dev.phone, STATUS_TRIGGER_RX, 60_000)
        var needReset = false
        if (statusReply == null) {
            l("No STATUS reply within 60s")
        } else {
            val fixed = GPS_FIXED_RX.containsMatchIn(statusReply)
            if (!fixed) needReset = true

            val timeStr = STATUS_TIME_RX.find(statusReply)?.groupValues?.getOrNull(1)
            if (timeStr != null) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                        timeZone = TimeZone.getDefault()
                    }
                    val gpsMillis = sdf.parse(timeStr)?.time
                    if (gpsMillis != null) {
                        val drift = abs(System.currentTimeMillis() - gpsMillis) / 1000
                        val timeOk = drift <= MAX_TIME_DRIFT_SEC
                        if (timeOk) {
                            l("Time/date correct.")
                        } else {
                            needReset = true
                            l("Time/date NOT correct (drift ${drift}s)")
                        }
                    } else {
                        l("Could not parse GPS time '$timeStr'")
                    }
                } catch (t: Throwable) {
                    l("Could not parse GPS time '$timeStr': ${t.message}")
                }
            } else {
                l("No timestamp found in STATUS reply; skipping time check.")
            }

            if (needReset) {
                val resetCmd = "RESET#"
                sendSms(dev.phone, resetCmd)
                l("RESET# (reset is done; check GPS after)")
                delay(PACE_MS)
            } else {
                l("STATUS OK ✅")
            }
        }

        // Step 4: HC# (frequency)
        val hcCmd = "HC#"
        sendSms(dev.phone, hcCmd); l("Sent: $hcCmd")
        val hcReply = waitFor(dev.phone, HC_TRIGGER_RX, 60_000)
        if (hcReply == null) {
            l("No HC reply within 60s")
        } else {
            val m = HC_VALUES_RX.find(hcReply)
            val normalized =
                if (m != null) "HC,${m.groupValues[1]},${m.groupValues[2]},${m.groupValues[3]}" else null
            if (normalized == null) {
                l("HC values not found in reply")
            } else if (!normalized.equals(EXPECTED_HC_TEXT, ignoreCase = true)) {
                val fix = "$EXPECTED_HC_TEXT#4"
                sendSms(dev.phone, fix)
                l("HC mismatch ($normalized) → Sent: $fix")
                delay(PACE_MS)
            } else {
                l("HC frequency OK ✅ ($normalized)")
                toast("Frequency: $normalized", long = false)
            }
        }

        // Step 5: CORNER# (should be 20)
        val cornerCmd = "CORNER#"
        sendSms(dev.phone, cornerCmd); l("Sent: $cornerCmd")
        val cornerReply = waitFor(dev.phone, CORNER_RX, 60_000)
        if (cornerReply == null) {
            l("No CORNER reply within 60s")
        } else {
            val v = CORNER_RX.find(cornerReply)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (v == null) {
                l("Corner value not found")
            } else if (v != EXPECTED_CORNER) {
                val fix = "CORNER,$EXPECTED_CORNER#"
                sendSms(dev.phone, fix)
                l("Corner mismatch ($v) → Sent: $fix")
                delay(PACE_MS)
            } else l("Corner OK ✅ ($v)")
        }

        delay(PACE_MS)
        l("Cool-down complete (${PACE_MS / 1000}s).")
        return DiagnosticResult.OK
    }

    // ------------------------------------------------------------
    // FLASHING (12 steps, continue even if no reply after 30s)
    // ------------------------------------------------------------

    suspend fun runFlashing(dev: Device, log: (String) -> Unit) {
        fun l(s: String) = log(s)

        val steps: List<Pair<String, String>> = listOf(
            "Protocol"           to "PROTOCOL,3,1#",
            "Server IP/Port"     to "IP,41.226.24.13,81,1#",
            "Backup IP2"         to "IP2,41.226.24.13,1200,1#",
            "Level"              to "LEVEL,5#",
            "Frequency (HC)"     to "HC,60,7200,7200#",
            "Corner"             to "CORNER,20#",
            "Time zone (ZD)"     to "ZD,0#",
            "WY"                 to "WY,1,500,0#",
            "UTC"                to "UTC,0#",
            "VACC"               to "VACC,0#",
            "SLEEP"              to "SLEEP,0#",
            "Alarm Speed"        to "ALARMSPEED,ON,120#"
        )

        l("== Flashing started ==")

        val reports = mutableListOf<StepReport>()

        for ((i, pair) in steps.withIndex()) {
            val label = pair.first
            val cmd   = pair.second

            if (!sendSms(dev.phone, cmd)) {
                l("Cannot send '$label' ($cmd). Skipping to next.")
                reports += StepReport(i + 1, cmd, label, StepStatus.ERROR, "send failed")
                delay(PACE_MS)
                continue
            }
            l("Sent: $label")

            // Wait up to 30s for any "OK" or "ERROR"-like keyword
            val reply = waitFor(
                dev.phone,
                Regex("(OK|ERROR|CMD\\s*ERROR|SUCCESS|FAIL)", RegexOption.IGNORE_CASE),
                30_000
            )

            val status = when {
                reply == null -> StepStatus.TIMEOUT
                ERR_RX.containsMatchIn(reply) -> StepStatus.ERROR
                OK_RX.containsMatchIn(reply)  -> StepStatus.OK
                else -> StepStatus.UNKNOWN
            }

            when (status) {
                StepStatus.OK      -> l("$label: OK")
                StepStatus.ERROR   -> l("$label: ERROR")
                StepStatus.TIMEOUT -> l("$label: No reply within 30s → continuing")
                StepStatus.UNKNOWN -> l("$label: reply received (no OK/ERROR keyword)")
            }

            reports += StepReport(i + 1, cmd, label, status, reply?.take(120))

            delay(PACE_MS)
        }

        // === Post configuration report ===
        l("== Post configuration report ==")
        for (r in reports) {
            val icon = when (r.status) {
                StepStatus.OK      -> "&"
                StepStatus.ERROR   -> "X"
                StepStatus.TIMEOUT -> ""
                StepStatus.UNKNOWN -> "?"
            }
            val extra = r.reply?.let { " – \"$it\"" } ?: ""
            l("${r.idx}) ${r.label} — $icon ${r.status}$extra")
        }

        val anyIssue = reports.any { it.status != StepStatus.OK }
        if (anyIssue) {
            toast("Flashing finished with issues (timeouts or errors). Check report.")
        } else {
            toast("Flashing SUCCESS — all steps OK ✅", long = false)
        }

        l("== Flashing finished ==")
    }
}
