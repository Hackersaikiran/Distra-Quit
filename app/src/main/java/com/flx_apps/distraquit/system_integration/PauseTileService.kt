package com.flx_apps.distraquit.system_integration

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.flx_apps.distraquit.features.PauseButtonFeature

/**
 * The [PauseTileService] is the tile that is shown in the quick settings. It allows the user to
 * toggle the pause state of the [DistraQuitAccessibilityService].
 */
class PauseTileService : TileService() {
    /**
     * Called when the user clicks the tile.
     * Will toggle the pause state of the [DistraQuitAccessibilityService].
     */
    override fun onClick() {
        super.onClick()

        if (DistraQuitAccessibilityService.instance == null) {
            return
        }

        PauseButtonFeature.togglePause(this)
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    /**
     * Updates the tile to the current state of the [DistraQuitAccessibilityService].
     * @see DistraQuitAccessibilityService
     * @see DistraQuitState
     */
    private fun updateTile() {
        val state = when (DistraQuitAccessibilityService.state.value) {
            DistraQuitState.Inactive -> Tile.STATE_UNAVAILABLE
            DistraQuitState.Paused -> Tile.STATE_ACTIVE
            DistraQuitState.Active -> Tile.STATE_INACTIVE
        }

        val tile = qsTile
        tile.state = state
        tile.updateTile()
    }
}