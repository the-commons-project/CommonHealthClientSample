# CommonHealth Client SDK

The CommonHealth Client SDK provides an interface that allows applications to access health data stored in CommonHealth.

The CommonHealth Client SDK is in closed beta. If you would like to participate in our beta program, please reach out to info [at] commonhealth.org. 

While we consider the SDK to be relatively stable, this is pre-release software, so the interfaces are subject to change based on evolving requirements and developer feedback. We're currently investigating way to make it easier to remove configuration dependencies, so if you find anything particularly burdensome or confusing, please let us know.

## Audience 
This quick start guide is geared towards participants in our closed beta program. This guide assumes that you have the CommonHealth developer edition installed on your device and you have gone through the enrollment process in the application.

## Configuration Requirements

### Gradle Dependencies

The CommonHealth Client SDK consists of two modules: commonhealthclient and common. Commonhealthclient contains the bulk of functionality for the SDK, while common types shared between the CommonHealth application and the CommonHealth Client SDK. You'll need to add the following to your application's list of dependencies:

```
implementation "org.thecommonsproject.commonhealth:common:0.1.0"
implementation "org.thecommonsproject.commonhealth:commonhealthclient:0.1.0"
```

The artifacts currently reside in our organization's bintray repo, but at some point these will be migrated to jcenter. In the mean time, you'll need to add the following maven repository to your list of repositories, typically defined in the project's `gradle.build` file:

`maven { url "https://dl.bintray.com/thecommonsproject/CommonHealth" }`

### Manifest

The interapplication data sharing functionality of CommonHealth leverages native Android communication components. CommonHealth and the CommonHealth Client SDK expect the following service and receivers to be defined in your `manifest.xml` file:

```
<service
    android:name="org.thecommonsproject.android.commonhealthclient.service.IdentityTokenJobIntentService"
    android:exported="false"
    android:permission="android.permission.BIND_JOB_SERVICE"/>

<receiver
    android:name="org.thecommonsproject.android.commonhealthclient.service.IdentityTokenBroadcastReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="org.thecommonsproject.android.common.interapp.identitytoken.broadcastreceiver.action.IDENTITY_TOKEN_REQUEST" />
    </intent-filter>
</receiver>

<receiver
    android:name="org.thecommonsproject.android.commonhealthclient.broadcastreceiver.AppIdentifierBroadcastReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="org.thecommonsproject.android.commonhealthclient.broadcastreceiver.AppIdentifierBroadcastReceiver.NOP" />
    </intent-filter>
</receiver>
```

Our authorziation process is inspired by OAuth and requires that an activity be defined in the client application's `manifest.xml` file to receive a redirect from CommonHealth. Here's an example:

```
<activity
    android:name="org.thecommonsproject.android.commonhealthclient.RedirectUriReceiverActivity"
    tools:node="replace">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data
            android:host="interapp"
            android:pathPrefix="/redirect"
            android:scheme="org.thecommonsproject.android.commonhealth.sampleapp" />
    </intent-filter>
</activity>
```

Note that the Uri defined by the data element translates to `org.thecommonsproject.android.commonhealth.sampleapp://interapp/redirect`. This is configurable, but must match the `authorizationCallbackUri` in the `CommonHealthStoreConfiguration` object (see below).

### Application

The CommonHealth Client SDK implements components that require access to the `CommonHealthStore`. Currently, the client application custom `Application` class **must** implement the `CommonHealthStoreProvider` interface. This interface has a single method:

```
fun provideCommonHealthStore(): CommonHealthStore
```

See `SampleApplication` in this repo for an example implementation.

## Initialization 

### Required Library Initialization
The CommonHealth Client SDK library depends on serveral libraries, two of which currently require that the client application perform initialization prior to creating the `CommonHealthStore` object. We're working on removing this as a requirement to use the CommonHealth Client SDK, but for now, the easiest way is to add the following to the `onCreate` method of your custom `Application` class implementation:

```
//Initialize AndroidThreeTen - backport of java.time
AndroidThreeTen.init(this)
//Initialize Tink
TinkConfig.register()
```
See the `onCreate` method in the `SampleApplication` class in this repo for a full implementation.

### CommonHealthStore Configuration

The client application must create a `CommonHealthStore` by providing a `Context`, `CommonHealthStoreConfiguration` object, and an object that implements the `NamespacedKeyValueStore` interface. 

For development, you can use the following to create the `CommonHealthStoreConfiguration` object:

