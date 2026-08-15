package com.adguard.home.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.adguard.home.domain.model.NetworkResult
import dagger.hilt.android.EntryPointAccessors
import java.text.NumberFormat
import java.util.Locale

class AdGuardGlanceWidget : GlanceAppWidget() {

    override var stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val isEnabled = prefs[PROTECTION_ENABLED_KEY] ?: true
        val blockedCount = prefs[BLOCKED_COUNT_KEY] ?: 0L
        val blockedPercent = prefs[BLOCKED_PERCENT_KEY] ?: 0f

        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(14.dp)
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = if (isEnabled) "Protected" else "Paused",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isEnabled) GlanceTheme.colors.primary else GlanceTheme.colors.error
                            )
                        )
                        Text(
                            text = "AdGuard Home",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                    }

                    Button(
                        text = if (isEnabled) "Pause" else "Resume",
                        onClick = actionRunCallback<ToggleProtectionCallback>()
                    )
                }

                Spacer(modifier = GlanceModifier.height(10.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = numberFormat.format(blockedCount),
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = GlanceTheme.colors.onSurface
                            )
                        )
                        Text(
                            text = "Blocked (%.1f%%)".format(blockedPercent),
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = GlanceTheme.colors.error
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
        val PROTECTION_ENABLED_KEY = booleanPreferencesKey("widget_protection_enabled")
        val BLOCKED_COUNT_KEY = longPreferencesKey("widget_blocked_count")
        val BLOCKED_PERCENT_KEY = floatPreferencesKey("widget_blocked_percent")
    }
}

class ToggleProtectionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetWorkerEntryPoint::class.java
        )
        val repository = entryPoint.repository()

        val currentStatus = repository.getDashboardData()
        if (currentStatus is NetworkResult.Success) {
            val isEnabled = currentStatus.data.protectionState.isEnabled
            if (isEnabled) {
                repository.setProtection(enabled = false, durationMs = 300_000L)
            } else {
                repository.setProtection(enabled = true, durationMs = null)
            }
        }

        val refreshRequest = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        WorkManager.getInstance(context).enqueue(refreshRequest)
    }
}
