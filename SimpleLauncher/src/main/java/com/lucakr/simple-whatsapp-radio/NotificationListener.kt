package com.lucakr.`simple-whatsapp-radio`

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotificationListener : NotificationListenerService() {
    private val nlContext = this
    private var focus: Notification.Action? = null
    private var answer: Notification.Action? = null
    private var decline: Notification.Action? = null
    private var end: Notification.Action? = null

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                OverlayService.ANSWER   -> answer?.actionIntent?.send()
                OverlayService.DECLINE  -> decline?.actionIntent?.send()
                OverlayService.END      -> end?.actionIntent?.send()
                OverlayService.FOCUS    -> focus?.actionIntent?.send()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        println("Notification Listener Active")

        // Setup Broadcast Receiver
        val filter = IntentFilter(OverlayService.FOCUS).apply {
            addAction(OverlayService.ANSWER)
            addAction(OverlayService.DECLINE)
            addAction(OverlayService.END)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, filter)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        println("Notification Listener Connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Filter whatsapp
        if(sbn.packageName != "com.whatsapp") return
        println("Notification Posted")

        // Get the actions
        sbn.notification.actions!!.find{it.title.contains("Answer")}?.let{
            answer = it
            println("Answer action found")
        }
        sbn.notification.actions!!.find{it.title.contains("Decline")}?.let{
            decline = it
            println("Decline action found")
        }
        sbn.notification.actions!!.find{it.title.contains("Hang up")}?.let{
            end = it
            println("Hang up action found")
        }

        // Send notification posted intent with caller name
        val intent = Intent(sbn.notification.extras!!.getString("android.title"))
        println("TX $intent")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Filter whatsapp
        if(sbn.packageName != "com.whatsapp") return
        println("Notification Removed")
    }
}