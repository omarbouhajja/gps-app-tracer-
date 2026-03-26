package com.example.sendsmsapp.util

import android.content.Context
import androidx.annotation.RawRes
import com.example.sendsmsapp.model.Device

object Csv {
    fun loadDevices(ctx: Context, @RawRes resId: Int): List<Device> {
        return ctx.resources.openRawResource(resId).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map { row ->
                    val parts = row.split(",")
                    val phone = parts[0].trim()
                    val imei  = parts.getOrNull(1)?.trim().orEmpty()
                    Device(phone, imei)
                }.toList()
        }
    }
}
