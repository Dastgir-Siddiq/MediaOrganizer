package com.mediaorganizer.app

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class MediaScanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(
            state = Tile.STATE_INACTIVE,
            subtitleText = AppPreferences.getLastScanSubtitle(applicationContext)
        )
    }

    override fun onClick() {
        super.onClick()
        updateTileState(state = Tile.STATE_ACTIVE, subtitleText = "Scanning...")
        WatchdogScheduler.triggerImmediateScan(applicationContext, isManualTrigger = true)
    }

    private fun updateTileState(state: Int, subtitleText: String) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = getString(R.string.qs_tile_scan)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitleText
        }
        tile.updateTile()
    }
}
