package com.qrconnect.owner.ui.tools

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qrconnect.owner.data.api.ApiClient
import com.qrconnect.owner.databinding.DialogDiagnosticsBinding
import com.qrconnect.owner.databinding.DialogQrPreviewBinding
import com.qrconnect.owner.databinding.FragmentToolsBinding
import com.qrconnect.owner.ui.auth.LoginActivity
import com.qrconnect.owner.ui.dashboard.DashboardViewModel
import com.qrconnect.owner.util.Constants
import com.qrconnect.owner.util.NetworkResult
import com.qrconnect.owner.util.QrCodeGenerator
import com.qrconnect.owner.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    private val dashboardViewModel: DashboardViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupProfileCard()
        setupListeners()
    }

    private fun setupProfileCard() {
        val name = TokenManager.getUserName() ?: "Owner"
        val email = TokenManager.getUserEmail() ?: "owner@qrconnect.app"
        binding.tvProfileName.text = name
        binding.tvProfileEmail.text = email

        binding.btnSwitchAccount.setOnClickListener {
            showAccountSwitcherDialog()
        }

        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out from this device?")
                .setPositiveButton("Sign Out") { _, _ ->
                    TokenManager.clear()
                    val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showAccountSwitcherDialog() {
        val dialogBinding = com.qrconnect.owner.databinding.DialogAccountSwitcherBinding.inflate(layoutInflater)
        val accounts = TokenManager.getSavedAccounts()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()

        dialogBinding.rvAccounts.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        dialogBinding.rvAccounts.adapter = AccountsAdapter(accounts) { selectedAccount ->
            TokenManager.switchAccount(selectedAccount.userId)
            Toast.makeText(requireContext(), "Switched to ${selectedAccount.name}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            requireActivity().recreate()
        }

        dialogBinding.btnAddAccount.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                putExtra("EXTRA_ADD_ACCOUNT", true)
            }
            startActivity(intent)
        }

        dialog.show()
    }

    private fun setupListeners() {
        binding.cardQrStudio.setOnClickListener {
            showQrStudioDialog()
        }

        binding.cardDiagnostics.setOnClickListener {
            showDiagnosticsDialog()
        }

        binding.cardTestPush.setOnClickListener {
            triggerTestNotification()
        }
    }

    private fun showQrStudioDialog() {
        val cardsResult = dashboardViewModel.cards.value
        val cardList = if (cardsResult is NetworkResult.Success) cardsResult.data else emptyList()

        if (cardList.isEmpty()) {
            Toast.makeText(requireContext(), "No cards available. Create a card first.", Toast.LENGTH_SHORT).show()
            return
        }

        val firstCard = cardList.first()
        val dialogBinding = DialogQrPreviewBinding.inflate(layoutInflater)
        dialogBinding.tvQrTitle.text = firstCard.name
        dialogBinding.tvQrSubtitle.text = "Type: ${firstCard.type.replaceFirstChar { it.uppercase() }}"

        val prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val currentMode = prefs.getString(Constants.KEY_QR_HOST_MODE, Constants.MODE_DEV_LOCALHOST)

        when (currentMode) {
            Constants.MODE_PROD_RENDER -> dialogBinding.chipProdRender.isChecked = true
            Constants.MODE_PROD_VANITY -> dialogBinding.chipVanity.isChecked = true
            else -> dialogBinding.chipDevLocalhost.isChecked = true
        }

        var printableCard: android.graphics.Bitmap? = null

        fun renderCard() {
            val scanUrl = QrCodeGenerator.getQrScanUrl(firstCard.cardToken, requireContext())
            dialogBinding.tvQrUrl.text = scanUrl
            val qrBitmap = QrCodeGenerator.generateQrBitmap(scanUrl, 600, 600)

            if (qrBitmap != null) {
                dialogBinding.ivQrCode.setImageBitmap(qrBitmap)
                printableCard = QrCodeGenerator.createPrintableQrCard(
                    context = requireContext(),
                    cardName = firstCard.name,
                    category = firstCard.type,
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
                QrCodeGenerator.shareQrImage(requireContext(), firstCard.name, cardToShare)
            } else {
                val scanUrl = QrCodeGenerator.getQrScanUrl(firstCard.cardToken, requireContext())
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "QR Connect - ${firstCard.name}")
                    putExtra(Intent.EXTRA_TEXT, "Scan or open this link to reach me: $scanUrl")
                }
                startActivity(Intent.createChooser(shareIntent, "Share QR Card Link"))
            }
        }

        dialogBinding.btnExportQrDialog.setOnClickListener {
            val cardToExport = printableCard
            if (cardToExport != null) {
                QrCodeGenerator.exportQrImageToGallery(requireContext(), firstCard.name, cardToExport)
            } else {
                Toast.makeText(requireContext(), "Could not generate card image", Toast.LENGTH_SHORT).show()
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showDiagnosticsDialog() {
        val dialogBinding = DialogDiagnosticsBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()

        fun runPing() {
            dialogBinding.diagProgress.visibility = View.VISIBLE
            dialogBinding.tvDiagLatency.text = "Ping: Measuring latency..."
            dialogBinding.tvDiagStatus.text = "Status: Testing /health endpoint..."

            lifecycleScope.launch {
                val startTime = System.currentTimeMillis()
                val (success, message) = withContext(Dispatchers.IO) {
                    try {
                        val url = URL("${Constants.DEFAULT_BASE_URL}health")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.requestMethod = "GET"
                        val code = conn.responseCode
                        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                        if (code == 200) {
                            Pair(true, "Healthy: $body")
                        } else {
                            Pair(false, "HTTP $code: $body")
                        }
                    } catch (e: Exception) {
                        Pair(false, "Connection error: ${e.localizedMessage ?: "Timeout"}")
                    }
                }
                val latency = System.currentTimeMillis() - startTime
                dialogBinding.diagProgress.visibility = View.GONE
                if (success) {
                    dialogBinding.tvDiagLatency.text = "Ping: ${latency}ms (Excellent)"
                    dialogBinding.tvDiagStatus.text = "Status: Online • $message"
                    dialogBinding.tvDiagStatus.setTextColor(resources.getColor(com.qrconnect.owner.R.color.success, null))
                } else {
                    dialogBinding.tvDiagLatency.text = "Ping: Failed"
                    dialogBinding.tvDiagStatus.text = "Status: Offline • $message"
                    dialogBinding.tvDiagStatus.setTextColor(resources.getColor(com.qrconnect.owner.R.color.danger, null))
                }
            }
        }

        dialogBinding.btnRecheck.setOnClickListener {
            runPing()
        }

        dialog.show()
        runPing()
    }

    private fun triggerTestNotification() {
        Toast.makeText(requireContext(), "Test notification dispatched to device!", Toast.LENGTH_SHORT).show()
        val messagingService = com.qrconnect.owner.service.QRConnectMessagingService()
        // Send a local test broadcast notification via notification manager
        val notificationManager = requireContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "qr_connect_alerts"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "QR Alerts", android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Test QR Connect Alert")
            .setContentText("Heads-up notification and alert sound verified!")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(999, builder.build())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
