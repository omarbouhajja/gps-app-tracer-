package com.example.sendsmsapp.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (msg in messages) {
                val from = msg.originatingAddress.orEmpty()
                val body = msg.messageBody.orEmpty()
                SmsBus.emit(from, body)
            }
        } catch (_: Throwable) { /* never crash the process */ }
    }
}

