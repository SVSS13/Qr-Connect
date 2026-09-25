package com.qrconnect.owner.ui.actions

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qrconnect.owner.databinding.ActivityActionsBinding
import com.qrconnect.owner.databinding.DialogAddActionBinding
import com.qrconnect.owner.util.NetworkResult

class ActionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityActionsBinding
    private val viewModel: ActionsViewModel by viewModels()
    private lateinit var adapter: ActionsAdapter
    private var cardId: String = ""

    companion object {
        const val EXTRA_CARD_ID = "extra_card_id"
        const val EXTRA_CARD_NAME = "extra_card_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cardId = intent.getStringExtra(EXTRA_CARD_ID).orEmpty()
        val cardName = intent.getStringExtra(EXTRA_CARD_NAME).orEmpty()

        if (cardName.isNotBlank()) {
            binding.toolbar.subtitle = cardName
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        if (cardId.isNotBlank()) {
            viewModel.fetchActions(cardId)
        }
    }

    private fun setupRecyclerView() {
        adapter = ActionsAdapter(
            onToggle = { action, isChecked ->
                viewModel.toggleAction(cardId, action.id, isChecked)
            },
            onDelete = { action ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Action")
                    .setMessage("Remove '${action.label}' from this QR card?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteAction(cardId, action.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvActions.layoutManager = LinearLayoutManager(this)
        binding.rvActions.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddAction.setOnClickListener {
            showAddActionDialog()
        }
    }

    private fun showAddActionDialog() {
        val dialogBinding = DialogAddActionBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val label = dialogBinding.etActionLabel.text?.toString()?.trim().orEmpty()
                val type = dialogBinding.etActionType.text?.toString()?.trim().orEmpty().ifBlank { "alert" }

                if (label.isNotBlank()) {
                    viewModel.createAction(cardId, label, type)
                } else {
                    Toast.makeText(this, "Label cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.actions.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    if (adapter.itemCount == 0) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                }
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    adapter.submitList(result.data)
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
