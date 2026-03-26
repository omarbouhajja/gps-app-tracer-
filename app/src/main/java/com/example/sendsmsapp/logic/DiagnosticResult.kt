package com.example.sendsmsapp.logic

enum class DiagnosticResult {
    OK,                // IMEI matches
    FIX_SENT_MISSING,  // No IMEI in reply → fix sent
    FIX_SENT_MISMATCH, // IMEI different → fix sent
    FIX_SENT_TIMEOUT   // No reply → fix sent
}
