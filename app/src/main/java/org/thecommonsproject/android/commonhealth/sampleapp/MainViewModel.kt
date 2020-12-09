package org.thecommonsproject.android.commonhealth.sampleapp

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import org.thecommonsproject.android.common.interapp.CommonHealthAuthorizationStatus
import org.thecommonsproject.android.common.interapp.dataquery.response.DataQueryResult
import org.thecommonsproject.android.common.interapp.dataquery.response.FHIRSampleDataQueryResult
import org.thecommonsproject.android.common.interapp.scope.DataType
import org.thecommonsproject.android.common.interapp.scope.Scope
import org.thecommonsproject.android.common.interapp.scope.ScopeRequest
import org.thecommonsproject.android.commonhealthclient.AuthorizationManagementActivity
import org.thecommonsproject.android.commonhealthclient.AuthorizationRequest
import org.thecommonsproject.android.commonhealthclient.CommonHealthAvailability
import org.thecommonsproject.android.commonhealthclient.CommonHealthStore
import timber.log.Timber
import java.util.*

class MainViewModel(
    private val commonHealthStore: CommonHealthStore
) : ViewModel() {

    private val connectionAlias = "connection_alias"
    private val TAG by lazy { MainViewModel::class.java.simpleName }

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

    sealed class ResultHolderMessage {
        class SetResults(val resourceType: DataType.ClinicalResource, val results: List<FHIRSampleDataQueryResult>) : ResultHolderMessage()
    }


    private var resultsMap: Map<DataType.ClinicalResource, List<FHIRSampleDataQueryResult>> = emptyMap()
    var resultsLiveData: MutableLiveData<Map<DataType.ClinicalResource, List<FHIRSampleDataQueryResult>>> = MutableLiveData(resultsMap)
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

    private suspend fun fetchData(context: Context, clinicalResource: DataType.ClinicalResource) : List<DataQueryResult>{
        return try {
            commonHealthStore.readSampleQuery(
                context,
                connectionAlias,
                setOf(clinicalResource)
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Exception fetching data: ", e)
            emptyList()
        }
    }

    suspend fun fetchAllData(context: Context) {


        val typesToFetch = allDataTypes
        val jobs = typesToFetch.map { clinicalResource ->

            CoroutineScope(Dispatchers.IO).launch {
                val results = fetchData(context, clinicalResource).mapNotNull { it as? FHIRSampleDataQueryResult }
                resultsHolderActor!!.send(
                    ResultHolderMessage.SetResults(
                        clinicalResource,
                        results
                    )
                )
            }

        }

        jobs.joinAll()
    }

    suspend fun getCommonHealthAvailability(context: Context): CommonHealthAvailability {
        return commonHealthStore.getCommonHealthAvailability(context)
    }

    fun prettyPrintJSON(jsonString: String): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val element = JsonParser.parseString(jsonString)
        return gson.toJson(element)
    }

}