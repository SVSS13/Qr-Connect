/**
 * QR Connect Scanner UI JavaScript
 *
 * Handles:
 * - Session countdown timer
 * - Action button interactions (alert, message, voice, location)
 * - MediaRecorder browser audio capture
 * - API calls to public endpoints
 * - UI state management (actions → form → voice → success → expired)
 */

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

let currentActionId = null;
let timerInterval = null;

// Voice recording state
let mediaRecorder = null;
let audioChunks = [];
let recordedAudioBlob = null;
let recordingTimerInterval = null;
let recordingSeconds = 0;
let isRecording = false;

// ---------------------------------------------------------------------------
// Session Timer
// ---------------------------------------------------------------------------

function startTimer() {
    const timerEl = document.getElementById('timer');
    const timerPill = document.getElementById('timer-pill');
    const pulseRing = document.getElementById('pulse-ring');
    const pulseDot = document.getElementById('pulse-dot');

    timerInterval = setInterval(() => {
        EXPIRES_IN--;

        if (EXPIRES_IN <= 0) {
            clearInterval(timerInterval);
            showExpired();
            return;
        }

        const mins = Math.floor(EXPIRES_IN / 60);
        const secs = EXPIRES_IN % 60;
        if (timerEl) timerEl.textContent = `${mins}:${secs.toString().padStart(2, '0')}`;

        // Visual warnings
        if (EXPIRES_IN <= 60) {
            if (pulseRing) pulseRing.className = 'animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75';
            if (pulseDot) pulseDot.className = 'relative inline-flex rounded-full h-2.5 w-2.5 bg-rose-500';
            if (timerPill) {
                timerPill.classList.remove('border-slate-800/80', 'border-amber-500/50');
                timerPill.classList.add('border-rose-500/60', 'ring-1', 'ring-rose-500/30');
            }
        } else if (EXPIRES_IN <= 120) {
            if (pulseRing) pulseRing.className = 'animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75';
            if (pulseDot) pulseDot.className = 'relative inline-flex rounded-full h-2.5 w-2.5 bg-amber-500';
            if (timerPill) {
                timerPill.classList.remove('border-slate-800/80');
                timerPill.classList.add('border-amber-500/50');
            }
        }
    }, 1000);
}

// ---------------------------------------------------------------------------
// UI State Management
// ---------------------------------------------------------------------------

function showActions() {
    document.getElementById('actions-panel').classList.remove('hidden');
    document.getElementById('message-form').classList.add('hidden');
    document.getElementById('voice-form').classList.add('hidden');
    document.getElementById('photo-form')?.classList.add('hidden');
    document.getElementById('video-form')?.classList.add('hidden');
    document.getElementById('success-screen').classList.add('hidden');
    document.getElementById('expired-screen').classList.add('hidden');
    currentActionId = null;
    resetVoiceRecording();
    resetPhotoSelection();
    resetVideoSelection();
}

function showMessageForm(actionId) {
    currentActionId = actionId;
    document.getElementById('actions-panel').classList.add('hidden');
    document.getElementById('message-form').classList.remove('hidden');
    document.getElementById('voice-form').classList.add('hidden');
    document.getElementById('photo-form')?.classList.add('hidden');
    document.getElementById('video-form')?.classList.add('hidden');
    document.getElementById('message-input').value = '';
    document.getElementById('char-count').textContent = '0 / 500';
    document.getElementById('message-input').focus();
}

function showVoiceForm(actionId) {
    currentActionId = actionId;
    document.getElementById('actions-panel').classList.add('hidden');
    document.getElementById('message-form').classList.add('hidden');
    document.getElementById('voice-form').classList.remove('hidden');
    document.getElementById('photo-form')?.classList.add('hidden');
    document.getElementById('video-form')?.classList.add('hidden');
    resetVoiceRecording();
}

function showPhotoForm(actionId) {
    currentActionId = actionId;
    document.getElementById('actions-panel').classList.add('hidden');
    document.getElementById('message-form').classList.add('hidden');
    document.getElementById('voice-form').classList.add('hidden');
    document.getElementById('photo-form')?.classList.remove('hidden');
    document.getElementById('video-form')?.classList.add('hidden');
    resetPhotoSelection();
}

