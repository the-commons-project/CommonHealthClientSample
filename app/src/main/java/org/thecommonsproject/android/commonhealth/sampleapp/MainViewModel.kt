package org.thecommonsproject.android.commonhealth.sampleapp

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.thecommonsproject.android.common.interapp.CommonHealthAuthorizationStatus
import org.thecommonsproject.android.common.interapp.dataquery.request.DataQuery
import org.thecommonsproject.android.common.interapp.dataquery.response.DataQueryResult
import org.thecommonsproject.android.common.interapp.scope.DataType
import org.thecommonsproject.android.common.interapp.scope.Scope
import org.thecommonsproject.android.common.interapp.scope.ScopeRequest
import org.thecommonsproject.android.commonhealth.sampleapp.fragments.DataTypeDialogueFragment
import org.thecommonsproject.android.commonhealthclient.AuthorizationManagementActivity
import org.thecommonsproject.android.commonhealthclient.AuthorizationRequest
import org.thecommonsproject.android.commonhealthclient.CommonHealthStore

class MainViewModel(
    private val commonHealthStore: CommonHealthStore
) : ViewModel(), DataTypeDialogueFragment.Listener {

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

    val scopeRequest = MutableLiveData<ScopeRequest?>()

    suspend fun checkAuthorizationStatus(
        context: Context,
        scopeRequest: ScopeRequest
    ) : CommonHealthAuthorizationStatus {
        return commonHealthStore.checkAuthorizationStatus(
            context,
            connectionAlias,
            scopeRequest
        )
    }

    fun generateAuthIntent(
        context: Context,
        scopeRequest: ScopeRequest
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

    suspend fun fetchData(context: Context, clinicalResource: DataType.ClinicalResource) : List<DataQueryResult>{
        val query = DataQuery.Builder()
            .withDataTypes(setOf(clinicalResource))
            .withAction(DataQuery.Action.read)
            .build()

        try {
            return commonHealthStore.executeDataQuery(context, query, connectionAlias)
        } catch (e: Throwable) {
            Log.w(TAG, "Exception fetching data: ", e)
        }

        return emptyList()
    }

    suspend fun isCommonHealthAvailable(context: Context): Boolean {
        return commonHealthStore.isCommonHealthAvailable(context)
    }

    fun prettyPrintJSON(jsonString: String): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val element = JsonParser.parseString(jsonString)
        return gson.toJson(element)
    }

    fun resetScopeRequest() {
        scopeRequest.postValue(null)
    }

    override fun onDataTypeDialogPositiveClick(dialogFragment: DialogFragment, dataTypes: Set<DataType>) {
        val newRequest = ScopeRequest.Builder()
            .apply {
                dataTypes.forEach { dataType ->
                    this.add(Scope(dataType, Scope.Access.READ))
                }
            }
            .build()

        scopeRequest.postValue(newRequest)
    }

    override fun onDataTypeDialogNegativeClick(dialogFragment: DialogFragment) {
        scopeRequest.postValue(null)
    }

}