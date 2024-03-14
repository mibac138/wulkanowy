package io.github.wulkanowy.utils

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

/**
 * Custom alternative to androidx.recyclerview.widget.ListAdapter. ListAdapter is asynchronous which
 * caused data race problems in views when a Resource.Error arrived shortly after
 * Resource.Intermediate/Success - occasionally in that case the user could see both the Resource's
 * data and a error message one on top of the other. This is synchronized by design to avoid that
 * problem, however it retains the quality of life improvements of the original.
 */
abstract class SyncListAdapter<T : Any, VH : RecyclerView.ViewHolder>
private constructor(private val updateStrategy: SyncListAdapter<T, VH>.(List<T>) -> Unit) :
    RecyclerView.Adapter<VH>() {

    /**
     * Support more efficient operation with a ItemCallback to diff items
     */
    constructor(differ: DiffUtil.ItemCallback<T>) : this({ newItems ->
        val diffResult = DiffUtil.calculateDiff(differ.toCallback(items, newItems))
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    })

    /**
     * Support a bare-bones mode that always invalidates all items
     */
    @SuppressLint("NotifyDataSetChanged")
    constructor() : this({ newItems ->
        items = newItems
        notifyDataSetChanged()
    })

    var items = emptyList<T>()
        private set

    fun isEmpty(): Boolean = items.isEmpty()

    fun submitList(data: List<T>) {
        val old = items
        updateStrategy(data.toList())
        onSubmit(old, data)
    }

    fun moveItem(from: Int, to: Int) {
        Collections.swap(items, from, to)
        notifyItemMoved(from, to)
    }

    fun removeItemAt(position: Int) {
        items = items.toMutableList().apply { removeAt(position) }
        notifyItemRemoved(position)
    }

    fun addItemAt(position: Int, item: T) {
        items = items.toMutableList().apply { add(position, item) }
        notifyItemInserted(position)
    }

    protected open fun onSubmit(old: List<T>, new: List<T>) {}

    final override fun getItemCount() = items.size
}