function showVideoForm(actionId) {
    currentActionId = actionId;
    document.getElementById('actions-panel').classList.add('hidden');
    document.getElementById('message-form').classList.add('hidden');
    document.getElementById('voice-form').classList.add('hidden');
    document.getElementById('photo-form')?.classList.add('hidden');
    document.getElementById('video-form')?.classList.remove('hidden');
    resetVideoSelection();
}

function cancelVoiceRecording() {
    if (isRecording) {
        stopVoiceRecording();
    }
    showActions();
}

function showSuccess(title, message) {
    document.getElementById('actions-panel').classList.add('hidden');
    document.getElementById('message-form').classList.add('hidden');
    document.getElementById('voice-form').classList.add('hidden');
    document.getElementById('photo-form')?.classList.add('hidden');
    document.getElementById('video-form')?.classList.add('hidden');
    document.getElementById('success-screen').classList.remove('hidden');
    document.getElementById('success-title').textContent = title;
    document.getElementById('success-message').textContent = message;
}

function showExpired() {
    document.getElementById('actions-panel')?.classList.add('hidden');
    document.getElementById('message-form')?.classList.add('hidden');
    document.getElementById('voice-form')?.classList.add('hidden');
    document.getElementById('photo-form')?.classList.add('hidden');
    document.getElementById('video-form')?.classList.add('hidden');
    document.getElementById('success-screen')?.classList.add('hidden');
    document.getElementById('expired-screen')?.classList.remove('hidden');
    
    const timerPill = document.getElementById('timer-pill');
    if (timerPill) {
        timerPill.className = 'backdrop-blur-xl bg-slate-900/80 border border-slate-700/50 rounded-full shadow-2xl px-4 py-2 flex items-center justify-between opacity-75';
    }
    const pulseRing = document.getElementById('pulse-ring');
    if (pulseRing) pulseRing.classList.add('hidden');
    const pulseDot = document.getElementById('pulse-dot');
    if (pulseDot) pulseDot.className = 'relative inline-flex rounded-full h-2.5 w-2.5 bg-slate-500';
    
    const timerEl = document.getElementById('timer');
    if (timerEl) timerEl.textContent = 'Expired';

    // Disable all action cards
    document.querySelectorAll('.action-card, .action-btn').forEach(btn => {
        btn.disabled = true;
        btn.classList.add('opacity-50', 'cursor-not-allowed');
    });
}

function insertTemplate(text) {
    const input = document.getElementById('message-input');
    if (input) {
        input.value = text;
        const len = input.value.length;
        const charCount = document.getElementById('char-count');
        if (charCount) charCount.textContent = `${len} / 500`;
        input.classList.remove('border-red-500');
        input.focus();
    }
}

function showLoading(show) {
    const el = document.getElementById('loading');
    if (show) {
        el.classList.remove('hidden');
    } else {
        el.classList.add('hidden');
    }
}

// ---------------------------------------------------------------------------
// Action Handlers
// ---------------------------------------------------------------------------

function handleAction(actionType, actionId, label) {
    if (EXPIRES_IN <= 0) {
        showExpired();
        return;
    }

    switch (actionType) {
        case 'alert':
            triggerAlert(actionId);
            break;
        case 'message':
            showMessageForm(actionId);
            break;
        case 'voice':
            showVoiceForm(actionId);
            break;
        case 'location':
            shareCurrentLocation(actionId);
            break;
        case 'photo':
            showPhotoForm(actionId);
            break;
        case 'video':
            showVideoForm(actionId);
            break;
        default:
            console.warn('Unknown action type:', actionType);
    }
}

// ---------------------------------------------------------------------------
// Geolocation Sharing
// ---------------------------------------------------------------------------

