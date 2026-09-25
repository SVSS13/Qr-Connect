package com.qrconnect.owner.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.qrconnect.owner.data.model.QrCardDto
import com.qrconnect.owner.databinding.ItemQrCardBinding

class CardsAdapter(private val onCardClick: (QrCardDto) -> Unit) :
    ListAdapter<QrCardDto, CardsAdapter.CardViewHolder>(CardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemQrCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CardViewHolder(private val binding: ItemQrCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: QrCardDto) {
            binding.tvCardName.text = card.name
            binding.tvCardType.text = card.type.replaceFirstChar { it.uppercase() }
            binding.tvCardStatus.text = card.status.uppercase()
            binding.tvCardToken.text = card.cardToken

            val iconRes = when (card.type.lowercase()) {
                "car" -> com.qrconnect.owner.R.drawable.ic_car
                "home" -> com.qrconnect.owner.R.drawable.ic_home
                else -> com.qrconnect.owner.R.drawable.ic_tag
            }
            binding.ivCardIcon.setImageResource(iconRes)

            binding.root.setOnClickListener {
                onCardClick(card)
            }
        }
    }

    class CardDiffCallback : DiffUtil.ItemCallback<QrCardDto>() {
        override fun areItemsTheSame(oldItem: QrCardDto, newItem: QrCardDto): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: QrCardDto, newItem: QrCardDto): Boolean =
            oldItem == newItem
    }
}
