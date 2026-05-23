package com.androidstudiomobile

import android.app.Application
import androidx.room.Room
import com.androidstudiomobile.data.db.AppDatabase
import com.androidstudiomobile.lsp.LanguageServerManager
import com.androidstudiomobile.profiler.BuildProfiler

class MainApplication : Application() {

    companion object {
        lateinit var db: AppDatabase            private set
        lateinit var lsp: LanguageServerManager private set
        lateinit var profiler: BuildProfiler    private set
    }

    override fun onCreate() {
        super.onCreate()
        db       = Room.databaseBuilder(this, AppDatabase::class.java, "asm_db")
            .fallbackToDestructiveMigration().build()
        lsp      = LanguageServerManager(this)
        profiler = BuildProfiler(this)
    }
}
