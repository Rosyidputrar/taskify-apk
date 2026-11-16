package com.rosyidputrar.taskify.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rosyidputrar.taskify.MainActivity
import com.rosyidputrar.taskify.R

/**
 * Tile Quick Settings "Tangkap": dari panel notifikasi langsung ke sheet Tangkap — tanpa mencari
 * ikon aplikasi. Kunci PIN tetap berlaku (sheet terbuka setelah PIN benar).
 */
class CaptureTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_capture_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = getString(R.string.tile_capture_subtitle)
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        // Layar terkunci → minta buka kunci perangkat dulu.
        if (isLocked) unlockAndRun { open() } else open()
    }
}
