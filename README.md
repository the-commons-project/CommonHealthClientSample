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
implementation "org.thecommonsproject.commonhealth:common:0.4.4"
implementation "org.thecommonsproject.commonhealth:commonhealthclient:0.4.4"
```

The artifacts currently reside in our organization's bintray repo, but at some point these will be migrated to jcenter. In the mean time, you'll need to add the following maven repository to your list of repositories, typically defined in the project's `gradle.build` file:

`maven { url "https://dl.bintray.com/thecommonsproject/CommonHealth" }`

Additionally, some dependency artifacts are served by Jitpack, so you will also need to add the following maven repository:

`maven { url "https://jitpack.io" }`

### Manifest Placeholders

The interapplication data sharing functionality of CommonHealth leverages native Android communication components. The CommonHealth Client SDK defines the following activities, services, and receivers in its `manifest.xml` file, which will be merged into the application's manifest during the build process:

```
<activity android:name=".AuthorizationManagementActivity"
        android:exported="false"
        android:theme="@style/Theme.AppCompat.NoActionBar"
        android:launchMode="singleTask" />

<activity android:name=".RedirectUriReceiverActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data
                android:host="${interappAuthRedirectHost}"
                android:pathPrefix="${interappAuthRedirectPath}"
                android:scheme="${interappAuthRedirectScheme}" />
    </intent-filter>
</activity>

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
```

Our authorziation process is inspired by OAuth and requires that the `RedirectUriReceiverActivity` receive a redirect from CommonHealth. Applications must define the `interappAuthRedirectScheme`, `interappAuthRedirectHost`, and `interappAuthRedirectPath` manifest placeholders in the app's `build.gradle` file, and the compiled URL must match the `authorizationCallbackUri` in the `CommonHealthStoreConfiguration` object (see below). For example, defining the following manifest placeholders in your `build.gradle` file:

```
manifestPlaceholders.interappAuthRedirectScheme = "org.thecommonsproject.android.commonhealth.sampleapp"
manifestPlaceholders.interappAuthRedirectHost = "interapp"
manifestPlaceholders.interappAuthRedirectPath = "/redirect"
```

Translates to `org.thecommonsproject.android.commonhealth.sampleapp://interapp/redirect`

## Initialization 

### CommonHealthStore Configuration

The client application must initialize the `CommonHealthStore` singleton by providing a `Application`, `CommonHealthStoreConfiguration` object, and an object that implements the `NamespacedKeyValueStore` interface. 

For development, you can use the following to create the `CommonHealthStoreConfiguration` object:

```
val configuration = CommonHealthStoreConfiguration(
    appId = BuildConfig.APPLICATION_ID,
    commonHealthAppId = "org.thecommonsproject.android.phr.developer",
    developerModeEnabled = true,
    attestationServiceConfiguration = null,
    commonHealthAuthorizationUri = "org.thecommonsproject.android.phr://interapp/auth",
    authorizationCallbackUri = "org.thecommonsproject.android.commonhealth.sampleapp://interapp/redirect"
)
```

Note that the the `interappAuthRedirectScheme`, `interappAuthRedirectHost`, and `interappAuthRedirectPath` manifest placeholders must translate into the Uri specified by the `authorizationCallbackUri` parameter.

In order to store the keys required to secure the connection(s) with the CommonHealth application, the client application must also provide a secure implementation of the `NamespacedKeyValueStore` interface. This object persists the stored values across application launches. 

The `common` module provides a `NamespacedKeyValueStore` implementation that utilizes the Android Room architecture component. If you're not using Android Room for persistence, you can provide an implementation using the persistence method of your choosing. 

To help with security, the `common` module provides `SecureNamespacedKeyValueStore` to automatically encrypt and decrypt values. This class leverages the Google Tink encryption library and a key stored in the Android Keystore.

The `SampleApplication` class contains the following code to create a `SecureNamespacedKeyValueStore`:

```
val database = database ?: createDataBase(context)
val namespacedKeyValueStore = SecureNamespacedKeyValueStore(
    KeyValueLocalDataStore(database.keyValueEntryDao()),
    "secure_namespaced_key_value_store"
)
```

Note that `database` is an AndroidRoom database that contains the `KeyValueEntry` entity and implements the `KeyValueEntryDao` DAO. Both of these classes can be found in the `common` module.

## Usage

Once installation and configuration is taken care of, usage of the CommonHealth Client SDK is pretty straightforward. The following outlines the steps client applications will need to take in order to fetch data from CommonHealth:

1) Check that CommonHealth is available
2) Define the scope of access
3) Check the authorization status
4) Authorize
5) Fetch data

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

Client applications must define a `ScopeRequest`, which encapsulates a set of `Scope` objects. Each `Scope` object **must** contain a data type (`DataType`) an access type (`Scope.Access`). If you would like to limit the scope of access to a defined set of codes, you may also provide a list of `ScopedCodeAllowListEntry` objects. `ScopeRequest` contains a `Builder` class to help with implementation.

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

The following sample creates a `ScopeRequest` object requesting read access to lab results coded with LOINC codes `2339-0` and `49765-1`:

```
 val scopeRequestByCodes: ScopeRequest = ScopeRequest.Builder()
    .add(
        Scope(
            DataType.ClinicalResource.LaboratoryResultsResource, 
            Scope.Access.READ, 
            listOf(
                ScopedCodeAllowListEntry(
                    codingSystem = "http://loinc.org",
                    codes = listOf("2339-0", "49765-1")
                )
            )
        )
    ).build()
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

The `CommonHealthAuthorizationStatus` enum defines the following values:

 - `shouldRequest`
 - `unnecessary`
 - `cannotAuthorize`
 - `exceedsMaximumAllowedScope`
 - `connectionExpired`
 - `inactive`

The `shouldRequest` value means that the application needs to request authorization in order to query resources specified in the scope request.

The `unnecessary` value means that authorization has already been performed for the current scope request and the client application is able to fetch data from CommonHealth.

The `cannotAuthorize` value means that either the CommonHealth app is not available OR in production environments, the CommonHealth application is in safe mode and the client application is not registered with CommonHealth.

The `exceedsMaximumAllowedScope` value means that in production environments, the CommonHealth application is in safe mode and the requested scope exceeds the scope defined in your CommonHealth registration.

The `connectionExpired` value means that connection lifetime (90 days) has expired. You may request authorization again. The user may also renew the connection from within CommonHealth.

The `inactive` value indicates that the CommonHealth team has deactivated your application across all users. Please contact CommonHealth support for more information.

### Authorize

Authorization is performed by launching the `AuthorizationManagementActivity`. To do so, create an intent using the `AuthorizationManagementActivity` static method `createStartForResultIntent`, passing in an authorization request. You then start the activity using the `startActivityForResult` like you typically would for any activity. Note that you will also need to implement the `onActivityResult` method of the calling Activity or Fragment to get the status when `AuthorizationManagementActivity` finishes. `CommonHealthAuthorizationActivityResponse.fromActivityResult` can be used to convert the response into a more developer friendly object.

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
        CH_AUTH -> {

            //process response
            when(val response = CommonHealthAuthorizationActivityResponse.fromActivityResult(resultCode, data)) {
                null -> super.onActivityResult(requestCode, resultCode, data)
                is CommonHealthAuthorizationActivityResponse.Success -> {
                    Toast.makeText(context, "Authorization Succeeded", Toast.LENGTH_SHORT).show()
                }
                is CommonHealthAuthorizationActivityResponse.UserCanceled -> {
                    Toast.makeText(context, "User Canceled", Toast.LENGTH_SHORT).show()
                }
                is CommonHealthAuthorizationActivityResponse.Failure -> {
                    val errorMessage = response.errorMessage ?: "Authorization Failed"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()

                    // Optionally take additional action based on exception
                    when(response.exception) {
                        is InterappException.ClientApplicationValidationFailed -> { }
                        is InterappException.AuthError -> { }
                        else -> { }
                    }
                }
            }

            updateUI()
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

### Fetch data

In order to fetch data, the SDK provides the `readSampleQuery` method on the `CommonHealthStore`. The interface for this method is defined below:

```
suspend fun readSampleQuery(
    context: Context,
    connectionAlias: String,
    dataTypes: Set<DataType>,
    before: Date? = null,
    after: Date? = null,
    fetchedAfter: Date? = null
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

## Upgrading from v0.4.0 to v0.4.4
`v0.4.4` introduced a small number of changes to the API:

 - Scope requests can now be limited to a specific set of codes within a data type.
 - The `inactive` value was added to the `CommonHealthAuthorizationStatus` enum.
 - The optional `fetchedAfter` parameter was added to the `readSampleQuery` interface method. This parameter can be used to limit responses to resources added or updated after a certain date.

Additionally, the following maven repo is now required:

    maven { url "https://jitpack.io" }

## Upgrading from v0.3.0 to v0.4.0
No functional changes to the API were introduced in `v0.4.0`.

## Upgrading from v0.2.0 to v0.3.0
`v0.3.0` introduced a number of changes:

 - Introduced the `CommonHealthAuthorizationActivityResponse` class to assist with decoding responses from `AuthorizationManagementActivity`.
 - Connections now timeout after 90 days. After that, the client application will need to request authorization again OR the user can also renew the connection via client application detail view in the CommonHealth application. If the connection has timed out, the `checkAuthorizationStatus` method will return `CommonHealthAuthorizationStatus.connectionExpired`.
 - The `readSampleQuery` performs additional validation on `before` and `after` parameters.
 - Public `CommonHealthStore` methods have been annotated to indicate that they throw.

## Upgrading from v0.1.0 to v0.2.0

`v0.2.0` introduced a number of changes, some of them breaking.

 - Introduced `AuthorizationManagementActivity.USER_CANCELED` activity status code that is returned to the initiating activity in the case that the user cancels authorization.
 - Removed requirement that applications perform initialization on the `Tink` and `AndroidThreeTen` libraries. This also results in removing application build dependencies on these libraries.
 - `CommonHealthStoreConfiguration.Builder` class has been removed. Applications should create instances of this class directly. 
 - Removed the `CommonHealthStoreProvider` interface and dependency on the custom Application implementation to adopt the interface. Implementers should instead initialize the `CommonHealthStore` singleton via the `CommonHealthStore.initialize` static method. The `CommonHealthStore.getSharedInstance` static method is provided for accessing the `CommonHealthStore` singleton.
 - Removed the requirement for applications to include SDK specific `service`, `broadcastreciever`, and `activity` components in the application manifest. These have been moved to the SDK manifest file and will be merged in during the build process. We've added a requirement that applications define the `interappAuthRedirectScheme`, `interappAuthRedirectHost`, and `interappAuthRedirectPath` manifest placeholders in the app's `build.gradle` file.
 - Replaced the `CommonHealthStore` `executeDataQuery` method with `readSampleQuery`. 