```
val configuration = CommonHealthStoreConfiguration.Builder()
    .setAppId(BuildConfig.APPLICATION_ID)
    .setCommonHealthAppId("org.thecommonsproject.android.phr.developer")
    .setCommonHealthAuthorizationUri("org.thecommonsproject.android.phr://interapp/auth)
    .setAuthorizationCallbackUri("org.thecommonsproject.android.commonhealth.sampleapp://interapp/redirect")
    .setDeveloperModeEnabled(true)
    .build()
```

Note that you'll need to replace the `setAuthorizationCallbackUri` parameter with the Uri defined by the `data` element in the redirect activity defined in your application's `manifest.xml`.

In order to store the keys required to secure the connection(s) with the CommonHealth application, the client application must also provide a secure implementation of the `NamespacedKeyValueStore` interface. This object persists the stored values across application launches. 

The `common` module provides a `NamespacedKeyValueStore` implementation that utilizes the Android Room architecture component. If you're not using Android Room for persistence, you can provide an implementation using the persistence method of your choosing. 

To help with security, the `common` module provides `SecureNamespacedKeyValueStore` to automatically encrypt and decrypt values. This class leverages the Google Tink encryption library and a key stored in the Android Keystore.

The `SampleApplication` class contains the `createSecureNamespacedKeyValueStore` method to create a `SecureNamespacedKeyValueStore`:

```
private fun createSecureNamespacedKeyValueStore(context: Context): SecureNamespacedKeyValueStore {
    val database = database ?: createDataBase(context)
    val namespacedKeyValueStore = KeyValueLocalDataStore(database.keyValueEntryDao())
    val synchronizedAndroidKeystoreKMSClient = SynchronizedAndroidKeystoreKmsClientWrapper()
    return SecureNamespacedKeyValueStore(
        namespacedKeyValueStore,
        "secure_namespaced_key_value_store",
        synchronizedAndroidKeystoreKMSClient
    )
}
```

Note that `database` is an AndroidRoom database that contains the `KeyValueEntry` entity and implements the `KeyValueEntryDao` DAO. Both of these classes can be found in the `common` module.

## Usage

Once installation and configuration is taken care of, usage of the CommonHealth Client SDK is pretty straightforward. The following outlines the steps client applications will need to take in order to fetch data from CommonHealth:

1) Check that CommonHealth is available
2) Define the scope of access
3) Check the authorization status
4) Authorize
5) Build a query and fetch data

### A note about the `connectionAlias` parameter

You'll likely notice that many of the interface methods require a `connectionAlias` parameter. The `connectionAlias` parameter is reserved for future support for multiple patients. For now, just use any string, making sure that it remains consistent across application launches.

### Check that CommonHealth is available

The `CommonHealthStore` class provides the `isCommonHealthAvailable` method that determines if the CommonHealth application is installed on the user's device and it is set up to share data with client application. The method signature is:

```
suspend fun isCommonHealthAvailable(context: Context): Boolean
```

If this method returns false, you may need to instruct the user to install and onboard with the CommonHealth app.

### Define the scope of access

Client applications should follow the principle of least privilege and only request access to resources deemed truly necessary. The production version of CommonHealth requires that applications are registered and their scope of access does not exceed their registered values. During the development process, the developer version of CommonHealth does not perform this check.

Client applications must define a `ScopeRequest`, which encapsulates a set of `Scope` objects. Each `Scope` object contains a data type (`DataType`) and an access type (`Scope.Access`). `ScopeRequest` contains a `Builder` class to help with implementation.

The following sample creates a `ScopeRequest` object requesting read access to all currently available clinical data types:

```
val allDataTypes: List<DataType.ClinicalResource> = listOf(
    DataType.ClinicalResource.AllergyIntoleranceResource,
    DataType.ClinicalResource.ClinicalVitalsResource,
    DataType.ClinicalResource.ConditionsResource,
    DataType.ClinicalResource.ImmunizationsResource,
    DataType.ClinicalResource.LaboratoryResultsResource,
    DataType.ClinicalResource.MedicationResource,
    DataType.ClinicalResource.ProceduresResource
)

val scopeRequest: ScopeRequest by lazy {
    val builder = ScopeRequest.Builder()
    allDataTypes.forEach {
        builder.add(it, Scope.Access.READ)
    }
    builder.build()
}
```

### Check the authorization status

The `CommonHealthStore` class provides the `checkAuthorizationStatus` method that determines the current authorization status and returns an enum that you can use to determine whether authorization is needed for the specified scope request. The method signature is:

