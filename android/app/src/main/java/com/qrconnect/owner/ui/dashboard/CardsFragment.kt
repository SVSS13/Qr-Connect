package com.qrconnect.owner.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qrconnect.owner.data.model.QrCardDto
import com.qrconnect.owner.databinding.DialogAddCardBinding
import com.qrconnect.owner.databinding.FragmentCardsBinding
import com.qrconnect.owner.ui.card.CardDetailActivity
import com.qrconnect.owner.util.NetworkResult

class CardsFragment : Fragment() {

    private var _binding: FragmentCardsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels()
    private lateinit var adapter: CardsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.fetchCards()
    }

    private fun setupRecyclerView() {
        adapter = CardsAdapter { card ->
            val intent = Intent(requireContext(), CardDetailActivity::class.java).apply {
                putExtra(CardDetailActivity.EXTRA_CARD, card)
            }
            startActivity(intent)
        }
        binding.rvCards.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCards.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.fetchCards()
        }

        binding.fabAddCard.setOnClickListener {
            showAddCardDialog()
        }
    }

    private fun showAddCardDialog() {
        val dialogBinding = DialogAddCardBinding.inflate(layoutInflater)

        dialogBinding.chipGroupScenarios.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                com.qrconnect.owner.R.id.chipCar -> {
                    dialogBinding.etCardType.setText("car")
                    dialogBinding.etCardName.setText("My Car")
                }
                com.qrconnect.owner.R.id.chipDoor -> {
                    dialogBinding.etCardType.setText("door")
                    dialogBinding.etCardName.setText("Front Door")
                }
                com.qrconnect.owner.R.id.chipShoeRack -> {
                    dialogBinding.etCardType.setText("shoerack")
                    dialogBinding.etCardName.setText("Shoe Rack")
                }
                com.qrconnect.owner.R.id.chipLuggage -> {
                    dialogBinding.etCardType.setText("luggage")
                    dialogBinding.etCardName.setText("My Travel Bag")
                }
                com.qrconnect.owner.R.id.chipPet -> {
                    dialogBinding.etCardType.setText("pet")
                    dialogBinding.etCardName.setText("My Pet")
                }
                com.qrconnect.owner.R.id.chipCustom -> {
                    dialogBinding.etCardType.setText("other")
                    dialogBinding.etCardName.setText("Custom QR")
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Create") { _, _ ->
                val name = dialogBinding.etCardName.text?.toString()?.trim().orEmpty()
                val type = dialogBinding.etCardType.text?.toString()?.trim().orEmpty().ifBlank { "other" }

                if (name.isNotBlank()) {
                    viewModel.createCard(name, type)
                } else {
                    Toast.makeText(requireContext(), "Card name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.cards.observe(viewLifecycleOwner) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Loading -> {
                    if (adapter.itemCount == 0) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                }
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val cards = result.data
                    adapter.submitList(cards)
                    binding.layoutEmpty.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.createCardResult.observe(viewLifecycleOwner) { result ->
            if (result is NetworkResult.Success) {
                Toast.makeText(requireContext(), "QR Card created!", Toast.LENGTH_SHORT).show()
                viewModel.resetCreateResult()
                viewModel.fetchCards()
            } else if (result is NetworkResult.Error) {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                viewModel.resetCreateResult()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchCards()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
