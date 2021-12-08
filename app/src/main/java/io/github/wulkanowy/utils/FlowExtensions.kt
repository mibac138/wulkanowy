package io.github.wulkanowy.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import kotlin.experimental.ExperimentalTypeInference

@OptIn(ExperimentalTypeInference::class, ExperimentalCoroutinesApi::class)
inline fun <reified T, R> combineTransformLatest(
    vararg flows: Flow<T>,
    @BuilderInference noinline transform: suspend FlowCollector<R>.(Array<T>) -> Unit
): Flow<R> = combine(*flows) { it }.transformLatest(transform)

@OptIn(ExperimentalTypeInference::class)
fun <T1, T2, R> combineTransformLatest(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    @BuilderInference transform: suspend FlowCollector<R>.(T1, T2) -> Unit
): Flow<R> = combineTransformLatest(flow1, flow2) { args ->
    @Suppress("UNCHECKED_CAST")
    transform(
        args[0] as T1,
        args[1] as T2
    )
}

inline fun <T1, T2, R> combineLatest(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    crossinline transform: suspend (T1, T2) -> R
): Flow<R> = combineTransformLatest(flow1, flow2) { a, b -> emit(transform(a, b)) }
