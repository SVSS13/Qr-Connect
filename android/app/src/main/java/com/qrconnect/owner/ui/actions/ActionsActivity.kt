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

        var currentSuggestedLabel = "Urgent Notification"
        dialogBinding.etActionLabel.setText(currentSuggestedLabel)

        fun updateSuggestion(newSuggestion: String, hintText: String) {
            val currentText = dialogBinding.etActionLabel.text?.toString()?.trim().orEmpty()
            if (currentText.isEmpty() || currentText == currentSuggestedLabel) {
                dialogBinding.etActionLabel.setText(newSuggestion)
                dialogBinding.etActionLabel.setSelection(newSuggestion.length)
            }
            currentSuggestedLabel = newSuggestion
            dialogBinding.tvActionHint.text = hintText
        }

        dialogBinding.chipPhoto.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) updateSuggestion("Attach Photo Note", "Visitors can capture and upload photos of surroundings, damage, or items.")
        }
        dialogBinding.chipVideo.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) updateSuggestion("Attach Video Note", "Visitors can record and upload short video clips with voice explanation.")
        }
        dialogBinding.chipAlert.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) updateSuggestion("Urgent Notification", "Sends an immediate instant alert notification to your phone.")
        }
        dialogBinding.chipMessage.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) updateSuggestion("Send Message", "Visitors can type and send you text messages.")
        }
        dialogBinding.chipVoice.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) updateSuggestion("Voice Recording", "Visitors can record voice notes directly in their browser.")
        }
        dialogBinding.chipLocation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) updateSuggestion("Share GPS Location", "Allows visitor to pinpoint and transmit their current GPS coordinates.")
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Add Action") { _, _ ->
                val label = dialogBinding.etActionLabel.text?.toString()?.trim().orEmpty()
                val selectedType = when {
                    dialogBinding.chipPhoto.isChecked -> "photo"
                    dialogBinding.chipVideo.isChecked -> "video"
                    dialogBinding.chipAlert.isChecked -> "alert"
                    dialogBinding.chipMessage.isChecked -> "message"
                    dialogBinding.chipVoice.isChecked -> "voice"
                    dialogBinding.chipLocation.isChecked -> "location"
                    else -> "photo"
                }

                if (label.isNotBlank()) {
                    viewModel.createAction(cardId, label, selectedType)
                } else {
                    com.google.android.material.snackbar.Snackbar.make(binding.root, "Label cannot be empty", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
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
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        result.message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAnchorView(binding.fabAddAction).show()
                }
            }
        }

        viewModel.operationResult.observe(this) { result ->
            if (result == null) return@observe
            when (result) {
                is NetworkResult.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        result.data,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAnchorView(binding.fabAddAction).show()
                    viewModel.clearOperationResult()
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        result.message,
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).setAnchorView(binding.fabAddAction)
                    .setBackgroundTint(getColor(com.qrconnect.owner.R.color.danger))
                    .show()
                    viewModel.clearOperationResult()
                }
            }
        }
    }
}
