package org.thecommonsproject.android.commonhealth.sampleapp

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import com.google.crypto.tink.config.TinkConfig
import com.jakewharton.threetenabp.AndroidThreeTen
import org.thecommonsproject.android.common.keyvaluestore.SecureNamespacedKeyValueStore
import org.thecommonsproject.android.common.keyvaluestore.room.KeyValueLocalDataStore
import org.thecommonsproject.android.commonhealthclient.CommonHealthStore
import org.thecommonsproject.android.commonhealthclient.CommonHealthStoreConfiguration
import org.thecommonsproject.android.commonhealthclient.CommonHealthStoreProvider

class SampleApplication: Application(), CommonHealthStoreProvider {


    private val lock = Any()

    private var database: SampleApplicationDatabase? = null

    @Volatile
    private var commonHealthStore: CommonHealthStore? = null
        @VisibleForTesting set

    override fun provideCommonHealthStore(): CommonHealthStore {
        synchronized(lock) {
            return commonHealthStore ?: {
                val store = createCommonHealthStore(this)
                commonHealthStore = store
                store
            }()
        }
    }

    private fun createDataBase(context: Context): SampleApplicationDatabase {
        val result = Room.databaseBuilder(
            context.applicationContext,
            SampleApplicationDatabase::class.java,
            "SampleApp.db"
        ).build()
        database = result
        return result
    }

    private fun createSecureNamespacedKeyValueStore(context: Context): SecureNamespacedKeyValueStore {
        val database = database ?: createDataBase(context)
        val namespacedKeyValueStore = KeyValueLocalDataStore(database.keyValueEntryDao())
        return SecureNamespacedKeyValueStore(
            namespacedKeyValueStore,
            "secure_namespaced_key_value_store"
        )
    }

    private fun createCommonHealthStore(context: Context): CommonHealthStore {

        val configuration = CommonHealthStoreConfiguration.Builder()
            .setAppId(BuildConfig.APPLICATION_ID)
            .setCommonHealthAppId(BuildConfig.COMMON_HEALTH_APP_ID)
            .setCommonHealthAuthorizationUri(BuildConfig.INTERAPP_AUTHORIZATION_URI)
            .setAuthorizationCallbackUri(BuildConfig.AUTH_CALLBACK_URI)
            .setDeveloperModeEnabled(true)
            .build()

        val namespacedKeyValueStore = createSecureNamespacedKeyValueStore(
            context
        )

        return CommonHealthStore(
            context,
            configuration,
            namespacedKeyValueStore
        )

    }

    override fun onCreate() {
        super.onCreate()

        //Initialize AndroidThreeTen - backport of java.time
        AndroidThreeTen.init(this)
        //Initialize Tink
        TinkConfig.register()

//        if (BuildConfig.DEBUG) {
//            Timber.plant(Timber.DebugTree())
//        } else {
////            Timber.plant(CrashReportingTree())
//        }

    }

}