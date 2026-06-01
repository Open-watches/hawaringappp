package com.handwriting.app

import android.app.Application
import com.handwriting.app.data.database.HandwritingDatabase
import com.handwriting.app.data.repository.HandwritingRepository

/**
 * Application class for initializing app-wide dependencies.
 */
class HandwritingApplication : Application() {

    // Lazy-initialized database
    val database: HandwritingDatabase by lazy {
        HandwritingDatabase.getDatabase(this)
    }

    // Repository instance
    val repository: HandwritingRepository by lazy {
        HandwritingRepository(database.handwritingDao(), this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        HandwritingDatabase.closeDatabase()
    }

    companion object {
        lateinit var instance: HandwritingApplication
            private set
    }
}
