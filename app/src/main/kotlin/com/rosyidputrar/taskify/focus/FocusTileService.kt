package com.rosyidputrar.taskify.focus

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rosyidputrar.taskify.MainActivity
import com.rosyidputrar.taskify.R
import com.rosyidputrar.taskify.core.domain.focus.FocusPhase
import org.koin.android.ext.android.inject

/** Tile Quick Settings "Fokus": menyala selama sesi berjalan; ketuk → layar Mode Fokus. */
class FocusTileService : TileService() {
    private val controller: FocusController by inject()

    override fun onStartListening() {
        super.onStartListening()
        val run = controller.run.value
        qsTile?.apply {
            state = if (run == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            label = getString(R.string.tile_focus_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = when {
                    run == null -> getString(R.string.tile_focus_subtitle)
                    run.isPaused -> "Dijeda"
                    run.phase == FocusPhase.Focus -> "Putaran ${run.round}"
                    else -> run.phase.label
                }
            }
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun { open() } else open()
    }
}
