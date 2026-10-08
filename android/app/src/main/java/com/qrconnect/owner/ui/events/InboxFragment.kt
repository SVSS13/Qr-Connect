package com.qrconnect.owner.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.qrconnect.owner.R
import com.qrconnect.owner.data.model.EventDto
import com.qrconnect.owner.databinding.FragmentInboxBinding
import com.qrconnect.owner.util.NetworkResult

class InboxFragment : Fragment() {

    private var _binding: FragmentInboxBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EventsViewModel by viewModels()
    private val adapter = EventsAdapter()
    private var allEvents: List<EventDto> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEvents.adapter = adapter

        setupFilterChips()
        observeViewModel()

        viewModel.fetchEvents()
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipAll
            val filterType = when (checkedId) {
                R.id.chipAlerts -> "alert"
                R.id.chipMessages -> "message"
                R.id.chipPhotos -> "photo"
                R.id.chipVideos -> "video"
                R.id.chipVoice -> "voice"
                R.id.chipLocation -> "location"
                else -> null
            }
            applyFilter(filterType)
        }
    }

    private fun applyFilter(type: String?) {
        val filtered = if (type == null) {
            allEvents
        } else {
            allEvents.filter { it.type.equals(type, ignoreCase = true) }
        }
        adapter.submitList(filtered)
        binding.layoutEmptyEvents.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        viewModel.events.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    if (allEvents.isEmpty()) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                }
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    allEvents = result.data
                    applyFilter(null)
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
