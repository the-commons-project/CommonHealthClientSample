# CommonHealth Client SDK

The CommonHealth Client SDK provides an interface that allows applications to access health data stored in CommonHealth.

The CommonHealth Client SDK is in open beta. 

If you're interested in developing a production integration with the CommonHealth Client SDK, please reach out to developers [at] commonhealth.org to begin the process of being whitelisted. 

While we consider the SDK to be relatively stable, this is pre-release software, so the interfaces are subject to change based on evolving requirements and developer feedback. We're currently investigating ways to make it easier to remove configuration dependencies, so if you find anything particularly burdensome or confusing, please let us know by either emailing us or opening a Github issue.

## Audience 
This quick start guide is geared towards participants in our closed beta program. This guide assumes that you have CommonHealth Developer Edition installed on your device and you have gone through the enrollment process in the application.

## CommonHealth Developer Edition

CommonHealth Developer Edition is a specialized version of CommonHealth specifically for developers working with the CommonHealth Client SDK. CommonHealth Developer Edition integrates with the [SMART® Health IT Sandbox](https://launch.smarthealthit.org) to provide access to sample patient data. CommonHealth Developer Edition also relaxes some security features which allows the application to be installed and run on an Android emulator (in addition to a physical Android device).

### Installing the CommonHealth Developer Edition

CommonHealth Developer Edition is currently made available via an open testing track in the Google Play Store. You can join and install the app via [this link](https://play.google.com/store/apps/details?id=org.thecommonsproject.android.phr.developer) on your Android device or [this link](https://play.google.com/apps/testing/org.thecommonsproject.android.phr.developer) on the web.

### Running the CommonHealth Developer Edition

Once CommonHealth Developer Edition is installed, you will need to add a sample patient account in order to populate the app with sample data. After selecting the SMART IT Sandbox from the list of avaialble data sources, you will redirected to the SMART IT Sandbox and be presented with an authentication screen. Enter the sample patient's `ID` (e.g., `099e7de7-c952-40e2-9b4e-0face78c9d80`, `smart-1288992`) in the `User Id` field. The `Password` field is not checked and can be anything. A full list of sample patients can be found [here](https://patient-browser.smarthealthit.org/index.html?config=r2).

## Configuration Requirements

### Gradle Dependencies

The CommonHealth Client SDK consists of two modules: commonhealth-client and commonhealth-common. CommonHealthClient contains the bulk of functionality for the SDK, while common contains types shared between the CommonHealth application and the CommonHealth Client SDK. You'll need to add the following to your application's list of dependencies:

```
implementation "org.thecommonsproject:commonhealth-common:1.6.2"
implementation "org.thecommonsproject:commonhealth-client:1.6.2"
```

The release artifacts are made avalable via the Maven Central repository, so you will need to have the following in your list of dependency repositories:

`mavenCentral()`

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

Our authorization process is inspired by OAuth and requires that the `RedirectUriReceiverActivity` receive a redirect from CommonHealth. Applications must define the `interappAuthRedirectScheme`, `interappAuthRedirectHost`, and `interappAuthRedirectPath` manifest placeholders in the app's `build.gradle` file, and the compiled URL must match the `authorizationCallbackUri` in the `CommonHealthStoreConfiguration` object (see below). For example, defining the following manifest placeholders in your `build.gradle` file:

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
val notificationPreferences = NotificationPreferences(
    subscribedNotificationTypes = setOf(
        CommonHealthNotificationType.AUTHORIZATION_FLOW_COMPLETED_WITH_RESULT
    ),
    subscriber = { notification ->
        when(notification) {
            is CommonHealthNotification.AuthorizationCompleted -> {
                when(val response = notification.userResponse) {
                    is CommonHealthAuthorizationActivityResponse.Failure -> { }
                    is CommonHealthAuthorizationActivityResponse.Success -> { }
                    is CommonHealthAuthorizationActivityResponse.UserCanceled -> { }
                }
            }
        }
    }
)
val configuration = CommonHealthStoreConfiguration(
    appId = BuildConfig.APPLICATION_ID,
    commonHealthAppId = "org.thecommonsproject.android.phr.developer",
    developerModeEnabled = true,
    attestationServiceConfiguration = null,
    commonHealthAuthorizationUri = "org.thecommonsproject.android.phr://interapp/auth",
    authorizationCallbackUri = "org.thecommonsproject.android.commonhealth.sampleapp://interapp/redirect",
    notificationPreferences = notificationPreferences
)
```

Note that the the `interappAuthRedirectScheme`, `interappAuthRedirectHost`, and `interappAuthRedirectPath` manifest placeholders must translate into the Uri specified by the `authorizationCallbackUri` parameter.

In order to store the keys required to secure the connection(s) with the CommonHealth application, the client application must also provide a secure implementation of the `NamespacedKeyValueStore` interface. This object persists the stored values across application launches. 

The `common` module provides a `NamespacedKeyValueStore` implementation that utilizes the Android Room architecture component. If you're not using Android Room for persistence, you can provide an implementation using the persistence method of your choosing. 

To help with security, the `common` module provides `SecureNamespacedKeyValueStore` to automatically encrypt and decrypt values. This class leverages the Google Tink encryption library and a key stored in the Android Keystore.

The `SampleApplication` class contains the following code to create a `SecureNamespacedKeyValueStore`:

```
val cryptoProvider = DefaultCryptoProvider(DefaultAndroidKeystoreClientWrapper())
val database = database ?: createDataBase(context, cryptoProvider)
val namespacedKeyValueStore = SecureNamespacedKeyValueStore(
    KeyValueLocalDataStore(database.keyValueEntryDao()),
    "secure_namespaced_key_value_store",
    cryptoProvider
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

The `CommonHealthStore` class provides the `getCommonHealthAvailability` method that can help determine how (or how not) to interact with CommonHealth:

```
val chStore = CommonHealthStore.getSharedInstance()
val chAvailability = chStore.getCommonHealthAvailability(context)
when(chAvailability) {
    CommonHealthAvailability.NOT_INSTALLED -> { } // show install UX
    CommonHealthAvailability.ACCOUNT_NOT_CONFIGURED_FOR_SHARING -> { } // this can indicate that a user has disabled sharing entirely or that their device is unsuitable for sharing (ex. device is rooted)
    CommonHealthAvailability.AVAILABLE -> { } // Suitable for authorization or querying if consent has been given
}
```

If CommonHealth is not installed, the CommonHealthStore also provides a convenience method to direct a user to Google Play to install it. It is invoked like so:

```
val chInstallIntent = chStore.buildCommonHealthInstallIntent(context)
startActivity(chInstallIntent)
```

### Define the scope of access

Client applications should follow the principle of least privilege and only request access to resources deemed truly necessary. The production version of CommonHealth requires that applications are registered and their scope of access does not exceed their registered values. During the development process, the developer version of CommonHealth does not perform this check.

Client applications must define a `ScopeRequest`, which encapsulates a set of `Scope` objects. Each `Scope` object **must** contain a data type (`DataType`) an access type (`Scope.Access`). If you would like to limit the scope of access to a defined set of codes, you may also provide a list of `ScopedCodeAllowListEntry` objects. `ScopeRequest` contains a `Builder` class to help with implementation.

The following sample creates a `ScopeRequest` object requesting read access to all currently available FHIR data types:

```
val allDataTypes: List<DataType.FHIRResource> = listOf(
   DataType.ClinicalResource.AllergyIntoleranceResource,
   DataType.ClinicalResource.ClinicalVitalsResource,
   DataType.ClinicalResource.ConditionsResource,
   DataType.ClinicalResource.ImmunizationsResource,
   DataType.ClinicalResource.LaboratoryResultsResource,
   DataType.ClinicalResource.MedicationResource,
   DataType.ClinicalResource.ProceduresResource,
   DataType.PayerResource.ExplanationOfBenefitResource,
   DataType.PayerResource.CoverageResource,
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

Authorization is performed by launching the `AuthorizationManagementActivity`. To do so, create an intent using the `AuthorizationManagementActivity` static method `createStartForResultIntent`, passing in an authorization request. You then start the activity using the `startActivity` like you typically would for any activity.

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

startActivity(authIntent)
```

This will initiate an authorization and consent flow in CommonHealth.

The authorization response--a success, cancellation, or error--is communicated back through the NotificationPreferences subscriber object registered in the CommonHealthStoreConfiguration bundle.

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

As you can see, the method returns a list of `SampleDataQueryResult` instances. For requests for FHIR data, these can be cast to `FHIRSampleDataQueryResult` instances. Each `FHIRSampleDataQueryResult` instance contains the following:

 - resourceType: DataType.FHIRResource - the type of the resource
 - json: String - JSON string representation of the FHIR resource
 - displayText: String - The primary display text computed from the resource by CommonHealth 
 - secondaryDisplayText: String - The secondary display text computed from the resource by CommonHealth
 - chId: UUID - A random, stable identifier for this given resource computed by CommonHealth
 - fhirVersion: String - The given FHIR version corresponding to this resource

See `ResourceListFragment` for an example implementation of the data query building and data fetching process.

### Receiving new data or updates to existing data

As part of the NotificationPreferences configuration in the CommonHealthStoreConfiguration, you can subscribe to various notification types:

 - CommonHealthNotificationType.AUTHORIZATION_FLOW_COMPLETED_WITH_RESULT - notification containing the authorization response / result
 - CommonHealthNotificationType.NEW_DATA_AVAILABLE - notification informing that new data is available

Upon receiving the NEW_DATA_AVAILABLE notification, you can invoke a method on the CommonHealthStore to receive a list of record updates over a given timeframe:

 ```
@Throws(InterappException::class)
 suspend fun getRecordUpdates(
    context: Context,
    connectionAlias: String,
    after: Date? = null,
    before: Date? = null
): List<RecordUpdateQueryResult> {
 ```

 The `after` and `before` optional parameters allow you specify an interval of time from which to receive a list of record update events. Each record update event contains:

 - CHRecordId: UUID - a stable, random, CH-generated unique identifier for a given record
 - updateType: RecordUpdateQueryResult.UpdateType - an enum containing UPDATE_OR_INSERT or DELETION cases
 - date: Date - timestamp for the given update

 Using these can help you identify when and if you need to pull data from CommonHealth, or if data has been deleted in CommonHealth and should be removed from your local datastore, if persisted.

### Using CMS Blue Button Sandbox -- new in v1.6.2 and currently in beta

_Note: This feature is currently in beta and should not yet be used in production applications. We'd love your feedback as you start using it; please open a Github issue, or email developers [at] commonhealth.org with any thoughts that you have. Thank you!_

Client applications can now request to read Insurance data from CommonHealth. This data flows similarly to the existing clinical data types.

Important note: CMS requires a client id and secret. [Register with CMS](https://sandbox.bluebutton.cms.gov/v1/accounts/create) to get a client id and secret.

Be sure to register your sandbox app with our redirect url: org.thecommonsproject.android.phr.developer://oauth/redirect

We can\'t use our own so you\'ll have to provide one. We will save this id and secret only locally, for your development convenience. We never send this client id/secret anywhere except to the authorization server for OAuth.

### Reading Verifiable Credentials (SMART® Health Cards) -- new in v1.3.15 and currently in beta 

_Note: This feature is currently in beta and should not yet be used in production applications. We'd love your feedback as you start using it; please open a Github issue, or email developers [at] commonhealth.org with any thoughts that you have. Thank you!_

Client applications can now request to read Verifiable Credentials (SMART® Health Cards) from CommonHealth. This is a completely separate, independent flow from requesting other types of health data. 

Client applications simply request access based on a set of Verifiable Credential types, and if the user consents to sharing any SMART® Health Cards that match the requested types, then the data will be returned immediately to the calling application. There is no persistent connection established, and the client application will have to request again (and the user consent again) to access the same data. 

Invoking the `readVerifiableCredentials` will redirect to CommonHealth, initiating a consent flow where the user can select which SHCs (or none) to share.

Here's a snippet demonstrating how the new `readVerifiableCredentials` method can be called to retrieve COVID-19 Vaccination SMART® Health Cards:

 ```
// Determine which VC types you'd like to read
// In this case, we're requesting COVID-19 Vaccination records.  
val c19VaxVcTypes = setOf(
    "https://smarthealth.cards#immunization",
    "https://smarthealth.cards#covid19",
    "https://smarthealth.cards#health-card"
)

// Pass the VC Types into the readVerifiableCredentials suspending method        
val smartHealthCards = commonHealthStore.readVerifiableCredentials(
    context,
    c19VaxVcTypes
)
 ```

CommonHealth performs SMART® Health Card validation prior to ingesting the cards into the app, including signature validation and checks against the payload contents. However, it is recommended to perform your own validation depending on your use case. The raw JWT can be accessed through the `json` property on each `VerifiableRecordSampleDataQueryResult` object.

## Registering with CommonHealth

Registering with CommonHealth is not required to begin testing integrations with CommonHealth Developer Edition. However, if you have a client application that you would like to use in staging or production environments, you'll need to register the application with CommonHealth. This is similar to registering an OAuth client, where you would specify information such as required scope, authorization redirect URI, etc. Please reach out to developers [at] commonhealth.org for more information.

## Upgrading from v1.3.15 to v1.6.2
`v1.6.12` introduced a small number of changes:

- Support for Insurance data through the CMS Blue Button 2.0 Sandbox. This will require you to register with CMS an app with a redirect url to CommonHealth, and then for you to provide the client id/secret locally into CommonHealth so that you can download the data

## Upgrading from v1.1.2 to v1.3.15
`v1.3.15` introduced a small number of changes:

- Support for requesting access to Verifiable Credentials (SMART® Health Cards) is now in beta
- Updates to support higher Android OS levels (specifically changes to accommodate new restrictions on apps calling into packageManager)

## Upgrading from v0.4.8 to v1.1.2
`v1.1.2` introduced a small number of changes:

 - The constructor for `SecureNamespacedKeyValueStore` now requires a `CryptoProvider` object.
 - The artifacts names have changed:
    - `org.thecommonsproject.commonhealth:common` is now `org.thecommonsproject:commonhealth-common`
    - `org.thecommonsproject.commonhealth:commonhealthclient` is now `org.thecommonsproject:commonhealth-client`
 - The artifacts are now hosted in Maven Central.

## Upgrading from v0.4.4 to v0.4.8
`v0.4.8` introduced a number of large changes and enhancements to the API:

 - CommonHealthStoreConfiguration now has a required NotificationPreferences property
 - AuthorizationResponses are now communicated as a notification to a subscriber contained in the CommonHealthStoreConfiguration
 - A new `getRecordUpdates` method was added to the CommonHealthStore to help determine changes to the data
 - A `NEW_DATA_AVAILABLE` notificationType is added to support receiving updates asynchronously from CommonHealth
 - The `isCommonHealthAvailable` method was removed in favor of the new `getCommonHealthAvailability`

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
