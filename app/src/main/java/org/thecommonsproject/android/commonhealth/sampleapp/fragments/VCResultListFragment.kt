package org.thecommonsproject.android.commonhealth.sampleapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thecommonsproject.android.common.interapp.dataquery.response.VerifiableRecordSampleDataQueryResult
import org.thecommonsproject.android.commonhealth.sampleapp.MainViewModel
import org.thecommonsproject.android.commonhealth.sampleapp.R
import org.thecommonsproject.android.commonhealth.sampleapp.getVmFactory
import java.text.SimpleDateFormat

class VCResultListFragment : Fragment() {

    companion object {
        private fun buildRecyclerViewData(vcResults: List<VerifiableRecordSampleDataQueryResult>): List<RowIdentifier> {
            return vcResults.map {
                RowIdentifier.VCResultRow(it)
            }
        }
    }

    private val viewModel by activityViewModels<MainViewModel> { getVmFactory() }

    private lateinit var adapter: ContentAdapter
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.resource_list_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)

        adapter = ContentAdapter(
            buildRecyclerViewData(viewModel.storedVCResults)
        )
        viewModel.storedVCResults = emptyList()

        recyclerView.adapter = adapter

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        progressBar = view.findViewById(R.id.progress_bar)
        progressBar.visibility = View.GONE

        updateUI()
    }

    private fun updateUI() {
        adapter.notifyDataSetChanged()
    }

    class VCResultListItemViewHolder(inflater: LayoutInflater, parent: ViewGroup) : RecyclerView.ViewHolder(
        inflater.inflate(R.layout.vc_result_list_item, parent, false)
    ) {
        val issuer = itemView.findViewById<TextView>(R.id.issuer)
        val issuedDate = itemView.findViewById<TextView>(R.id.issuedDate)
    }

    private class ContentAdapter(
        private val rows: List<RowIdentifier>
    ) : RecyclerView.Adapter<VCResultListItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VCResultListItemViewHolder {
            return VCResultListItemViewHolder(LayoutInflater.from(parent.context), parent)
        }

        override fun getItemCount(): Int {
            return rows.count()
        }

        override fun onBindViewHolder(holder: VCResultListItemViewHolder, position: Int) {
            val row = rows[position]

            when (row) {
                is RowIdentifier.VCResultRow -> {
                    holder.issuer.text = row.vcResult.issuerIdentifier
                    holder.issuedDate.text = SimpleDateFormat.getDateTimeInstance().format(row.vcResult.issuedDate)
                }
            }
        }
    }

    private sealed class RowIdentifier {
        data class VCResultRow(val vcResult: VerifiableRecordSampleDataQueryResult) : RowIdentifier()
    }
}