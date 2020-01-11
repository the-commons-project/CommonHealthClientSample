package org.thecommonsproject.android.commonhealth.sampleapp.fragments


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import org.thecommonsproject.android.commonhealth.sampleapp.MainViewModel
import org.thecommonsproject.android.commonhealth.sampleapp.R
import org.thecommonsproject.android.commonhealth.sampleapp.getVmFactory

/**
 * A simple [Fragment] subclass.
 */
class FHIRDataFragment : Fragment() {

    private val viewModel by activityViewModels<MainViewModel> { getVmFactory() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fhir_data_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val safeArgs: FHIRDataFragmentArgs by navArgs()
        val resourceJson = safeArgs.resourceJson

        val textView = view.findViewById<TextView>(R.id.text_view)
        textView.text = viewModel.prettyPrintJSON(resourceJson)

    }


}
