package io.github.wulkanowy.utils

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.SwipeRefreshState
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import io.github.wulkanowy.R
import io.github.wulkanowy.data.Resource
import io.github.wulkanowy.data.onData
import io.github.wulkanowy.data.onError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.experimental.ExperimentalTypeInference

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

@Composable
fun <T> SwipeRefreshResourceViewComposable(
    resource: Resource<T>,
    onRefresh: () -> Unit,
    onRetry: () -> Unit = onRefresh,
    success: @Composable (T) -> Unit
) {
    // If no content (whether actual data or an error) was loaded, then display full screen
    // loading indicator, otherwise only a swipe refresh indicator
    val hasContent by remember { mutableStateOf(false) }.apply {
        if (resource !is Resource.Loading) value = true
    }

    AppSwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = hasContent && resource is Resource.Loading),
        onRefresh = onRefresh,
    ) {
        ResourceViewComposable(resource = resource, onRetry = onRetry, success)
    }
}

@Composable
fun <T : Collection<E>, E> SwipeRefreshResourceListViewComposable(
    resource: Resource<T>,
    onRefresh: () -> Unit,
    iconEmpty: Painter,
    textEmpty: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = onRefresh,
    success: LazyListScope.(T) -> Unit,
) {
    SwipeRefreshResourceViewComposable(
        resource = resource,
        onRefresh = onRefresh,
        onRetry = onRetry
    ) {
        if (it.isEmpty()) {
            EmptyView(icon = iconEmpty, text = textEmpty)
        } else {
            // fillMaxSize is required for correct behavior of swipe refresh (without it the swipe
            // to refresh gesture only works on items, not on the empty space below them)
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .then(modifier)) {
                success(it)
            }
        }
    }
}

@Composable
private fun AppSwipeRefresh(
    state: SwipeRefreshState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    refreshTriggerDistance: Dp = 80.dp,
    indicatorAlignment: Alignment = Alignment.TopCenter,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    indicator: @Composable (state: SwipeRefreshState, refreshTrigger: Dp) -> Unit = { s, trigger ->
        SwipeRefreshIndicator(s, trigger, contentColor = MaterialTheme.colors.primary)
    },
    clipIndicatorToPadding: Boolean = true,
    content: @Composable () -> Unit,
) = SwipeRefresh(
    state,
    onRefresh,
    modifier,
    swipeEnabled,
    refreshTriggerDistance,
    indicatorAlignment,
    indicatorPadding,
    indicator,
    clipIndicatorToPadding,
    content
)

// Before any actual data arrives, loading and errors are full screen
// After data arrives, loading is ignored (should be handled in parent SwipeRefreshLayout), and
// errors are displayed as snackbars
@Composable
private fun <T> ResourceViewComposable(
    resource: Resource<T>, onRetry: () -> Unit, success: @Composable (T) -> Unit
) {
    val snackbarHostState = LocalSnackbarHostState.current
    var lastData by remember { mutableStateOf<T?>(null) }
    var lastError by remember { mutableStateOf<Throwable?>(null) }
    var errorDialog by remember { mutableStateOf<Throwable?>(null) }

    resource.onData {
        lastData = it
        lastError = null
    }.onError {
        // Error is saved to prevent a blink during refreshing after a full screen error
        lastError = it
    }

    errorDialog?.let {
        ErrorDialog(error = it, onDismiss = { errorDialog = null })
    }


    val data = lastData
    val error = lastError
    if (data != null) {
        success(data)

        if (error != null) {
            val errorName = error.resString()
            val actionLabel = stringResource(id = R.string.all_details)
            LaunchedEffect(snackbarHostState, error) {
                val result =
                    snackbarHostState.showSnackbar(errorName, actionLabel, withDismissAction = true, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) errorDialog = error
            }
        }
    } else if (error != null) {
        // fillMaxSize is required for correct behavior of swipe refresh (without it the swipe
        // to refresh gesture only works on items, not on the empty space below them)
        ErrorView(
            error = error,
            onRetry = onRetry,
            onShowDetails = { errorDialog = error },
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    } else if (resource is Resource.Loading) {
        LoadingView()
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyView(icon: Painter, text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // required for swipe refresh
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painter = icon, contentDescription = null, modifier = Modifier.size(100.dp))
        Text(text, fontSize = 20.sp, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun ErrorView(error: Throwable, onRetry: () -> Unit, onShowDetails: () -> Unit, modifier: Modifier = Modifier) {
    val errorName = error.resString()
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_error),
            contentDescription = null,
            modifier = Modifier
                .padding(bottom = 20.dp)
                .size(100.dp)
        )
        Text(
            text = errorName,
            textAlign = TextAlign.Center,
            fontSize = 20.sp
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            OutlinedButton(onClick = onShowDetails) {
                Text(
                    text = stringResource(id = R.string.all_details).uppercase(),
                    fontWeight = FontWeight.Medium,
                )
            }
            Button(onClick = onRetry) {
                Text(
                    text = stringResource(id = R.string.all_retry).uppercase(),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}


private class ProduceStateScopeImpl<T>(
    state: MutableState<T>, override val coroutineContext: CoroutineContext
) : ProduceStateScope<T>, MutableState<T> by state {

    override suspend fun awaitDispose(onDispose: () -> Unit): Nothing {
        try {
            suspendCancellableCoroutine<Nothing> { }
        } finally {
            onDispose()
        }
    }
}

@OptIn(ExperimentalTypeInference::class)
@Composable
fun <T> produceStateWithReferentialEqualityPolicy(
    initialValue: T,
    key1: Any?,
    key2: Any?,
    @BuilderInference producer: suspend ProduceStateScope<T>.() -> Unit
): State<T> {
    val result = remember { mutableStateOf(initialValue, referentialEqualityPolicy()) }
    LaunchedEffect(key1, key2) {
        ProduceStateScopeImpl(result, coroutineContext).producer()
    }
    return result
}

@Composable
fun <T : R, R> Flow<T>.collectAsStateWithReferentialEquality(
    initial: R, context: CoroutineContext = EmptyCoroutineContext
): State<R> = produceStateWithReferentialEqualityPolicy(initial, this, context) {
    if (context == EmptyCoroutineContext) {
        collect { value = it }
    } else withContext(context) {
        collect { value = it }
    }
}
