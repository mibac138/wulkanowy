package io.github.wulkanowy.ui.modules.more

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.wulkanowy.databinding.ItemMoreBinding
import io.github.wulkanowy.utils.SyncListAdapter
import javax.inject.Inject

class MoreAdapter @Inject constructor() : SyncListAdapter<MoreItem, MoreAdapter.ItemViewHolder>() {

    var onClickListener: (moreItem: MoreItem) -> Unit = {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ItemViewHolder(
        ItemMoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context

        with(holder.binding) {
            moreItemTitle.text = context.getString(item.title)
            moreItemImage.setImageResource(item.icon)

            root.setOnClickListener { onClickListener(item) }
        }
    }

    class ItemViewHolder(val binding: ItemMoreBinding) : RecyclerView.ViewHolder(binding.root)
}