function shareCurrentLocation(actionId) {
    if (!navigator.geolocation) {
        alert('Geolocation is not supported by your browser.');
        return;
    }

    showLoading(true);

    navigator.geolocation.getCurrentPosition(
        async (position) => {
            try {
                const resp = await fetch('/api/public/location', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        session_token: SESSION_TOKEN,
                        action_id: actionId || null,
                        latitude: position.coords.latitude,
                        longitude: position.coords.longitude,
                        accuracy: position.coords.accuracy || null,
                    }),
                });

                const data = await resp.json();

                if (resp.ok) {
                    showSuccess('Location Shared!', data.message);
                } else {
                    if (resp.status === 403) {
                        showExpired();
                    } else {
                        alert(data.detail || 'Failed to share location');
                    }
                }
            } catch (err) {
                console.error('Error sharing location:', err);
                alert('Network error. Please try again.');
            } finally {
                showLoading(false);
            }
        },
        (error) => {
            showLoading(false);
            console.error('Geolocation error:', error);
            if (error.code === error.PERMISSION_DENIED) {
                alert('Location permission was denied. Please enable location access to share your position.');
            } else {
                alert('Could not determine current location. Please verify device GPS is active.');
            }
        },
        {
            enableHighAccuracy: true,
            timeout: 10000,
            maximumAge: 0,
        }
    );
}


// ---------------------------------------------------------------------------
// Voice Recording (MediaRecorder API)
// ---------------------------------------------------------------------------

async function toggleRecording() {
    if (!isRecording) {
        await startVoiceRecording();
    } else {
        stopVoiceRecording();
    }
}

