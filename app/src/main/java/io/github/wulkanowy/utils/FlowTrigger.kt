package io.github.wulkanowy.utils

import io.github.wulkanowy.data.Resource
import io.github.wulkanowy.data.toFirstResult
import io.github.wulkanowy.data.untilFirstResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.experimental.ExperimentalTypeInference


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
fun FlowTrigger(): FlowTrigger = FlowTrigger(
    Channel(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
)

@OptIn(ExperimentalTypeInference::class)
fun <T> flowWithTriggerTransform(
    trigger: FlowTrigger,
    @BuilderInference block: suspend FlowCollector<Resource<T>>.(isManuallyTriggered: Boolean) -> Unit
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
    trigger: FlowTrigger, block: suspend (triggered: Boolean) -> Flow<R>
) = flowWithTriggerTransform(trigger) { emitAll(block(it)) }

