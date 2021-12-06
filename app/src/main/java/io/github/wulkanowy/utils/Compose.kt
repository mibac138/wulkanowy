package io.github.wulkanowy.utils

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.experimental.ExperimentalTypeInference

@Composable
@ReadOnlyComposable
private fun resources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}


@Composable
fun <T> ResourceViewComposable(
    resource: Resource<T>,
    onRetry: () -> Unit,
    success: @Composable (T) -> Unit
) {
    if (resource.status == Status.SUCCESS || (resource.status == Status.LOADING && resource.data != null)) {
        success(resource.data!!)
    }
    if (resource.status == Status.LOADING) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
    if (resource.status == Status.ERROR) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_error),
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .size(100.dp)
            )
            Text(
                text = resources().getString(resource.error!!),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
                fontSize = 20.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 16.dp)
            ) {
                OutlinedButton(onClick = { /*TODO*/ }) {
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

class FlowTrigger(private val onRefresh: Channel<() -> Unit>) {
    fun trigger() {
        onRefresh.trySend {}.getOrThrow()
    }

    suspend fun triggerUntilCompletion() {
        val done = Channel<Unit>(1)
        onRefresh.trySend { done.trySend(Unit).getOrThrow() }.getOrThrow()
        done.receive()
    }

    internal suspend fun receive() = onRefresh.receive()
}

fun FlowTrigger(): FlowTrigger =
    FlowTrigger(
        Channel(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    )

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTypeInference::class)
fun <T> flowWithTrigger(
    trigger: FlowTrigger,
    @BuilderInference block: suspend FlowCollector<Resource<T>>.(manuallyTriggered: Boolean) -> Unit
): Flow<Resource<T>> {
    class Ref<T>(var data: T)

    val triggerInProgress = Ref(false)
    val flow = merge(
        // Don't emit values while trigger in progress to prevent emitting the same values twice
        flow { block(false) }.transform { if (!triggerInProgress.data) emit(it) },
        flow {
            while (true) {
                val onDoneCallback = trigger.receive()
                triggerInProgress.data = true
                // Only subscribe for a single real (not loading) value
                emit(flow {
                    block(true)
                }.toFirstResult())
                onDoneCallback()
                triggerInProgress.data = false
            }
        })
    return flow
}

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

/**
 * Load a quantity string resource.
 *
 * @param id the resource identifier
 * @param quantity The number used to get the string for the current language's plural rules.
 * @return the string data associated with the resource
 */
@Composable
fun quantityStringResource(@PluralsRes id: Int, quantity: Int): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(id, quantity)
}

/**
 * Load a quantity string resource with formatting.
 *
 * @param id the resource identifier
 * @param quantity The number used to get the string for the current language's plural rules.
 * @param formatArgs the format arguments
 * @return the string data associated with the resource
 */
@Composable
fun quantityStringResource(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    return context.resources.getQuantityString(id, quantity, *formatArgs)
}