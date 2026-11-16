package com.rosyidputrar.taskify.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Alarm akhir fase + tombol di notifikasi Mode Fokus (Jeda/Lanjut/Lewati/Berhenti). */
class FocusReceiver : BroadcastReceiver(), KoinComponent {
    private val controller: FocusController by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_PHASE_END -> controller.catchUp()
                    ACTION_PAUSE -> controller.pause()
                    ACTION_RESUME -> controller.resume()
                    ACTION_SKIP -> controller.skip()
                    ACTION_STOP -> controller.stop()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PHASE_END = "com.rosyidputrar.taskify.focus.PHASE_END"
        const val ACTION_PAUSE = "com.rosyidputrar.taskify.focus.PAUSE"
        const val ACTION_RESUME = "com.rosyidputrar.taskify.focus.RESUME"
        const val ACTION_SKIP = "com.rosyidputrar.taskify.focus.SKIP"
        const val ACTION_STOP = "com.rosyidputrar.taskify.focus.STOP"
    }
}
