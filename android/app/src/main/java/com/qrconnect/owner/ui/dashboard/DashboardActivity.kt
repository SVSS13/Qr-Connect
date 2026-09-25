package com.qrconnect.owner.ui.dashboard

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.qrconnect.owner.R
import com.qrconnect.owner.databinding.ActivityDashboardBinding
import com.qrconnect.owner.ui.events.InboxFragment
import com.qrconnect.owner.ui.tools.ToolsFragment
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()

    private val cardsFragment = CardsFragment()
    private val inboxFragment = InboxFragment()
    private val toolsFragment = ToolsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
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
