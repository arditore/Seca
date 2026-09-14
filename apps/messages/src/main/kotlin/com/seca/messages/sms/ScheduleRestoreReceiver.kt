package com.seca.messages.sms

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sets the alarms of scheduled messages again when Android dropped them: after
 * a restart or an update, and once the exact-time permission changes. The
 * verification codes whose erasure a restart cancelled are erased then.
 * Not exported: only the system reaches it.
 */
class ScheduleRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ScheduledMessages(context).rearmAll()
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
