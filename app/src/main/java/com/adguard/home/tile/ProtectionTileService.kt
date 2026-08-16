package com.adguard.home.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.adguard.home.data.repository.AdGuardRepository
import com.adguard.home.domain.model.NetworkResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TileServiceEntryPoint {
    fun repository(): AdGuardRepository
}

class ProtectionTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: AdGuardRepository

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            TileServiceEntryPoint::class.java
        )
        repository = entryPoint.repository()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentTile = qsTile ?: return

        // TileService.onClick() fires even from a locked screen -- unlockAndRun prompts the
        // device's normal unlock (PIN/biometric/swipe) first if locked, and runs immediately
        // with no prompt at all if the device is already unlocked. This closes the "anyone
        // holding a locked phone can pause DNS filtering with one tap" gap without opening the
        // app, keeping the tile's "works without opening the app" requirement intact.
        unlockAndRun {
            serviceScope.launch {
                val isCurrentlyActive = currentTile.state == Tile.STATE_ACTIVE

                if (isCurrentlyActive) {
                    // Pause for 5 minutes default (300,000 ms)
                    val result = repository.setProtection(enabled = false, durationMs = 300_000L)
                    if (result is NetworkResult.Success) {
                        updateTileUI(isEnabled = false, isPaused = true)
                    }
                } else {
                    val result = repository.setProtection(enabled = true, durationMs = null)
                    if (result is NetworkResult.Success) {
                        updateTileUI(isEnabled = true, isPaused = false)
                    }
                }
            }
        }
    }

    private fun updateTileState() {
        serviceScope.launch {
            when (val result = repository.getDashboardData()) {
                is NetworkResult.Success -> {
                    val state = result.data.protectionState
                    updateTileUI(isEnabled = state.isEnabled, isPaused = state.isPaused)
                }
                else -> {
                    val tile = qsTile ?: return@launch
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.subtitle = "Unreachable"
                    tile.updateTile()
                }
            }
        }
    }

    private fun updateTileUI(isEnabled: Boolean, isPaused: Boolean) {
        val tile = qsTile ?: return
        if (isEnabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Protection"
            tile.subtitle = "Enabled"
        } else if (isPaused) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Protection"
            tile.subtitle = "Paused (5m)"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Protection"
            tile.subtitle = "Disabled"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
