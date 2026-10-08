package com.qrconnect.owner.ui.dashboard

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.qrconnect.owner.R
import com.qrconnect.owner.databinding.ActivityDashboardBinding
import com.qrconnect.owner.service.EventSyncReceiver
import com.qrconnect.owner.ui.events.InboxFragment
import com.qrconnect.owner.ui.tools.ToolsFragment
import com.qrconnect.owner.util.EventSyncManager
import com.qrconnect.owner.util.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()

    private val cardsFragment = CardsFragment()
    private val inboxFragment = InboxFragment()
    private val toolsFragment = ToolsFragment()

    private var syncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createNotificationChannel(this)
        setupBottomNavigation()
        setupEventSyncListeners()
        requestNotificationPermission()
        syncFcmToken()

        if (savedInstanceState == null) {
            switchFragment(cardsFragment)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_cards -> {
                    switchFragment(cardsFragment)
                    true
                }
                R.id.nav_inbox -> {
                    binding.bottomNav.removeBadge(R.id.nav_inbox)
                    EventSyncManager.markAllAsRead(this)
                    switchFragment(inboxFragment)
                    true
                }
                R.id.nav_tools -> {
                    switchFragment(toolsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupEventSyncListeners() {
        EventSyncManager.onUnreadCountChanged = { unreadCount ->
            if (unreadCount > 0) {
                val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_inbox)
                badge.isVisible = true
                badge.number = unreadCount
            } else {
                binding.bottomNav.removeBadge(R.id.nav_inbox)
            }
        }

        EventSyncManager.onNewEventsListener = { newEvents ->
            if (newEvents.isNotEmpty()) {
                val latest = newEvents.last()
                val label = if (!latest.cardName.isNullOrBlank()) "on ${latest.cardName}" else "received"
                Snackbar.make(binding.root, "New activity $label!", Snackbar.LENGTH_LONG)
                    .setAction("View") {
                        binding.bottomNav.selectedItemId = R.id.nav_inbox
                    }
                    .setAnchorView(binding.bottomNav)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startForegroundSync()
        EventSyncReceiver.cancelPeriodicSync(this)
    }

    override fun onPause() {
        super.onPause()
        stopForegroundSync()
        EventSyncReceiver.schedulePeriodicSync(this)
    }

    private fun startForegroundSync() {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    EventSyncManager.syncEvents(this@DashboardActivity, isBackground = false)
                } catch (_: Exception) {}
                delay(10_000) // Poll every 10 seconds while app is in foreground
            }
        }
    }

    private fun stopForegroundSync() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun syncFcmToken() {
        val token = com.qrconnect.owner.util.TokenManager.getFcmToken()
        if (!token.isNullOrBlank()) {
            lifecycleScope.launch {
                try {
                    com.qrconnect.owner.data.api.ApiClient.authApi.registerFcmToken(
                        com.qrconnect.owner.data.model.FcmTokenRequest(token)
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
