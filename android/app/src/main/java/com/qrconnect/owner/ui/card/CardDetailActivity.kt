package com.qrconnect.owner.ui.card

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qrconnect.owner.data.model.QrCardDto
import com.qrconnect.owner.data.repository.CardRepository
import com.qrconnect.owner.databinding.ActivityCardDetailBinding
import com.qrconnect.owner.ui.actions.ActionsActivity
import com.qrconnect.owner.ui.events.EventsActivity
import com.qrconnect.owner.util.Constants
import com.qrconnect.owner.util.NetworkResult
import kotlinx.coroutines.launch

class CardDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCardDetailBinding
    private var card: QrCardDto? = null
    private val cardRepository = CardRepository()

    companion object {
        const val EXTRA_CARD = "extra_card"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        card = intent.getSerializableExtra(EXTRA_CARD) as? QrCardDto

        if (card == null) {
            Toast.makeText(this, "Card details unavailable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        val currentCard = card ?: return
        binding.toolbar.title = currentCard.name
        binding.tvDetailName.text = currentCard.name
        binding.tvDetailType.text = "Type: ${currentCard.type.replaceFirstChar { it.uppercase() }}"
        binding.tvDetailToken.text = "Token: ${currentCard.cardToken}"
    }

    private fun setupListeners() {
        val currentCard = card ?: return

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnManageActions.setOnClickListener {
            val intent = Intent(this, ActionsActivity::class.java).apply {
                putExtra(ActionsActivity.EXTRA_CARD_ID, currentCard.id)
                putExtra(ActionsActivity.EXTRA_CARD_NAME, currentCard.name)
            }
            startActivity(intent)
        }

        binding.btnViewEvents.setOnClickListener {
            val intent = Intent(this, EventsActivity::class.java).apply {
                putExtra(EventsActivity.EXTRA_CARD_ID, currentCard.id)
            }
            startActivity(intent)
        }

        binding.btnShareQr.setOnClickListener {
            val dialogBinding = com.qrconnect.owner.databinding.DialogQrPreviewBinding.inflate(layoutInflater)
            dialogBinding.tvQrTitle.text = currentCard.name
            dialogBinding.tvQrSubtitle.text = "Type: ${currentCard.type.replaceFirstChar { it.uppercase() }}"

            val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
            val currentMode = prefs.getString(Constants.KEY_QR_HOST_MODE, Constants.MODE_DEV_LOCALHOST)

            when (currentMode) {
                Constants.MODE_PROD_RENDER -> dialogBinding.chipProdRender.isChecked = true
                Constants.MODE_PROD_VANITY -> dialogBinding.chipVanity.isChecked = true
                else -> dialogBinding.chipDevLocalhost.isChecked = true
            }

            var printableCard: android.graphics.Bitmap? = null

            fun renderCard() {
                val scanUrl = com.qrconnect.owner.util.QrCodeGenerator.getQrScanUrl(currentCard.cardToken, this)
                dialogBinding.tvQrUrl.text = scanUrl
                val qrBitmap = com.qrconnect.owner.util.QrCodeGenerator.generateQrBitmap(scanUrl, 600, 600)

                if (qrBitmap != null) {
                    dialogBinding.ivQrCode.setImageBitmap(qrBitmap)
                    printableCard = com.qrconnect.owner.util.QrCodeGenerator.createPrintableQrCard(
                        context = this,
                        cardName = currentCard.name,
                        category = currentCard.type,
                        qrBitmap = qrBitmap,
                        scanUrl = scanUrl
                    )
                }
            }

            renderCard()

            dialogBinding.chipGroupHostMode.setOnCheckedStateChangeListener { _, checkedIds ->
                val newMode = when {
                    checkedIds.contains(dialogBinding.chipProdRender.id) -> Constants.MODE_PROD_RENDER
                    checkedIds.contains(dialogBinding.chipVanity.id) -> Constants.MODE_PROD_VANITY
                    else -> Constants.MODE_DEV_LOCALHOST
                }
                prefs.edit().putString(Constants.KEY_QR_HOST_MODE, newMode).apply()
                renderCard()
            }

            dialogBinding.btnShareQrDialog.setOnClickListener {
                val cardToShare = printableCard
                if (cardToShare != null) {
                    com.qrconnect.owner.util.QrCodeGenerator.shareQrImage(this, currentCard.name, cardToShare)
                } else {
                    val scanUrl = com.qrconnect.owner.util.QrCodeGenerator.getQrScanUrl(currentCard.cardToken, this)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "QR Connect - ${currentCard.name}")
                        putExtra(Intent.EXTRA_TEXT, "Scan or open this link to reach me: $scanUrl")
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share QR Card Link"))
                }
            }

            dialogBinding.btnExportQrDialog.setOnClickListener {
                val cardToExport = printableCard
                if (cardToExport != null) {
                    com.qrconnect.owner.util.QrCodeGenerator.exportQrImageToGallery(this, currentCard.name, cardToExport)
                } else {
                    Toast.makeText(this, "Could not generate card image", Toast.LENGTH_SHORT).show()
                }
            }

            MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.root)
                .setPositiveButton("Done", null)
                .show()
        }

        binding.btnDeleteCard.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun showDeleteConfirmDialog() {
        val currentCard = card ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Card")
            .setMessage("Are you sure you want to delete '${currentCard.name}'? This will remove all associated actions and events.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val result = cardRepository.deleteCard(currentCard.id)
                    if (result is NetworkResult.Success) {
                        Toast.makeText(this@CardDetailActivity, "Card deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    } else if (result is NetworkResult.Error) {
                        Toast.makeText(this@CardDetailActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
