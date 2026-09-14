package com.seca.messages.sms

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seca.messages.link.LinkService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Picks up again what a restart or an update stopped: the alarms of scheduled
 * messages, the erasure of verification codes, and Seca Link's listening.
 * The alarms are set again too once the exact-time permission changes.
 * Not exported: only the system reaches it.
 */
class ScheduleRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ScheduledMessages(context).rearmAll()
                LinkService.start(context)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        runCatching { CodeCleanup.sweep(context) }
                    } finally {
                        pending.finish()
                    }
                }
            }
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> ScheduledMessages(context).rearmAll()
        }
    }
}
