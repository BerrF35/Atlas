package com.berrf35.atlas

import android.app.Application
import com.berrf35.atlas.core.data.di.AtlasDataLocator

class AtlasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AtlasDataLocator.init(this)
    }
}
