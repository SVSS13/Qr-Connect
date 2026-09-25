package com.qrconnect.owner.ui.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qrconnect.owner.databinding.ItemSavedAccountBinding
import com.qrconnect.owner.util.SavedAccount
import com.qrconnect.owner.util.TokenManager

class AccountsAdapter(
    private val accounts: List<SavedAccount>,
    private val onAccountSelected: (SavedAccount) -> Unit
) : RecyclerView.Adapter<AccountsAdapter.AccountViewHolder>() {

    private val currentUserId = TokenManager.getUserId()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemSavedAccountBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(accounts[position])
    }

    override fun getItemCount(): Int = accounts.size

    inner class AccountViewHolder(private val binding: ItemSavedAccountBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(account: SavedAccount) {
            val initial = account.name.trim().take(1).uppercase().ifEmpty { "O" }
            binding.tvAccountAvatar.text = initial
            binding.tvAccountName.text = account.name
            binding.tvAccountEmail.text = account.email

            val isActive = account.userId == currentUserId
            binding.tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE
            binding.btnSwitch.visibility = if (isActive) View.GONE else View.VISIBLE

            binding.btnSwitch.setOnClickListener {
                onAccountSelected(account)
            }
            binding.root.setOnClickListener {
                if (!isActive) {
                    onAccountSelected(account)
                }
            }
        }
    }
}
