package io.github.wulkanowy.utils

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable

inline fun <T> LazyListScope.dividedItems(
    items: List<T>,
    noinline key: ((item: T) -> Any)? = null,
    crossinline divider: @Composable LazyItemScope.() -> Unit = { Divider() },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit
) {
    if (items.isEmpty()) return

    val mainItems = items.subList(0, items.size - 1)
    items(mainItems, key) {
        itemContent(it)
        divider()
    }

    val lastItem = items.last()
    items(listOf(lastItem), key) {
        itemContent(it)
    }
}
