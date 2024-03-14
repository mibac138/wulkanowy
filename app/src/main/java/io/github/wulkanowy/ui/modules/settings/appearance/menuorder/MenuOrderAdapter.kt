package io.github.wulkanowy.ui.modules.settings.appearance.menuorder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.github.wulkanowy.databinding.ItemMenuOrderBinding
import io.github.wulkanowy.utils.toCallback
import javax.inject.Inject

class MenuOrderAdapter @Inject constructor() :
    RecyclerView.Adapter<MenuOrderAdapter.ViewHolder>() {

    val items = mutableListOf<MenuOrderItem>()

    fun submitList(newItems: List<MenuOrderItem>) {
        val diffResult = DiffUtil.calculateDiff(Differ.toCallback(newItems, items.toMutableList()))

        with(items) {
            clear()
            addAll(newItems)
        }

        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMenuOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position].appMenuItem

        with(holder.binding) {
            menuOrderItemTitle.setText(item.title)
            menuOrderItemIcon.setImageResource(item.icon)
        }
    }

    class ViewHolder(val binding: ItemMenuOrderBinding) : RecyclerView.ViewHolder(binding.root)

    private object Differ : DiffUtil.ItemCallback<MenuOrderItem>() {
        override fun areItemsTheSame(oldItem: MenuOrderItem, newItem: MenuOrderItem) =
            oldItem.appMenuItem.destinationType == newItem.appMenuItem.destinationType

        override fun areContentsTheSame(oldItem: MenuOrderItem, newItem: MenuOrderItem) =
            oldItem == newItem
    }
}