async function startVoiceRecording() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        audioChunks = [];

        const mimeTypes = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg', 'audio/mp4'];
        let selectedMime = mimeTypes.find(type => MediaRecorder.isTypeSupported(type)) || '';

        mediaRecorder = new MediaRecorder(stream, selectedMime ? { mimeType: selectedMime } : {});

        mediaRecorder.ondataavailable = (event) => {
            if (event.data.size > 0) {
                audioChunks.push(event.data);
            }
        };

        mediaRecorder.onstop = () => {
            recordedAudioBlob = new Blob(audioChunks, { type: selectedMime || 'audio/webm' });
            stream.getTracks().forEach(track => track.stop());

            const audioUrl = URL.createObjectURL(recordedAudioBlob);
            const preview = document.getElementById('audio-preview');
            preview.src = audioUrl;
            preview.classList.remove('hidden');

            document.getElementById('voice-actions').classList.remove('hidden');
            document.getElementById('voice-status').textContent = 'Recording ready to send';
        };

        mediaRecorder.start(100);
        isRecording = true;
        recordingSeconds = 0;

        document.getElementById('record-pulse').classList.remove('hidden');
        document.getElementById('mic-icon').classList.add('hidden');
        document.getElementById('stop-icon').classList.remove('hidden');
        document.getElementById('voice-status').textContent = 'Recording in progress... Tap to stop';
        document.getElementById('audio-preview').classList.add('hidden');
        document.getElementById('voice-actions').classList.add('hidden');

        recordingTimerInterval = setInterval(() => {
            recordingSeconds++;
            const mins = Math.floor(recordingSeconds / 60);
            const secs = recordingSeconds % 60;
            document.getElementById('voice-timer').textContent =
                `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;

            if (recordingSeconds >= 60) { // Max 60 seconds
                stopVoiceRecording();
            }
        }, 1000);

    } catch (err) {
        console.error('Microphone access denied or unsupported:', err);
        alert('Could not access microphone. Please grant audio permissions to record voice messages.');
    }
}

function stopVoiceRecording() {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
    }
    isRecording = false;
    clearInterval(recordingTimerInterval);

    document.getElementById('record-pulse').classList.add('hidden');
    document.getElementById('mic-icon').classList.remove('hidden');
    document.getElementById('stop-icon').classList.add('hidden');
}

function resetVoiceRecording() {
    stopVoiceRecording();
    recordedAudioBlob = null;
    audioChunks = [];
    recordingSeconds = 0;

    const timer = document.getElementById('voice-timer');
    if (timer) timer.textContent = '00:00';

    const status = document.getElementById('voice-status');
    if (status) status.textContent = 'Tap to start recording';

    const preview = document.getElementById('audio-preview');
    if (preview) {
        preview.pause();
        preview.src = '';
        preview.classList.add('hidden');
    }

    const actions = document.getElementById('voice-actions');
    if (actions) actions.classList.add('hidden');
}

async function sendVoiceRecording() {
    if (!recordedAudioBlob) {
        alert('Please record an audio message first.');
        return;
    }

    showLoading(true);

    try {
        const formData = new FormData();
        formData.append('session_token', SESSION_TOKEN);
        if (currentActionId) {
            formData.append('action_id', currentActionId);
        }
        formData.append('duration', recordingSeconds.toString());
        formData.append('audio_file', recordedAudioBlob, 'voice_message.webm');

        const resp = await fetch('/api/public/voice', {
            method: 'POST',
            body: formData,
        });

        const data = await resp.json();

        if (resp.ok) {
            showSuccess('Voice Sent!', data.message);
        } else {
            if (resp.status === 403) {
                showExpired();
            } else {
                alert(data.detail || 'Failed to send voice recording');
            }
        }
    } catch (err) {
        console.error('Error sending voice recording:', err);
        alert('Network error. Please try again.');
    } finally {
        showLoading(false);
    }
}

// ---------------------------------------------------------------------------
// API Calls (Alert & Text Message)
// ---------------------------------------------------------------------------

async function triggerAlert(actionId) {
    showLoading(true);
    try {
        const resp = await fetch('/api/public/action', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                session_token: SESSION_TOKEN,
                action_id: actionId || null,
            }),
        });

        const data = await resp.json();

        if (resp.ok) {
            showSuccess('Alert Sent!', data.message);
        } else {
            if (resp.status === 403) {
                showExpired();
            } else {
                alert(data.detail || 'Failed to send alert');
            }
        }
    } catch (err) {
        console.error('Error sending alert:', err);
        alert('Network error. Please try again.');
    } finally {
        showLoading(false);
    }
}

async function sendMessage() {
    const input = document.getElementById('message-input');
    const content = input.value.trim();

    if (!content) {
        input.classList.add('border-red-500');
        input.focus();
        return;
    }

    input.classList.remove('border-red-500');
    showLoading(true);

    try {
        const resp = await fetch('/api/public/message', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                session_token: SESSION_TOKEN,
                action_id: currentActionId || null,
                content: content,
            }),
        });

        const data = await resp.json();

        if (resp.ok) {
            showSuccess('Message Sent!', data.message);
        } else {
            if (resp.status === 403) {
                showExpired();
            } else {
                alert(data.detail || 'Failed to send message');
            }
        }
    } catch (err) {
        console.error('Error sending message:', err);
        alert('Network error. Please try again.');
    } finally {
        showLoading(false);
    }
}

// ---------------------------------------------------------------------------
// Photo Upload Handling
// ---------------------------------------------------------------------------

let selectedPhotoFile = null;

function onPhotoSelected(input) {
    if (input.files && input.files[0]) {
        selectedPhotoFile = input.files[0];
        const reader = new FileReader();
        reader.onload = (e) => {
            const preview = document.getElementById('photo-preview-img');
            const placeholder = document.getElementById('photo-placeholder');
            if (preview && placeholder) {
                preview.src = e.target.result;
                preview.classList.remove('hidden');
                placeholder.classList.add('hidden');
            }
            const sendBtn = document.getElementById('send-photo-btn');
            if (sendBtn) sendBtn.disabled = false;
        };
        reader.readAsDataURL(selectedPhotoFile);
    }
}

function resetPhotoSelection() {
    selectedPhotoFile = null;
    const input = document.getElementById('photo-input');
    if (input) input.value = '';
    const preview = document.getElementById('photo-preview-img');
    const placeholder = document.getElementById('photo-placeholder');
    if (preview) {
        preview.src = '';
        preview.classList.add('hidden');
    }
    if (placeholder) placeholder.classList.remove('hidden');
    const sendBtn = document.getElementById('send-photo-btn');
    if (sendBtn) sendBtn.disabled = true;
    const caption = document.getElementById('photo-caption');
    if (caption) caption.value = '';
}

async function sendPhoto() {
    if (!selectedPhotoFile) {
        alert('Please choose or take a photo first.');
        return;
    }

    showLoading(true);

    try {
        const formData = new FormData();
        formData.append('session_token', SESSION_TOKEN);
        if (currentActionId) {
            formData.append('action_id', currentActionId);
        }
        const captionInput = document.getElementById('photo-caption');
        if (captionInput && captionInput.value.trim()) {
            formData.append('caption', captionInput.value.trim());
        }
        formData.append('photo_file', selectedPhotoFile, selectedPhotoFile.name || 'photo.jpg');

        const resp = await fetch('/api/public/photo', {
            method: 'POST',
            body: formData,
        });

        const data = await resp.json();

        if (resp.ok) {
            showSuccess('Photo Sent!', data.message);
        } else {
            if (resp.status === 403) {
                showExpired();
            } else {
                alert(data.detail || 'Failed to send photo');
            }
        }
    } catch (err) {
        console.error('Error sending photo:', err);
        alert('Network error. Please try again.');
    } finally {
        showLoading(false);
    }
}

// ---------------------------------------------------------------------------
// Video Upload Handling
// ---------------------------------------------------------------------------

let selectedVideoFile = null;

function onVideoSelected(input) {
    if (input.files && input.files[0]) {
        selectedVideoFile = input.files[0];
        const preview = document.getElementById('video-preview-player');
        const placeholder = document.getElementById('video-placeholder');
        if (preview && placeholder) {
            const videoUrl = URL.createObjectURL(selectedVideoFile);
            preview.src = videoUrl;
            preview.classList.remove('hidden');
            placeholder.classList.add('hidden');
        }
        const sendBtn = document.getElementById('send-video-btn');
        if (sendBtn) sendBtn.disabled = false;
    }
}

function resetVideoSelection() {
    selectedVideoFile = null;
    const input = document.getElementById('video-input');
    if (input) input.value = '';
    const preview = document.getElementById('video-preview-player');
    const placeholder = document.getElementById('video-placeholder');
    if (preview) {
        preview.pause();
        preview.src = '';
        preview.classList.add('hidden');
    }
    if (placeholder) placeholder.classList.remove('hidden');
    const sendBtn = document.getElementById('send-video-btn');
    if (sendBtn) sendBtn.disabled = true;
    const caption = document.getElementById('video-caption');
    if (caption) caption.value = '';
}

async function sendVideo() {
    if (!selectedVideoFile) {
        alert('Please choose or record a video first.');
        return;
    }

    showLoading(true);

    try {
        const formData = new FormData();
        formData.append('session_token', SESSION_TOKEN);
        if (currentActionId) {
            formData.append('action_id', currentActionId);
        }
        const captionInput = document.getElementById('video-caption');
        if (captionInput && captionInput.value.trim()) {
            formData.append('caption', captionInput.value.trim());
        }
        formData.append('video_file', selectedVideoFile, selectedVideoFile.name || 'video.mp4');

        const resp = await fetch('/api/public/video', {
            method: 'POST',
            body: formData,
        });

        const data = await resp.json();

        if (resp.ok) {
            showSuccess('Video Sent!', data.message);
        } else {
            if (resp.status === 403) {
                showExpired();
            } else {
                alert(data.detail || 'Failed to send video');
            }
        }
    } catch (err) {
        console.error('Error sending video:', err);
        alert('Network error. Please try again.');
    } finally {
        showLoading(false);
    }
}

// ---------------------------------------------------------------------------
// Event Listeners
// ---------------------------------------------------------------------------

document.addEventListener('DOMContentLoaded', () => {
    // Start the session countdown timer
    startTimer();

    // Character counter for message input
    const messageInput = document.getElementById('message-input');
    if (messageInput) {
        messageInput.addEventListener('input', () => {
            const len = messageInput.value.length;
            document.getElementById('char-count').textContent = `${len} / 500`;
            messageInput.classList.remove('border-red-500');
        });
    }
});
