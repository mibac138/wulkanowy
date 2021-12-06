package io.github.wulkanowy.utils

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.wulkanowy.data.Resource
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
                emitAll(flow {
                    block(true)
                }.takeUntilFirstResultInclusive())
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