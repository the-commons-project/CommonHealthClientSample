package org.thecommonsproject.android.commonhealth.sampleapp

import android.app.Application
import android.content.Context
import androidx.room.Room
import net.sqlcipher.database.SupportFactory
import org.thecommonsproject.android.common.keyvaluestore.SecureNamespacedKeyValueStore
import org.thecommonsproject.android.common.keyvaluestore.room.KeyValueLocalDataStore
import org.thecommonsproject.android.common.util.CryptoProvider
import org.thecommonsproject.android.common.util.DatabasePassphraseManager
import org.thecommonsproject.android.common.util.DefaultAndroidKeystoreClientWrapper
import org.thecommonsproject.android.common.util.DefaultCryptoProvider
import org.thecommonsproject.android.commonhealthclient.*
import org.thecommonsproject.android.commonhealthclient.notification.CommonHealthNotification
import timber.log.Timber

class SampleApplication: Application() {

    private var database: SampleApplicationDatabase? = null
    private val cryptoProvider: CryptoProvider by lazy {
        DefaultCryptoProvider(DefaultAndroidKeystoreClientWrapper())
    }

    private fun getDatabasePassphrase(context: Context, cryptoProvider: CryptoProvider) : ByteArray {
        val passphraseFilePath = context.filesDir.absolutePath.plus("/encryptedDatabasePassphrase")
        val passphraseManager = DatabasePassphraseManager(
            passphraseFilePath,
            64,
            "passphrase_file",
            cryptoProvider
        )

        return passphraseManager.getPassphrase()
    }

    private fun createDataBase(context: Context, cryptoProvider: CryptoProvider): SampleApplicationDatabase {
        val passphrase = getDatabasePassphrase(context, cryptoProvider)
        val supportFactory = SupportFactory(passphrase)
        val result = Room.databaseBuilder(
            context.applicationContext,
            SampleApplicationDatabase::class.java,
            "SampleApp.db"
        )
            .openHelperFactory(supportFactory)
            .build()
        database = result
        return result
    }

    private fun initializeCommonHealthStore(application: Application) {

        val context = application.applicationContext
        val notificationPreferences = NotificationPreferences(
            subscribedNotificationTypes = setOf(
                CommonHealthNotificationType.AUTHORIZATION_FLOW_COMPLETED_WITH_RESULT,
                CommonHealthNotificationType.NEW_DATA_AVAILABLE
            ),
            subscriber = { notification ->
                when(notification) {
                    is CommonHealthNotification.NewData -> {
                        Timber.d("New data available at: ${notification.notificationTimestamp}")
                    }
                    is CommonHealthNotification.AuthorizationCompleted -> {
                        when(val response = notification.userResponse) {
                            is CommonHealthAuthorizationActivityResponse.Failure -> {
                                response.errorMessage?.let {
                                    Timber.e("Authorization failed with error: $it")
                                }
                            }
                            is CommonHealthAuthorizationActivityResponse.Success -> Timber.d("Authorization succeeded at ${notification.notificationTimestamp}")
                            is CommonHealthAuthorizationActivityResponse.UserCanceled -> Timber.d("User canceled authorization")
                        }
                    }
                    is CommonHealthNotification.SingleAuthorizationResult -> TODO("TODO HELLO")
                }
            }
        )
        val configuration = CommonHealthStoreConfiguration(
            appId = BuildConfig.APPLICATION_ID,
            commonHealthAppId = BuildConfig.COMMON_HEALTH_APP_ID,
            developerModeEnabled = true,
            attestationServiceConfiguration = null,
            commonHealthAuthorizationUri = BuildConfig.INTERAPP_AUTHORIZATION_URI,
            authorizationCallbackUri = BuildConfig.AUTH_CALLBACK_URI,
            loggingEnabled = true,
            notificationPreferences = notificationPreferences
        )

        val database = database ?: createDataBase(context, cryptoProvider)
        val namespacedKeyValueStore = SecureNamespacedKeyValueStore(
            KeyValueLocalDataStore(database.keyValueEntryDao()),
            "secure_namespaced_key_value_store",
            cryptoProvider
        )

        //if initialization fails, halt
        //client applications need to decide how to handle this
        try {
            CommonHealthStore.initialize(
                application,
                configuration,
                namespacedKeyValueStore
            )
        } catch (e: Exception) {
            throw RuntimeException("Cannot initialize CommonHealthStore. Halting...")
        }

    }

    override fun onCreate() {
        super.onCreate()
        //For Logging
        Timber.plant(Timber.DebugTree())
        Timber.d("CommonHealth Sample Client Application Logging Enabled")

        initializeCommonHealthStore(this)
    }

}