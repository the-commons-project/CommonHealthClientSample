package org.thecommonsproject.android.commonhealth.sampleapp.fragments

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.thecommonsproject.android.common.interapp.scope.DataType
import org.thecommonsproject.android.commonhealth.sampleapp.R

class DataTypeDialogueFragment(
    private val allDataTypes: List<DataType>,
    private val listener: Listener
): DialogFragment() {

    interface Listener {
        fun onDataTypeDialogPositiveClick(dialogFragment: DialogFragment, dataTypes: Set<DataType>)
        fun onDataTypeDialogNegativeClick(dialogFragment: DialogFragment)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val selectedTypes = ArrayList<DataType>()
            val dataTypeStrings: Array<String> = allDataTypes
                .map { resources.getString(it.descriptionStringRes) }.toTypedArray()

            val builder = AlertDialog.Builder(it)
            builder.setTitle(R.string.select_datatypes_prompt)
                .setMultiChoiceItems(dataTypeStrings, null) { _, which, isChecked ->
                    val dataType: DataType = allDataTypes[which]
                    if (isChecked) {
                        selectedTypes.add(dataType)
                    } else {
                        selectedTypes.remove(dataType)
                    }
                }
                .setPositiveButton(R.string.ok) { _, _ ->
                    listener.onDataTypeDialogPositiveClick(
                        this,
                        selectedTypes.toSet()
                    )
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    listener.onDataTypeDialogNegativeClick(
                        this
                    )
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}