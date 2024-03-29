package org.thecommonsproject.android.commonhealth.sampleapp

import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import org.thecommonsproject.android.common.interapp.CommonHealthAuthorizationStatus
import org.thecommonsproject.android.common.interapp.dataquery.cardtypes.InterappHealthCardType
import org.thecommonsproject.android.common.interapp.dataquery.response.DataQueryResult
import org.thecommonsproject.android.common.interapp.dataquery.response.FHIRSampleDataQueryResult
import org.thecommonsproject.android.common.interapp.dataquery.response.RecordUpdateQueryResult
import org.thecommonsproject.android.common.interapp.dataquery.response.SampleDataQueryResult
import org.thecommonsproject.android.common.interapp.dataquery.response.VerifiableRecordSampleDataQueryResult
import org.thecommonsproject.android.common.interapp.scope.DataType
import org.thecommonsproject.android.common.interapp.scope.Scope
import org.thecommonsproject.android.common.interapp.scope.ScopeRequest
import org.thecommonsproject.android.commonhealthclient.AuthorizationManagementActivity
import org.thecommonsproject.android.commonhealthclient.AuthorizationRequest
import org.thecommonsproject.android.commonhealthclient.CommonHealthAvailability
import org.thecommonsproject.android.commonhealthclient.CommonHealthStore
import timber.log.Timber

