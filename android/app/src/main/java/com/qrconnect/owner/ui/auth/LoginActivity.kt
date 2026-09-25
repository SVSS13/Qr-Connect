package com.qrconnect.owner.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.qrconnect.owner.databinding.ActivityLoginBinding
import com.qrconnect.owner.ui.dashboard.DashboardActivity
import com.qrconnect.owner.util.NetworkResult

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already logged in AND not adding an account, navigate straight to Dashboard
        val isAddingAccount = intent.getBooleanExtra("EXTRA_ADD_ACCOUNT", false)
        if (!isAddingAccount && viewModel.isLoggedIn()) {
            navigateToDashboard()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    private var isRegisterMode = false

    private fun setupListeners() {
        binding.tvToggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            if (isRegisterMode) {
                binding.tilName.visibility = View.VISIBLE
                binding.btnLogin.text = getString(com.qrconnect.owner.R.string.register_button)
                binding.tvToggleMode.text = getString(com.qrconnect.owner.R.string.already_have_account)
            } else {
                binding.tilName.visibility = View.GONE
                binding.btnLogin.text = getString(com.qrconnect.owner.R.string.login_button)
                binding.tvToggleMode.text = getString(com.qrconnect.owner.R.string.dont_have_account)
            }
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString()?.trim().orEmpty()
            if (isRegisterMode) {
                val name = binding.etName.text?.toString()?.trim().orEmpty()
                viewModel.register(name, email, password)
            } else {
                viewModel.login(email, password)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this, "Welcome, ${result.data.user.name}!", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
