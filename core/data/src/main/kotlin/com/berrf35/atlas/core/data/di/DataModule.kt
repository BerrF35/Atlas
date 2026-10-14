package com.berrf35.atlas.core.data.di

import android.content.Context
import com.berrf35.atlas.core.data.database.AtlasDao
import com.berrf35.atlas.core.data.database.AtlasDbHelper
import com.berrf35.atlas.core.data.repository.AnalyticsRepository
import com.berrf35.atlas.core.data.repository.AtlasSettingsRepository
import com.berrf35.atlas.core.data.repository.DiagnosticsRepository
import com.berrf35.atlas.core.data.repository.ExportRepository
import com.berrf35.atlas.core.data.repository.JourneyRepository
import com.berrf35.atlas.core.data.repository.PlacesRepository
import com.berrf35.atlas.core.data.repository.TrackingRepository
import com.berrf35.atlas.core.data.repository.impl.AnalyticsRepositoryImpl
import com.berrf35.atlas.core.data.repository.impl.AtlasSettingsRepositoryImpl
import com.berrf35.atlas.core.data.repository.impl.DiagnosticsRepositoryImpl
import com.berrf35.atlas.core.data.repository.impl.ExportRepositoryImpl
import com.berrf35.atlas.core.data.repository.impl.JourneyRepositoryImpl
import com.berrf35.atlas.core.data.repository.impl.PlacesRepositoryImpl
import com.berrf35.atlas.core.data.repository.impl.TrackingRepositoryImpl
import com.berrf35.atlas.core.datastore.AtlasPreferencesDataSource

/**
 * Service locator / factory for repositories, supporting both manual creation
 * and Dagger / Hilt dependency injection.
 */
object AtlasDataLocator {
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var dbHelperInstance: AtlasDbHelper? = null
    @Volatile
    private var daoInstance: AtlasDao? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        preferencesDataSource.attachContext(app)
        if (dbHelperInstance == null) {
            synchronized(this) {
                if (dbHelperInstance == null) {
                    val helper = AtlasDbHelper(app)
                    dbHelperInstance = helper
                    daoInstance = AtlasDao(helper)
                }
            }
        }
    }

    fun getDao(): AtlasDao? = daoInstance
    fun getContext(): Context? = appContext

    val preferencesDataSource: AtlasPreferencesDataSource by lazy { AtlasPreferencesDataSource() }
    val settingsRepository: AtlasSettingsRepository by lazy { AtlasSettingsRepositoryImpl(preferencesDataSource) }
    val trackingRepository: TrackingRepository by lazy { TrackingRepositoryImpl(settingsRepository, { getDao() }, { getContext() }) }
    val journeyRepository: JourneyRepository by lazy { JourneyRepositoryImpl(settingsRepository, { getDao() }) }
    val analyticsRepository: AnalyticsRepository by lazy { AnalyticsRepositoryImpl(settingsRepository, { getDao() }) }
    val placesRepository: PlacesRepository by lazy { PlacesRepositoryImpl(settingsRepository, { getDao() }) }
    val diagnosticsRepository: DiagnosticsRepository by lazy { DiagnosticsRepositoryImpl(settingsRepository, { getDao() }) }
    val exportRepository: ExportRepository by lazy { ExportRepositoryImpl(settingsRepository, { getDao() }, { getContext() }) }
}
