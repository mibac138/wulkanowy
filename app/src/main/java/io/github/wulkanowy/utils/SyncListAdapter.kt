package io.github.wulkanowy.utils

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Custom alternative to androidx.recyclerview.widget.ListAdapter. ListAdapter is asynchronous which
 * caused data race problems in views when a Resource.Error arrived shortly after
 * Resource.Intermediate/Success - occasionally in that case the user could see both the Resource's
 * data and a error message one on top of the other. This is synchronized by design to avoid that
 * problem, however it retains the quality of life improvements of the original.
 */
abstract class SyncListAdapter<T : Any, VH : RecyclerView.ViewHolder>(private val diffCallback: DiffUtil.ItemCallback<T>) :
    RecyclerView.Adapter<VH>() {
    private var items = emptyList<T>()

    fun isEmpty(): Boolean = items.isEmpty()

    fun submitList(data: List<T>) {
        val diffResult = DiffUtil.calculateDiff(diffCallback.toCallback(items, data))
        items = data.toList()
        diffResult.dispatchUpdatesTo(this)
    }

    fun getItem(position: Int) = items[position]

    override fun getItemCount() = items.size
}
