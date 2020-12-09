package org.thecommonsproject.android.commonhealth.sampleapp.fragments


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thecommonsproject.android.common.interapp.dataquery.response.FHIRSampleDataQueryResult
import org.thecommonsproject.android.common.interapp.scope.DataType
import org.thecommonsproject.android.commonhealth.sampleapp.MainViewModel
import org.thecommonsproject.android.commonhealth.sampleapp.R
import org.thecommonsproject.android.commonhealth.sampleapp.getVmFactory

/**
 * A simple [Fragment] subclass.
 */
class ResourceListFragment : Fragment() {

    private val viewModel by activityViewModels<MainViewModel> { getVmFactory() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.resource_list_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val safeArgs: ResourceListFragmentArgs by navArgs()
        val dataType = (safeArgs.dataType as? DataType.ClinicalResource) ?: run {
            throw Exception("Unsupported data type")
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)

        val navController = findNavController()

        val generateOnClickListener: (FHIRSampleDataQueryResult) -> View.OnClickListener = { result ->
            View.OnClickListener { view ->
                //prefetch based on selected recordSummaryViewType
//                dashboardViewModel.prefetchResourceSummariesForType(
//                    dataGroupSummary,
//                    summaryViewType
//                )

                val options = navOptions {
                    anim {
                        enter = R.anim.slide_in_right
                        exit = R.anim.slide_out_left
                        popEnter = R.anim.slide_in_left
                        popExit = R.anim.slide_out_right
                    }
                }

                val directions = ResourceListFragmentDirections.actionResourceListFragmentToFHIRDataFragment(
                    result.json
                )

                navController.navigate(directions, options)
            }
        }

        val adapter = ContentAdapter(
            generateOnClickListener
        )

        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        progressBar.visibility = View.GONE

        viewModel.resultsLiveData.observe(this) { resultsMap ->
            resultsMap[dataType]?.let {
                adapter.updateResources(it)
            }
        }
    }

    class ResourceListItemViewHolder(inflater: LayoutInflater, parent: ViewGroup): RecyclerView.ViewHolder(
        inflater.inflate(R.layout.resource_list_item, parent, false)
    ) {
        val title = itemView.findViewById<TextView>(R.id.title_text_view)
        val subtitle = itemView.findViewById<TextView>(R.id.subtitle_text_view)
        val actionButton = itemView.findViewById<ImageView>(R.id.action_button)
    }

    class ContentAdapter(
        private val generateOnClickListener: (FHIRSampleDataQueryResult) -> View.OnClickListener
    ) : RecyclerView.Adapter<ResourceListItemViewHolder>() {

        private var resources: List<FHIRSampleDataQueryResult> = emptyList()
        fun updateResources(newResources: List<FHIRSampleDataQueryResult>) {
            resources = newResources
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResourceListItemViewHolder {
            return ResourceListItemViewHolder(LayoutInflater.from(parent.context), parent)
        }

        override fun getItemCount(): Int {
            return resources.count()
        }

        override fun onBindViewHolder(holder: ResourceListItemViewHolder, position: Int) {

            val resource = resources[position]

            holder.title.text = resource.displayText
            holder.subtitle.text = resource.secondaryDisplayText
            holder.actionButton.visibility = View.VISIBLE
            holder.actionButton.setOnClickListener(
                generateOnClickListener(resource)
            )
        }

    }


}
