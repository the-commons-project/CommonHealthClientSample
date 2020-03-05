package org.thecommonsproject.android.commonhealth.sampleapp

import android.app.Application
import android.content.Context
import androidx.room.Room
import org.thecommonsproject.android.common.keyvaluestore.SecureNamespacedKeyValueStore
import org.thecommonsproject.android.common.keyvaluestore.room.KeyValueLocalDataStore
import org.thecommonsproject.android.commonhealthclient.CommonHealthStore
import org.thecommonsproject.android.commonhealthclient.CommonHealthStoreConfiguration
import timber.log.Timber

class SampleApplication: Application() {

    private var database: SampleApplicationDatabase? = null

    private fun createDataBase(context: Context): SampleApplicationDatabase {
        val result = Room.databaseBuilder(
            context.applicationContext,
            SampleApplicationDatabase::class.java,
            "SampleApp.db"
        ).build()
        database = result
        return result
    }

    private fun initializeCommonHealthStore(application: Application) {

        val context = application.applicationContext
        val configuration = CommonHealthStoreConfiguration(
            appId = BuildConfig.APPLICATION_ID,
            commonHealthAppId = BuildConfig.COMMON_HEALTH_APP_ID,
            developerModeEnabled = true,
            attestationServiceConfiguration = null,
            commonHealthAuthorizationUri = BuildConfig.INTERAPP_AUTHORIZATION_URI,
            authorizationCallbackUri = BuildConfig.AUTH_CALLBACK_URI,
            loggingEnabled = true
        )

        val database = database ?: createDataBase(context)
        val namespacedKeyValueStore = SecureNamespacedKeyValueStore(
            KeyValueLocalDataStore(database.keyValueEntryDao()),
            "secure_namespaced_key_value_store"
        )

        CommonHealthStore.initialize(
            application,
            configuration,
            namespacedKeyValueStore
        )

    }

    override fun onCreate() {
        super.onCreate()
        //For Logging
        Timber.plant(Timber.DebugTree())
        Timber.d("CommonHealth Sample Client Application Logging Enabled")

        initializeCommonHealthStore(this)
    }

}