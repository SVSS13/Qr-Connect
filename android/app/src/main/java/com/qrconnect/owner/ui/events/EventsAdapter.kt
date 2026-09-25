package com.qrconnect.owner.ui.events

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.qrconnect.owner.R
import com.qrconnect.owner.data.model.EventDto
import com.qrconnect.owner.databinding.ItemEventBinding
import com.qrconnect.owner.util.Constants

class EventsAdapter : ListAdapter<EventDto, EventsAdapter.EventViewHolder>(EventDiffCallback()) {

    private var activePlayer: MediaPlayer? = null
    private var activePlayingEventId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: EventViewHolder) {
        super.onViewRecycled(holder)
        stopCurrentPlayback()
    }

    private fun stopCurrentPlayback() {
        activePlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        activePlayer = null
        activePlayingEventId = null
    }

    inner class EventViewHolder(private val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: EventDto) {
            binding.tvEventType.text = event.type.uppercase()
            binding.tvEventDate.text = event.createdAt.take(19).replace("T", " ")
            binding.tvEventContent.text = event.content ?: "(No content)"

            // Geolocation display with interactive maps intent
            if (event.latitude != null && event.longitude != null) {
                binding.tvEventLocation.visibility = View.VISIBLE
                val displayText = if (!event.address.isNullOrBlank()) {
                    "📍 ${event.address} (Tap to view map)"
                } else {
                    "📍 ${event.latitude}, ${event.longitude} (Tap to view map)"
                }
                binding.tvEventLocation.text = displayText

                binding.tvEventLocation.setOnClickListener {
                    val label = event.address ?: "Visitor Location"
                    val uri = android.net.Uri.parse("geo:${event.latitude},${event.longitude}?q=${event.latitude},${event.longitude}(${android.net.Uri.encode(label)})")
                    val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                    try {
                        binding.root.context.startActivity(mapIntent)
                    } catch (e: Exception) {
                        Toast.makeText(binding.root.context, "No map application available", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.tvEventLocation.visibility = View.GONE
            }


            // Voice audio playback
            if (event.type == "voice") {
                binding.layoutVoicePlayer.visibility = View.VISIBLE
                binding.tvVoiceDuration.text = "Voice Message"

                val isCurrentPlaying = activePlayingEventId == event.id
                binding.btnPlayVoice.setIconResource(
                    if (isCurrentPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )

                binding.btnPlayVoice.setOnClickListener {
                    if (activePlayingEventId == event.id) {
                        stopCurrentPlayback()
                        binding.btnPlayVoice.setIconResource(android.R.drawable.ic_media_play)
                    } else {
                        playVoiceAudio(event, binding)
                    }
                }
            } else {
                binding.layoutVoicePlayer.visibility = View.GONE
            }

            // Photo note display
            if (event.type == "photo" && !event.content.isNullOrBlank()) {
                val parts = event.content.split(":::")
                val photoPath = parts.firstOrNull().orEmpty()
                val caption = if (parts.size > 1) parts[1] else null

                if (!caption.isNullOrBlank()) {
                    binding.tvEventContent.text = caption
                } else {
                    binding.tvEventContent.text = "Photo sent by visitor"
                }

                val fullPhotoUrl = if (photoPath.startsWith("http")) photoPath else "${Constants.DEFAULT_BASE_URL.removeSuffix("/")}$photoPath"
                binding.cardEventPhoto.visibility = View.VISIBLE
                com.qrconnect.owner.util.ImageLoader.load(fullPhotoUrl, binding.ivEventPhoto)

                binding.cardEventPhoto.setOnClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fullPhotoUrl))
                    try {
                        binding.root.context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(binding.root.context, "Opening photo", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.cardEventPhoto.visibility = View.GONE
            }

            // Video note playback
            if (event.type == "video" && !event.content.isNullOrBlank()) {
                val parts = event.content.split(":::")
                val videoPath = parts.firstOrNull().orEmpty()
                val caption = if (parts.size > 1) parts[1] else null

                if (!caption.isNullOrBlank()) {
                    binding.tvEventContent.text = caption
                } else {
                    binding.tvEventContent.text = "Video note recorded by visitor"
                }

                val fullVideoUrl = if (videoPath.startsWith("http")) videoPath else "${Constants.DEFAULT_BASE_URL.removeSuffix("/")}$videoPath"
                binding.layoutVideoPlayer.visibility = View.VISIBLE
                binding.btnPlayVideo.setOnClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(android.net.Uri.parse(fullVideoUrl), "video/*")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        binding.root.context.startActivity(intent)
                    } catch (e: Exception) {
                        val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fullVideoUrl))
                        binding.root.context.startActivity(browserIntent)
                    }
                }
            } else {
                binding.layoutVideoPlayer.visibility = View.GONE
            }
        }

        private fun playVoiceAudio(event: EventDto, binding: ItemEventBinding) {
            stopCurrentPlayback()

            // Resolve audio URL
            val audioUrl = if (event.content != null && event.content.startsWith("/uploads/")) {
                "${Constants.DEFAULT_BASE_URL.removeSuffix("/")}${event.content}"
            } else {
                "${Constants.DEFAULT_BASE_URL.removeSuffix("/")}/uploads/voice/${event.id}.webm"
            }

            try {
                activePlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(audioUrl)
                    setOnPreparedListener { mp ->
                        mp.start()
                        activePlayingEventId = event.id
                        binding.btnPlayVoice.setIconResource(android.R.drawable.ic_media_pause)
                    }
                    setOnCompletionListener {
                        stopCurrentPlayback()
                        binding.btnPlayVoice.setIconResource(android.R.drawable.ic_media_play)
                    }
                    setOnErrorListener { _, _, _ ->
                        stopCurrentPlayback()
                        binding.btnPlayVoice.setIconResource(android.R.drawable.ic_media_play)
                        Toast.makeText(binding.root.context, "Audio playback error", Toast.LENGTH_SHORT).show()
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e("EventsAdapter", "Failed to start audio playback", e)
                Toast.makeText(binding.root.context, "Could not stream audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<EventDto>() {
        override fun areItemsTheSame(oldItem: EventDto, newItem: EventDto): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EventDto, newItem: EventDto): Boolean =
            oldItem == newItem
    }
}
