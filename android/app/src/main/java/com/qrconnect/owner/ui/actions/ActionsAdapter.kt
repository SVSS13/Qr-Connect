package com.qrconnect.owner.ui.actions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.qrconnect.owner.data.model.QrActionDto
import com.qrconnect.owner.databinding.ItemActionBinding

class ActionsAdapter(
    private val onToggle: (QrActionDto, Boolean) -> Unit,
    private val onDelete: (QrActionDto) -> Unit
) : ListAdapter<QrActionDto, ActionsAdapter.ActionViewHolder>(ActionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val binding = ItemActionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ActionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ActionViewHolder(private val binding: ItemActionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(action: QrActionDto) {
            val (icon, typeName) = when (action.actionType.lowercase()) {
                "photo" -> "📸" to "Photo Note"
                "video" -> "🎥" to "Video Note"
                "alert" -> "🚨" to "Quick Alert"
                "message" -> "💬" to "Text Message"
                "voice" -> "🎙️" to "Voice Note"
                "location" -> "📍" to "Location Pin"
                else -> "⚡" to action.actionType.replaceFirstChar { it.uppercase() }
            }
            binding.tvActionIcon.text = icon
            binding.tvActionLabel.text = action.label
            binding.tvActionType.text = typeName

            // Remove listener before setting checked state to avoid unwanted triggers
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = action.enabled

            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(action, isChecked)
            }

            binding.btnDeleteAction.setOnClickListener {
                onDelete(action)
            }
        }
    }

    class ActionDiffCallback : DiffUtil.ItemCallback<QrActionDto>() {
        override fun areItemsTheSame(oldItem: QrActionDto, newItem: QrActionDto): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: QrActionDto, newItem: QrActionDto): Boolean =
            oldItem == newItem
    }
}
