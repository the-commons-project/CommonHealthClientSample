package org.thecommonsproject.android.commonhealth.sampleapp.fragments


import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.thecommonsproject.android.common.interapp.CommonHealthAuthorizationStatus
import org.thecommonsproject.android.common.interapp.InterappException
import org.thecommonsproject.android.common.interapp.scope.DataType
import org.thecommonsproject.android.common.interapp.scope.ScopeRequest
import org.thecommonsproject.android.commonhealth.sampleapp.MainViewModel
import org.thecommonsproject.android.commonhealth.sampleapp.R
import org.thecommonsproject.android.commonhealth.sampleapp.getVmFactory
import org.thecommonsproject.android.commonhealthclient.AuthorizationManagementActivity

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

        updateUI()
    }

    private fun updateUI() {
        viewModel.viewModelScope.launch {
            if (!viewModel.isCommonHealthAvailable(requireContext())) {
                Toast.makeText(requireContext(), "Please make sure CommonHealth is installed and setup", Toast.LENGTH_LONG).show()
                authorizeButton.isEnabled = false
                return@launch
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
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when(requestCode) {
            CH_AUTH -> {

                when(resultCode) {
                    AuthorizationManagementActivity.SUCCESS -> {
                        Toast.makeText(context, "Authorization Succeeded", Toast.LENGTH_SHORT).show()
                        authorizeButton.isEnabled = false
                    }
                    AuthorizationManagementActivity.FAILURE -> {

                        when (val exception = data?.getSerializableExtra(AuthorizationManagementActivity.EXTRA_AUTH_FAILURE_EXCEPTION)) {
                            is InterappException.ClientApplicationValidationFailed -> {
                                val message = exception.message ?: "Authorization Failed"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                            else -> Toast.makeText(context, "Authorization Failed", Toast.LENGTH_SHORT).show()
                        }

                    }
                    else -> {
                        Toast.makeText(context, "Authorization Not Determined", Toast.LENGTH_SHORT).show()
                    }
                }
                //process result
                updateUI()
                return
            }
            else -> {
                updateUI()
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    class CategoryListItemViewHolder(inflater: LayoutInflater, parent: ViewGroup): RecyclerView.ViewHolder(
        inflater.inflate(R.layout.category_list_item, parent, false)
    ) {
        val title = itemView.findViewById<TextView>(R.id.title_text_view)
        val actionButton = itemView.findViewById<ImageView>(R.id.action_button)
    }

    class ContentAdapter(
        private val categories: List<DataType>,
        private val generateOnClickListener: (DataType) -> View.OnClickListener
    ) : RecyclerView.Adapter<CategoryListItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryListItemViewHolder {
            return CategoryListItemViewHolder(LayoutInflater.from(parent.context), parent)
        }

        override fun getItemCount(): Int {
            return categories.count()
        }

        override fun onBindViewHolder(holder: CategoryListItemViewHolder, position: Int) {

            val category = categories[position]

            holder.title.setText(category.descriptionStringRes)
            holder.actionButton.visibility = View.VISIBLE
            holder.actionButton.setOnClickListener(
                generateOnClickListener(category)
            )
        }

    }
}
