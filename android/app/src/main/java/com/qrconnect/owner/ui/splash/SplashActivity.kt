package com.qrconnect.owner.ui.splash

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.qrconnect.owner.data.api.ApiClient
import com.qrconnect.owner.data.model.RefreshRequest
import com.qrconnect.owner.databinding.ActivitySplashBinding
import com.qrconnect.owner.ui.auth.LoginActivity
import com.qrconnect.owner.ui.dashboard.DashboardActivity
import com.qrconnect.owner.util.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var probeJob: Job? = null
    private var timerJob: Job? = null
    private var elapsedSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRetry.setOnClickListener {
            startWarmupProcess()
        }

        startWarmupProcess()
    }

    private fun startWarmupProcess() {
        probeJob?.cancel()
        timerJob?.cancel()
        elapsedSeconds = 0

        binding.btnRetry.visibility = View.GONE
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvStatusTitle.text = "Connecting to cloud backend..."
        binding.tvStatusDesc.text = "Checking server status..."
        binding.tvStatusTimer.text = "Time elapsed: 0s"

        if (!isNetworkAvailable()) {
            binding.progressIndicator.visibility = View.GONE
            binding.tvStatusTitle.text = "No Internet Connection"
            binding.tvStatusDesc.text = "Please check your network settings and try again."
            binding.tvStatusTimer.text = "Offline"
            binding.btnRetry.visibility = View.VISIBLE
            return
        }

        // Timer job to update UX during Render cold start (~45s)
        timerJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                elapsedSeconds++
                binding.tvStatusTimer.text = "Time elapsed: ${elapsedSeconds}s"

                if (elapsedSeconds in 3..14) {
                    binding.tvStatusTitle.text = "Waking up cloud server..."
                    binding.tvStatusDesc.text = "Render containers spin down after inactivity. Initializing server instance..."
                } else if (elapsedSeconds in 15..45) {
                    binding.tvStatusTitle.text = "Starting backend container..."
                    binding.tvStatusDesc.text = "Render free tier instances typically take 45–60s to wake up. Please wait..."
                } else if (elapsedSeconds > 45) {
                    binding.tvStatusTitle.text = "Almost ready..."
                    binding.tvStatusDesc.text = "Database connection pool and API routes are booting up..."
                }

                if (elapsedSeconds >= 90) {
                    binding.btnRetry.visibility = View.VISIBLE
                    binding.tvStatusDesc.text = "Server is taking longer than expected. Tap Retry to continue checking."
                }
            }
        }

        // Probe job to continuously ping /health until healthy
        probeJob = lifecycleScope.launch {
            val maxAttempts = 30
            var attempts = 0
            var serverReady = false

            while (isActive && attempts < maxAttempts) {
                attempts++
                try {
                    val response = ApiClient.systemApi.healthCheck()
                    if (response.isSuccessful) {
                        serverReady = true
                        break
                    }
                } catch (e: Exception) {
                    // Cold start in progress or network transit error
                }
                delay(3000)
            }

            timerJob?.cancel()

            if (serverReady) {
                binding.progressIndicator.visibility = View.GONE
                binding.tvStatusTitle.text = "Connected!"
                binding.tvStatusDesc.text = "Server is online. Verifying session..."
                delay(400) // Brief smooth transition
                processSessionAndNavigate()
            } else {
                binding.progressIndicator.visibility = View.GONE
                binding.tvStatusTitle.text = "Connection Timed Out"
                binding.tvStatusDesc.text = "Could not reach the server. Please check your internet or retry."
                binding.btnRetry.visibility = View.VISIBLE
            }
        }
    }

    private fun processSessionAndNavigate() {
        lifecycleScope.launch {
            if (!TokenManager.isLoggedIn()) {
                navigateToLogin(sessionExpired = false)
                return@launch
            }

            // User has stored session: check if access token is already valid
            if (!TokenManager.isAccessTokenExpired()) {
                navigateToDashboard()
                return@launch
            }

            // Access token expired, attempt silent token refresh
            val refreshToken = TokenManager.getRefreshToken()
            if (refreshToken.isNullOrBlank() || TokenManager.isRefreshTokenExpired()) {
                TokenManager.clearCurrentSession()
                navigateToLogin(sessionExpired = true)
                return@launch
            }

            try {
                val refreshResponse = ApiClient.authApi.refresh(RefreshRequest(refreshToken))
                if (refreshResponse.isSuccessful) {
                    val tokenData = refreshResponse.body()
                    if (tokenData != null && !tokenData.accessToken.isNullOrBlank()) {
                        TokenManager.saveTokens(tokenData.accessToken, tokenData.refreshToken)
                        navigateToDashboard()
                        return@launch
                    }
                }
            } catch (e: Exception) {
                // If refresh network fails, fallback to authenticator or dashboard
            }

            // If refresh explicitly failed/rejected by server
            TokenManager.clearCurrentSession()
            navigateToLogin(sessionExpired = true)
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this@SplashActivity, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin(sessionExpired: Boolean) {
        val intent = Intent(this@SplashActivity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (sessionExpired) {
                putExtra("EXTRA_SESSION_EXPIRED", true)
            }
        }
        startActivity(intent)
        finish()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onDestroy() {
        super.onDestroy()
        probeJob?.cancel()
        timerJob?.cancel()
    }
}
