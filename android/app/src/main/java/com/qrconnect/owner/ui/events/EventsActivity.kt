package com.qrconnect.owner.ui.events

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.qrconnect.owner.databinding.ActivityEventsBinding
import com.qrconnect.owner.util.NetworkResult

class EventsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEventsBinding
    private val viewModel: EventsViewModel by viewModels()
    private lateinit var adapter: EventsAdapter

    companion object {
        const val EXTRA_CARD_ID = "extra_card_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cardId = intent.getStringExtra(EXTRA_CARD_ID)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupRecyclerView()
        observeViewModel()

        viewModel.fetchEvents(cardId)
    }

    private fun setupRecyclerView() {
        adapter = EventsAdapter()
        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.events.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    if (adapter.itemCount == 0) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                }
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val events = result.data
                    adapter.submitList(events)
                    binding.tvEmptyEvents.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