```
suspend fun checkAuthorizationStatus(
    context: Context,
    connectionAlias: String,
    scopeRequest: ScopeRequest
): CommonHealthAuthorizationStatus
```

The `CommonHealthAuthorizationStatus` enum defines 4 values:

 - `shouldRequest`
 - `unnecessary`
 - `cannotAuthorize`
 - `exceedsMaximumAllowedScope`

The `shouldRequest` value means that the application needs to request authorization in order to query resources specified in the scope request.

The `unnecessary` value means that authorization has already been performed for the current scope request and the client application is able to fetch data from CommonHealth.

The `cannotAuthorize` value means that either the CommonHealth app is not available OR in production environments, the CommonHealth application is in safe mode and the client application is not registered with CommonHealth.

The `exceedsMaximumAllowedScope` value means that in production environments, the CommonHealth application is in safe mode and the requested scope exceeds the scope defined in your CommonHealth registration.

### Authorize

Authorization is performed by launching the `AuthorizationManagementActivity`. To do so, create an intent using the `AuthorizationManagementActivity` static method `createStartForResultIntent`, passing in an authorization request. You then start the activity using the `startActivityForResult` like you typically would for any activity. Note that you will also need to implement the `onActivityResult` method of the calling Activity or Fragment to get the status when `AuthorizationManagementActivity` finishes.

The following sample launches the `AuthorizationManagementActivity`:

```
val authorizationRequest = AuthorizationRequest(
    connectionAlias,
    scopeRequest,
    false,
    "Sample app would like to read your labs, vitals, and conditions."
)

val intent = AuthorizationManagementActivity.createStartForResultIntent(
    context,
    authorizationRequest
)

startActivityForResult(authIntent, REQUEST_CODE)
```

The following sample implements the Fragment's `onActivityResult` method:

```
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when(requestCode) {
        REQUEST_CODE -> {

            when(resultCode) {
                AuthorizationManagementActivity.SUCCESS -> {
                    // authorization was successful, we can now fetch data from CommonHealth
                }
                AuthorizationManagementActivity.FAILURE -> {
                    //authorization failed, check the failure exception
                    when (val exception = 
                        data?.getSerializableExtra(AuthorizationManagementActivity.EXTRA_AUTH_FAILURE_EXCEPTION)) {
                        is InterappException.ClientApplicationValidationFailed -> {
                            val message = exception.message ?: "Authorization Failed"
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        else -> Toast.makeText(context, "Authorization Failed", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    //Authorization not determined
                    Toast.makeText(context, "Authorization Not Determined", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        else -> {
            updateUI()
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
```

See `CategoryListFragment` in this repo for a working implementation.

### Build a query and fetch data

In order to fetch data, client applications first need to build a `DataQuery` object. Currently, only read queries are supported. 

The following sample builds a query that reads all of the patients laboratory results after a reference date:

```
val laboratoryResultsResource = DataType.ClinicalResource.LaboratoryResultsResource
val referenceDate = ...
val referenceDateParameter = DataQuery.Parameter.After(referenceDate)
val query = DataQuery.Builder()
    .withDataTypes(setOf(laboratoryResultsResource))
    .withAction(DataQuery.Action.read)
    .withQueryParameter(referenceDateParameter)
    .build()
```

Once the `DataQuery` object has been built, it can be executed via the `executeDataQuery` method on the `CommonHealthStore`. The interface for this method is defined below:

```
suspend fun executeDataQuery(
    context: Context,
    query: DataQuery,
    connectionAlias: String
): List<DataQueryResult>
```

As you can see, the method returns a list of `DataQueryResult` instances. For requests for clinical data, these can be cast to `ClinicalDataQueryResult` instances. Each `ClinicalDataQueryResult` instance contains the following:

 - resourceType: DataType.ClinicalResource - the type of the resource
 - json: String - JSON string representation of the FHIR resource
 - displayText: String - The primary display text computed from the resource by CommonHealth 
 - secondaryDisplayText: String - The secondary display text computed from the resource by CommonHealth 

See `ResourceListFragment` for an example implementation of the data query building and data fetching process.

## Registering with CommonHealth

Registering with CommonHealth is not required to begin testing integrations with CommonHealth. However, if you have a client application that you would like to use in production environments, you'll need to register the application with CommonHealth. This is similar to registering an OAuth client, where you would specify information such as required scope, authorization redirect URI, etc. Please reach out to info [at] commonhealth.org for more information.