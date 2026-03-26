package com.example.sendsmsapp.sms

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Simple in-process bus: all inbound SMS are emitted here.
 * (SmsReceiver pushes, engine listens)
 */
object SmsBus {
    private val _msgs = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32)
    val messages: SharedFlow<Pair<String, String>> = _msgs

    fun emit(from: String, body: String) {
        _msgs.tryEmit(from to body)
    }
}
