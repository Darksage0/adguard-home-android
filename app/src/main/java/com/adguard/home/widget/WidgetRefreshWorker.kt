package com.adguard.home.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adguard.home.data.repository.AdGuardRepository
import com.adguard.home.domain.model.NetworkResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetWorkerEntryPoint {
    fun repository(): AdGuardRepository
}

class WidgetRefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetWorkerEntryPoint::class.java
        )
        val repository = entryPoint.repository()

        return when (val result = repository.getDashboardData()) {
            is NetworkResult.Success -> {
                val data = result.data
                val glanceManager = GlanceAppWidgetManager(context)
                val glanceIds = glanceManager.getGlanceIds(AdGuardGlanceWidget::class.java)

                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[AdGuardGlanceWidget.PROTECTION_ENABLED_KEY] = data.protectionState.isEnabled
                            this[AdGuardGlanceWidget.BLOCKED_COUNT_KEY] = data.headlineCards.blockedFiltering.value
                            this[AdGuardGlanceWidget.BLOCKED_PERCENT_KEY] = data.headlineCards.blockedFiltering.percentage ?: 0f
                        }
                    }
                    AdGuardGlanceWidget().update(context, glanceId)
                }
                Result.success()
            }
            is NetworkResult.Error -> Result.retry()
            is NetworkResult.Loading -> Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "AdGuardWidgetRefreshWorker"

        fun enqueuePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodicRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
