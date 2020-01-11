package org.thecommonsproject.android.commonhealth.sampleapp

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thecommonsproject.android.commonhealthclient.CommonHealthStore
import org.thecommonsproject.android.commonhealthclient.InterappServiceClient

@Suppress("UNCHECKED_CAST")
class ViewModelFactory constructor(
    private val commonHealthStore: CommonHealthStore
) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>) =
        with(modelClass) {
            when {
                isAssignableFrom(MainViewModel::class.java) ->
                    MainViewModel(
                        commonHealthStore
                    )
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        } as T
}

fun Activity.getVmFactory(): ViewModelFactory {
    val commonHealthStore = (applicationContext as SampleApplication).provideCommonHealthStore()
    return ViewModelFactory(
        commonHealthStore
    )
}

fun Fragment.getVmFactory(): ViewModelFactory {
    val context = requireContext().applicationContext
    val commonHealthStore = (context as SampleApplication).provideCommonHealthStore()
    return ViewModelFactory(
        commonHealthStore
    )
}