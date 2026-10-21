package com.chunting.reader

import android.app.Application
import com.chunting.reader.data.SettingsStore
import com.chunting.reader.data.db.AppDatabase
import com.chunting.reader.player.PlayerController

class TingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        settings = SettingsStore(this)
        PlayerController.init(this)
    }

    override fun onTerminate() {
        PlayerController.release()
        super.onTerminate()
    }

    companion object {
        lateinit var db: AppDatabase
            private set
        lateinit var settings: SettingsStore
            private set
    }
}
