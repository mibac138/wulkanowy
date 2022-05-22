package io.github.wulkanowy.ui.modules.schoolannouncement

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.parseAsHtml
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.wulkanowy.R
import io.github.wulkanowy.data.Resource
import io.github.wulkanowy.data.db.entities.SchoolAnnouncement
import io.github.wulkanowy.data.logResourceStatus
import io.github.wulkanowy.data.onResourceSuccess
import io.github.wulkanowy.data.repositories.SchoolAnnouncementRepository
import io.github.wulkanowy.data.repositories.StudentRepository
import io.github.wulkanowy.databinding.FragmentSchoolAnnouncementBinding
import io.github.wulkanowy.databinding.FragmentSchoolAnnouncementComposeBinding
import io.github.wulkanowy.ui.base.BaseFragment
import io.github.wulkanowy.ui.modules.main.MainActivity
import io.github.wulkanowy.ui.modules.main.MainView
import io.github.wulkanowy.ui.widgets.DividerItemDecoration
import io.github.wulkanowy.utils.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

@AndroidEntryPoint
class SchoolAnnouncementFragment :
    BaseFragment<FragmentSchoolAnnouncementBinding>(R.layout.fragment_school_announcement),
    SchoolAnnouncementView, MainView.TitledView {

    @Inject
    lateinit var presenter: SchoolAnnouncementPresenter

    @Inject
    lateinit var schoolAnnouncementAdapter: SchoolAnnouncementAdapter

    companion object {
        fun newInstance() = SchoolAnnouncementFragment()
    }

    override val titleStringId: Int
        get() = R.string.school_announcement_title

    override val isViewEmpty: Boolean
        get() = schoolAnnouncementAdapter.items.isEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSchoolAnnouncementBinding.bind(view)
        presenter.onAttachView(this)
    }

    override fun initView() {
        with(binding.directorInformationRecycler) {
            layoutManager = LinearLayoutManager(context)
            adapter = schoolAnnouncementAdapter.apply {
                onItemClickListener = presenter::onItemClickListener
            }
            addItemDecoration(DividerItemDecoration(context))
        }
        with(binding) {
            directorInformationSwipe.setOnRefreshListener(presenter::onSwipeRefresh)
            directorInformationSwipe.setColorSchemeColors(requireContext().getThemeAttrColor(R.attr.colorPrimary))
            directorInformationSwipe.setProgressBackgroundColorSchemeColor(
                requireContext().getThemeAttrColor(R.attr.colorSwipeRefresh)
            )
            directorInformationErrorRetry.setOnClickListener { presenter.onRetry() }
            directorInformationErrorDetails.setOnClickListener { presenter.onDetailsClick() }
        }
    }

    override fun updateData(data: List<SchoolAnnouncement>) {
        with(schoolAnnouncementAdapter) {
            items = data
            notifyDataSetChanged()
        }
    }

    override fun clearData() {
        with(schoolAnnouncementAdapter) {
            items = listOf()
            notifyDataSetChanged()
        }
    }

    override fun showEmpty(show: Boolean) {
        binding.directorInformationEmpty.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun showErrorView(show: Boolean) {
        binding.directorInformationError.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun setErrorDetails(message: String) {
        binding.directorInformationErrorMessage.text = message
    }

    override fun showProgress(show: Boolean) {
        binding.directorInformationProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun enableSwipe(enable: Boolean) {
        binding.directorInformationSwipe.isEnabled = enable
    }

    override fun showContent(show: Boolean) {
        binding.directorInformationRecycler.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun showRefresh(show: Boolean) {
        binding.directorInformationSwipe.isRefreshing = show
    }

    override fun openSchoolAnnouncementDialog(item: SchoolAnnouncement) {
        (activity as? MainActivity)?.showDialogFragment(SchoolAnnouncementDialog.newInstance(item))
    }

    override fun onDestroyView() {
        presenter.onDetachView()
        super.onDestroyView()
    }
}

@HiltViewModel
class SchoolAnnouncementViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val schoolAnnouncementRepository: SchoolAnnouncementRepository,
    private val analytics: AnalyticsHelper,
) : ViewModel() {
    private val itemsTrigger = FlowTrigger()
    val items = flatFlowWithTrigger(itemsTrigger) { isManuallyTriggered ->
        val student = studentRepository.getCurrentStudent()
        schoolAnnouncementRepository.getSchoolAnnouncements(student, isManuallyTriggered)
    }.logResourceStatus("load school announcement").onResourceSuccess {
        analytics.logEvent(
            "load_school_announcement", "items" to it.size
        )
    }.catch { emit(Resource.Error(it)) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    fun refresh() {
        itemsTrigger.trigger(emitLoading = true)
    }
}

@AndroidEntryPoint
class SchoolAnnouncementFragmentCompose :
    BaseFragment<FragmentSchoolAnnouncementComposeBinding>(R.layout.fragment_school_announcement_compose),
    MainView.TitledView {

    companion object {
        fun newInstance() = SchoolAnnouncementFragmentCompose()
    }

    override val titleStringId: Int
        get() = R.string.school_announcement_title

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSchoolAnnouncementComposeBinding.bind(view)
        binding.composeView.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            CompositionLocalProvider(
                LocalSnackbarHostState provides snackbarHostState
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.padding(it).fillMaxSize().wrapContentSize()) {
                        SchoolAnnouncements()
                    }
                }
            }
        }
    }
}

@Composable
fun SchoolAnnouncements(viewModel: SchoolAnnouncementViewModel = viewModel()) {
    val resource by viewModel.items.collectAsStateWithReferentialEquality(Resource.Loading())

    SwipeRefreshResourceListViewComposable(
        resource = resource, onRefresh = viewModel::refresh,
        iconEmpty = painterResource(id = R.drawable.ic_all_about),
        textEmpty = stringResource(id = R.string.school_announcement_no_items),
    ) { list ->
        dividedItems(list, { it.id }) {
            SchoolAnnouncementItem(
                item = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 15.dp, horizontal = 10.dp)
            )
        }
    }
}

@Composable
fun SchoolAnnouncementItem(item: SchoolAnnouncement, modifier: Modifier = Modifier) {
    var dialogOpen by remember { mutableStateOf(false) }

    if (dialogOpen) {
        DetailsDialog(item = item, onDismiss = { dialogOpen = false })
    }

    Column(
        Modifier
            .clickable { dialogOpen = true }
            .then(modifier)) {
        Text(item.date.toFormattedString(), style = MaterialTheme.typography.subtitle2)
        Text(item.subject, style = MaterialTheme.typography.subtitle1)
        Text(item.content.parseAsHtml().toString(), style = MaterialTheme.typography.body2)
    }
}

@Composable
private fun DetailsDialog(item: SchoolAnnouncement, onDismiss: () -> Unit = {}) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.all_details)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.all_close))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.SpaceEvenly) {
                Column {
                    Text(
                        text = stringResource(id = R.string.all_subject),
                        style = MaterialTheme.typography.overline
                    )
                    Text(item.subject.ifBlank { stringResource(id = R.string.all_no_data) })
                }
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(
                        text = stringResource(id = R.string.exam_entry_date),
                        style = MaterialTheme.typography.overline
                    )
                    Text(item.date.toFormattedString()
                        .ifBlank { stringResource(id = R.string.all_no_data) })
                }
                Column {
                    Text(
                        text = stringResource(id = R.string.all_description),
                        style = MaterialTheme.typography.overline
                    )
                    Text(item.content.parseAsHtml().toString()
                        .ifBlank { stringResource(id = R.string.all_no_data) })
                }
            }
        })
}
