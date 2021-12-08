package io.github.wulkanowy.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.wulkanowy.data.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.experimental.ExperimentalTypeInference

@Composable
fun <T> SwipeRefreshResourceViewComposable(
    resource: Resource<T>,
    onRefresh: () -> Unit,
    success: @Composable (T) -> Unit
) {
    val hasData by remember { mutableStateOf(false) }.apply {
        if (resource.data != null) value = true
    }
    AppSwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = hasData && resource.status == Status.LOADING),
        onRefresh = onRefresh
    ) {
        ResourceViewComposable(resource = resource, onRetry = onRefresh, success)
    }
}

@Composable
fun AppSwipeRefresh(
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
fun <T> ResourceViewComposable(
    resource: Resource<T>,
    onRetry: () -> Unit,
    success: @Composable (T) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lastData = remember { mutableStateOf<T?>(null) }
    if (resource.status == Status.SUCCESS
        || (resource.status == Status.LOADING &&
            (resource.data != null || lastData.value != null))
    ) {
        if (resource.data != null) {
            lastData.value = resource.data
        } else {
            // Success is required to have associated data
            assert(resource.status != Status.SUCCESS)
        }
        success(lastData.value!!)
    } else if (resource.status == Status.LOADING) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (resource.status == Status.ERROR) {
        val errorName = resource.error!!.resString()
        val data = lastData.value
        if (data != null) {
            val actionLabel = stringResource(id = R.string.all_details)
            LaunchedEffect(snackbarHostState, resource.error) {
                val result =
                    snackbarHostState.showSnackbar(errorName, actionLabel, SnackbarDuration.Long)
                // TODO show error details
            }
            success(data)
        } else {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_error),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(bottom = 20.dp)
                        .size(100.dp)
                )
                Text(
                    text = "$errorName [Compose]",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                    fontSize = 20.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    OutlinedButton(onClick = { /*TODO show error details*/ }) {
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
    }
    SnackbarHost(hostState = snackbarHostState)
}

internal data class TriggerData(val emitLoading: Boolean, val callback: () -> Unit)
class FlowTrigger internal constructor(private val onRefresh: Channel<TriggerData>) {
    fun trigger(emitLoading: Boolean = false) {
        onRefresh.trySend(TriggerData(emitLoading) {}).getOrThrow()
    }

    suspend fun triggerUntilCompletion(emitLoading: Boolean = false) {
        val done = Channel<Unit>(1)
        // This should never actually throw
        val callback = { done.trySend(Unit).getOrThrow() }
        // This could throw if there were multiple refreshes in progress at once, but that shouldn't
        // happen (or more generally, this can throw whenever there's more than one flow using this
        // trigger at the same time)
        onRefresh.trySend(TriggerData(emitLoading, callback)).getOrThrow()
        done.receive()
    }

    internal suspend fun receive() = onRefresh.receive()
}

// Do NOT share across flows
fun FlowTrigger(): FlowTrigger =
    FlowTrigger(
        Channel(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    )

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTypeInference::class)
fun <T> flowWithTriggerTransform(
    trigger: FlowTrigger,
    @BuilderInference block: suspend FlowCollector<Resource<T>>.(manuallyTriggered: Boolean) -> Unit
): Flow<Resource<T>> {
    var triggerInProgress = false
    var done = false
    val flow = merge(
        // Don't emit values while trigger in progress to prevent emitting the same values twice
        flow { block(false) }
            .transform { if (!triggerInProgress) emit(it) }
            .onCompletion { done = true },
        flow {
            while (!done) {
                val params = trigger.receive()
                triggerInProgress = true
                try {
                    val flow = flow { block(true) }
                    // This must not be an infinite flow (when not fulfilled effects include:
                    // infinite loading or `retry` button that's broken after the first use)
                    if (params.emitLoading) {
                        emitAll(flow.untilFirstResult())
                    } else {
                        emit(flow.toFirstResult())
                    }
                } finally {
                    triggerInProgress = false
                    params.callback()
                }
            }
        })
    return flow
}

fun <R : Resource<T>, T> flatFlowWithTrigger(
    trigger: FlowTrigger,
    block: suspend (triggered: Boolean) -> Flow<R>
) =
    flowWithTriggerTransform(trigger) { emitAll(block(it)) }

private class ProduceStateScopeImpl2<T>(
    state: MutableState<T>,
    override val coroutineContext: CoroutineContext
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
fun <T> produceState2(
    initialValue: T,
    key1: Any?,
    key2: Any?,
    @BuilderInference producer: suspend ProduceStateScope<T>.() -> Unit
): State<T> {
    val result = remember { mutableStateOf(initialValue, referentialEqualityPolicy()) }
    LaunchedEffect(key1, key2) {
        ProduceStateScopeImpl2(result, coroutineContext).producer()
    }
    return result
}

@Composable
fun <T : R, R> Flow<T>.collectAsState2(
    initial: R,
    context: CoroutineContext = EmptyCoroutineContext
): State<R> = produceState2(initial, this, context) {
    if (context == EmptyCoroutineContext) {
        collect { value = it }
    } else withContext(context) {
        collect { value = it }
    }
}