class MainViewModel(
    private val commonHealthStore: CommonHealthStore
) : ViewModel() {

    private val connectionAlias = "connection_alias"

    var storedVCResults = emptyList<VerifiableRecordSampleDataQueryResult>()

    val allDataTypes: List<DataType> = listOf(
        DataType.ClinicalResource.AllergyIntoleranceResource,
        DataType.ClinicalResource.ClinicalVitalsResource,
        DataType.ClinicalResource.ConditionsResource,
        DataType.ClinicalResource.ImmunizationsResource,
        DataType.ClinicalResource.LaboratoryResultsResource,
        DataType.ClinicalResource.MedicationResource,
        DataType.ClinicalResource.ProceduresResource,
        DataType.ClinicalResource.CarePlanResource,
        DataType.ClinicalResource.GoalResource,
        DataType.ClinicalResource.DocumentReferenceResource,
        DataType.PayerResource.CoverageResource,
        DataType.PayerResource.ExplanationOfBenefitResource,
        // Supported by SDK but no example data sources for developer usage
//        DataType.OMHealthResource.BloodPressure,
//        DataType.OMHealthResource.BloodGlucose,
//        DataType.OMHealthResource.HeartRate
    )

    val scopeRequest: ScopeRequest by lazy {
        val builder = ScopeRequest.Builder()
        allDataTypes.forEach {
            builder.add(it, Scope.Access.READ)
        }
        builder.build()
    }

    sealed class ResultHolderMessage {
        class SetResults(val resourceType: DataType.FHIRResource, val results: List<FHIRSampleDataQueryResult>) : ResultHolderMessage()
        class SetMhealthResults(val resourceType: DataType.OMHealthResource, val results: List<SampleDataQueryResult>) : ResultHolderMessage()
        class SetRecordUpdateResults(val results: List<RecordUpdateQueryResult>) : ResultHolderMessage()
    }

    private var resultsMap: Map<DataType, List<SampleDataQueryResult>> = emptyMap()
    var resultsLiveData: MutableLiveData<Map<DataType, List<SampleDataQueryResult>>> = MutableLiveData(resultsMap)
    val recordUpdatesLiveData = MutableLiveData<List<RecordUpdateQueryResult>>()

    // This function launches a new counter actor
    fun CoroutineScope.resultsHolderActor() = actor<ResultHolderMessage> {

        for (msg in channel) { // iterate over incoming messages
            when (msg) {
                is ResultHolderMessage.SetResults -> {
                    when (val existingResults = resultsMap[msg.resourceType]) {
                        null -> {}
                        else -> { assert(existingResults.count() == msg.results.count()) }
                    }

                    resultsMap = resultsMap.plus(Pair(msg.resourceType, msg.results))
                    resultsLiveData.postValue(resultsMap)
                }
                is ResultHolderMessage.SetMhealthResults -> {
                    when (val existingResults = resultsMap[msg.resourceType]) {
                        null -> {}
                        else -> { assert(existingResults.count() == msg.results.count()) }
                    }

                    resultsMap = resultsMap.plus(Pair(msg.resourceType, msg.results))
                    resultsLiveData.postValue(resultsMap)
                }
                is ResultHolderMessage.SetRecordUpdateResults -> {
                    recordUpdatesLiveData.postValue(msg.results)
                }
            }
        }
    }

    var resultsHolderActor: SendChannel<ResultHolderMessage>? = null
    init {
        viewModelScope.launch {
            this@MainViewModel.resultsHolderActor = resultsHolderActor()
        }
    }

    suspend fun checkAuthorizationStatus(
        context: Context
    ) : CommonHealthAuthorizationStatus {
        return try {
            commonHealthStore.checkAuthorizationStatus(
                context,
                connectionAlias,
                scopeRequest
            )
        } catch (e: Exception) {
            CommonHealthAuthorizationStatus.cannotAuthorize
        }
    }

    fun generateAuthIntent(
        context: Context
    ) : Intent {
        val authorizationRequest = AuthorizationRequest(
            connectionAlias,
            scopeRequest,
            false,
            "Sample app would like to read your labs, vitals, and conditions."
        )

        return AuthorizationManagementActivity.createStartForResultIntent(
            context,
            authorizationRequest
        )
    }

    private suspend fun fetchFHIRData(context: Context, dataType: DataType.FHIRResource) : List<DataQueryResult>{
        return try {
            commonHealthStore.readSampleQuery(
                context,
                connectionAlias,
                setOf(dataType),
                Pair(null, null)
            )
        } catch (e: Throwable) {
            Timber.e( e, "Exception fetching data")
            emptyList()
        }
    }

    private suspend fun fetchMHealthData(context: Context, dataType: DataType.OMHealthResource) : List<SampleDataQueryResult>{
        return try {
            commonHealthStore.readMHealthSampleQuery(
                context,
                connectionAlias,
                setOf(dataType),
                Pair(null, null)
            )
        } catch (e: Throwable) {
            Timber.e( e, "Exception fetching data")
            emptyList()
        }
    }

    private suspend fun fetchRecordUpdates(context: Context) : List<RecordUpdateQueryResult> {
        return try {
            commonHealthStore.getRecordUpdates(
                context,
                connectionAlias
            )
        } catch (e: Throwable) {
            Timber.e(e, "Exception fetching record updates")
            emptyList()
        }
    }

    suspend fun fetchAllData(context: Context) {
        val mHealthTypes = allDataTypes.filterIsInstance<DataType.OMHealthResource>()
        val fhirTypes = allDataTypes.filterIsInstance<DataType.FHIRResource>()

        val sampleFHIRQueryFetchJobs = fhirTypes.map { fhirType ->
            CoroutineScope(Dispatchers.IO).launch {
                val results = fetchFHIRData(context, fhirType).mapNotNull { it as? FHIRSampleDataQueryResult }
                resultsHolderActor!!.send(
                    ResultHolderMessage.SetResults(
                        fhirType,
                        results
                    )
                )
            }
        }

        val recordUpdatesFetchJob = CoroutineScope(Dispatchers.IO).launch {
            val recordUpdates = fetchRecordUpdates(context)
            resultsHolderActor!!.send(
                ResultHolderMessage.SetRecordUpdateResults(recordUpdates)
            )
        }

        (listOf(recordUpdatesFetchJob) + sampleFHIRQueryFetchJobs).joinAll()
    }

    suspend fun getCommonHealthAvailability(context: Context): CommonHealthAvailability {
        return commonHealthStore.getCommonHealthAvailability(context)
    }

    suspend fun fetchHealthCards(context: Context): List<VerifiableRecordSampleDataQueryResult> {
        val results = commonHealthStore.readVerifiableCredentials(
            context,
            setOf(
                InterappHealthCardType.SHL_IPS,
                InterappHealthCardType.SHL_PAYER,
                InterappHealthCardType.SHC
            )
        )
        storedVCResults = results
        return results
    }

    fun prettyPrintJSON(jsonString: String): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val element = JsonParser.parseString(jsonString)
        return gson.toJson(element)
    }

}