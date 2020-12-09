package org.thecommonsproject.android.commonhealth.sampleapp.fragments


import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.thecommonsproject.android.common.interapp.CommonHealthAuthorizationStatus
import org.thecommonsproject.android.common.interapp.InterappException
import org.thecommonsproject.android.common.interapp.scope.DataType
import org.thecommonsproject.android.commonhealth.sampleapp.MainViewModel
import org.thecommonsproject.android.commonhealth.sampleapp.R
import org.thecommonsproject.android.commonhealth.sampleapp.getVmFactory
import org.thecommonsproject.android.commonhealthclient.AuthorizationManagementActivity
import org.thecommonsproject.android.commonhealthclient.CommonHealthAuthorizationActivityResponse
import org.thecommonsproject.android.commonhealthclient.CommonHealthAvailability

/**
 * A simple [Fragment] subclass.
 */
class CategoryListFragment : Fragment() {

    private val viewModel by activityViewModels<MainViewModel> { getVmFactory() }
    private lateinit var authorizeButton: Button
    private lateinit var spinner: ProgressBar

    companion object {
        private val CH_AUTH = 4096
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.category_list_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)

        val navController = findNavController()

        val generateOnClickListener: (DataType) -> View.OnClickListener = { dataType ->
            View.OnClickListener { view ->
                val options = navOptions {
                    anim {
                        enter = R.anim.slide_in_right
                        exit = R.anim.slide_out_left
                        popEnter = R.anim.slide_in_left
                        popExit = R.anim.slide_out_right
                    }
                }
                val title = requireContext().getString(dataType.descriptionStringRes)
                val directions = CategoryListFragmentDirections.actionCategoryListFragmentToResourceListFragment(
                    dataType,
                    title
                )
                navController.navigate(directions, options)
            }
        }

        val adapter = ContentAdapter(
            viewModel.allDataTypes,
            generateOnClickListener
        )

        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        authorizeButton = view.findViewById(R.id.authorize_button)
        authorizeButton.isEnabled = false
        authorizeButton.setOnClickListener {
            val authIntent = viewModel.generateAuthIntent(requireContext())
            startActivityForResult(authIntent, CH_AUTH)
        }

        spinner = view.findViewById(R.id.progress_bar)

        viewModel.resultsLiveData.observe(this) { resultsMap ->
            val resultsCounts = resultsMap.mapValues { it.value.count() }
            adapter.updateResultsCounts(resultsCounts)
        }

        updateUI()
    }

    private fun updateUI() {
        viewModel.viewModelScope.launch {
            when(viewModel.getCommonHealthAvailability(requireContext())) {
                CommonHealthAvailability.AVAILABLE -> { }
                CommonHealthAvailability.NOT_INSTALLED,
                CommonHealthAvailability.ACCOUNT_NOT_CONFIGURED_FOR_SHARING -> {
                    Toast.makeText(
                        requireContext(),
                        "Please make sure CommonHealth is installed and setup",
                        Toast.LENGTH_LONG
                    ).show()
                    authorizeButton.isEnabled = false
                    return@launch
                }
            }

            val authorizationStatus = viewModel.checkAuthorizationStatus(
                requireContext()
            )

            when(authorizationStatus) {
                CommonHealthAuthorizationStatus.unnecessary -> {
                    Toast.makeText(requireContext(), "Authorization not needed", Toast.LENGTH_LONG).show()
                    authorizeButton.isEnabled = false
                }
                CommonHealthAuthorizationStatus.shouldRequest -> {
                    authorizeButton.isEnabled = true
                }
                CommonHealthAuthorizationStatus.cannotAuthorize -> {
                    Toast.makeText(requireContext(), "Cannot Authorize", Toast.LENGTH_LONG).show()
                    authorizeButton.isEnabled = false
                }
                CommonHealthAuthorizationStatus.exceedsMaximumAllowedScope -> {
                    Toast.makeText(requireContext(), "Exceeds maximum allowed scope", Toast.LENGTH_LONG).show()
                    authorizeButton.isEnabled = false
                }
                CommonHealthAuthorizationStatus.connectionExpired -> {
                    authorizeButton.isEnabled = true
                }
                CommonHealthAuthorizationStatus.inactive -> {
                    Toast.makeText(requireContext(), "The application is inactive", Toast.LENGTH_LONG).show()
                    authorizeButton.isEnabled = false
                }
            }

            if (authorizationStatus == CommonHealthAuthorizationStatus.unnecessary) {
                viewModel.fetchAllData(requireContext())
            }
        }
    }

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

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    class CategoryListItemViewHolder(inflater: LayoutInflater, parent: ViewGroup): RecyclerView.ViewHolder(
        inflater.inflate(R.layout.category_list_item, parent, false)
    ) {
        val title = itemView.findViewById<TextView>(R.id.title_text_view)
        val countTextView = itemView.findViewById<TextView>(R.id.count_text_view)
        val actionButton = itemView.findViewById<ImageView>(R.id.action_button)
    }

    class ContentAdapter(
        private val categories: List<DataType.ClinicalResource>,
        private val generateOnClickListener: (DataType) -> View.OnClickListener
    ) : RecyclerView.Adapter<CategoryListItemViewHolder>() {

        var resultsCounts: Map<DataType.ClinicalResource, Int>? = null
        fun updateResultsCounts(newResultsCounts: Map<DataType.ClinicalResource, Int>) {
            resultsCounts = newResultsCounts
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryListItemViewHolder {
            return CategoryListItemViewHolder(LayoutInflater.from(parent.context), parent)
        }

        override fun getItemCount(): Int {
            return categories.count()
        }

        override fun onBindViewHolder(holder: CategoryListItemViewHolder, position: Int) {

            val category = categories[position]

            when (val resultsCounts = this.resultsCounts) {
                null -> holder.countTextView.text = ""
                else -> {
                    val resultsCount = resultsCounts[category] ?: 0
                    holder.countTextView.text = "$resultsCount"
                }
            }

            holder.title.setText(category.descriptionStringRes)

            holder.actionButton.visibility = View.VISIBLE
            holder.actionButton.setOnClickListener(
                generateOnClickListener(category)
            )


        }

    }
}
