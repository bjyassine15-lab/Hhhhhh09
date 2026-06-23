package com.example.data.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import android.util.Log
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

enum class VoiceState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LISTENING,  // Mic capturing active
    SPEAKING,   // Model audio streaming & playing
    ERROR
}

class VoiceAssistantManager private constructor() {

    companion object {
        private const val TAG = "VoiceAssistantManager"
        
        @Volatile
        private var instance: VoiceAssistantManager? = null

        fun getInstance(): VoiceAssistantManager {
            return instance ?: synchronized(this) {
                instance ?: VoiceAssistantManager().also { instance = it }
            }
        }
    }

    private val _state = MutableStateFlow(VoiceState.DISCONNECTED)
    val state = _state.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    private val _liveTranscript = MutableStateFlow<String>("")
    val liveTranscript = _liveTranscript.asStateFlow()

    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    
    private var recordJob: Job? = null
    private var playbackJob: Job? = null
    private var resetStateJob: Job? = null
    private val audioQueue = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isRecording = false
    private var isPlaying = false

    // Audio Capture Config (Input: 16kHz, mono, 16bit)
    private val inputSampleRate = 16000
    private val inputChannelConfig = AudioFormat.CHANNEL_IN_MONO
    private val inputAudioFormat = AudioFormat.ENCODING_PCM_16BIT

    // Audio Playback Config (Output: 24kHz, mono, 16bit)
    private val outputSampleRate = 24000
    private val outputChannelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val outputAudioFormat = AudioFormat.ENCODING_PCM_16BIT

    init {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket infinite timeout
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun startSession(context: Context) {
        if (_state.value != VoiceState.DISCONNECTED && _state.value != VoiceState.ERROR) {
            Log.d(TAG, "Session already active or starting")
            return
        }

        _errorFlow.value = null
        _liveTranscript.value = ""
        _state.value = VoiceState.CONNECTING

        val apiKey = VoiceAssistantPreferences.getVoiceApiKey(context)
        val model = VoiceAssistantPreferences.getVoiceModel(context)
        val voiceName = VoiceAssistantPreferences.getVoiceName(context)

        if (apiKey.isBlank()) {
            _errorFlow.value = "مفتاح Gemini API غير موجود بصفحة إعدادات المساعد الصوتي."
            _state.value = VoiceState.ERROR
            return
        }

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        Log.d(TAG, "Connecting to: $url with model: $model")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Opened")
                _state.value = VoiceState.CONNECTED
                
                // 1. Start Background Playback Consumer
                startPlaybackConsumer(context)

                // 2. Send Setup Configuration Message
                sendSetupMessage(webSocket, model, voiceName)
                
                // 3. Start Microphone Recording
                startRecording(context)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason ($code)")
                if (code != 1000) {
                    val friendlyReason = when (code) {
                        1008 -> "رمز 1008 (انتهاك السياسة): النموذج غير مدعوم للاتصال الصوتي المباشر. يرجى اختيار 'gemini-2.0-flash' أو 'gemini-3.1-flash-live-preview' في الإعدادات."
                        else -> reason.ifBlank { "بدون تفاصيل إضافية" }
                    }
                    _errorFlow.value = "انقطع الاتصال بالخادم: $friendlyReason (رمز: $code)"
                    _state.value = VoiceState.ERROR
                }
                stopSession()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                val responseMsg = response?.let { " (رمز الاستجابة: ${it.code} - ${it.message})" } ?: ""
                val errorMessage = when {
                    t.message?.contains("1008") == true -> "رمز 1008 (انتهاك السياسة): النموذج غير مدعوم للاتصال الصوتي المباشر. يرجى التأكد من اختيار أحد طرازات البث المباشر المعتمدة."
                    response?.code == 403 -> "رمز 403 (غير مصرح به): يرجى مراجعة مفتاح الـ API والتحقق من صلاحيته لنموذج الصوت المباشر."
                    else -> t.localizedMessage ?: "يرجى التحقق من اتصال الإنترنت وصحة مفتاح API الخاص بك."
                }
                _errorFlow.value = "فشل الاتصال: $errorMessage$responseMsg"
                _state.value = VoiceState.ERROR
                stopSessionInternal()
            }
        })
    }

    private fun sendSetupMessage(webSocket: WebSocket, model: String, voiceName: String) {
        val formattedModel = if (model.startsWith("models/")) model else "models/$model"
        try {
            val setupObj = JSONObject().apply {
                put("model", formattedModel)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })
            }
            val rootMsg = JSONObject().apply {
                put("setup", setupObj)
            }
            val jsonStr = rootMsg.toString()
            Log.d(TAG, "Sending Setup: $jsonStr")
            webSocket.send(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create setup json", e)
            _errorFlow.value = "فشل تنظيم بيانات المساعد الصوتي: ${e.localizedMessage}"
            _state.value = VoiceState.ERROR
            stopSessionInternal()
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)
            
            // Check for error payload
            if (json.has("error")) {
                val errObj = json.getJSONObject("error")
                val errMsg = errObj.optString("message", "خطأ غير معروف")
                _errorFlow.value = "خطأ خادم Gemini: $errMsg"
                _state.value = VoiceState.ERROR
                Log.e(TAG, "Server error: $errMsg")
                stopSessionInternal()
                return
            }

            if (json.has("serverContent")) {
                val serverContent = json.optJSONObject("serverContent") ?: return
                
                // Check if model turn has some direct output
                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts") ?: return
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        
                        // Parse live text transcript if streamed by the model
                        if (part.has("text")) {
                            val textStr = part.optString("text", "")
                            if (textStr.isNotEmpty()) {
                                _liveTranscript.value += textStr
                            }
                        }
                        
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val mimeType = inlineData.optString("mimeType", "")
                            val base64Data = inlineData.optString("data", "")
                            
                            if (base64Data.isNotEmpty() && mimeType.startsWith("audio/")) {
                                val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                enqueueAudioBytes(audioBytes)
                            }
                        }
                    }
                }

                // If turn is complete, check state
                val turnComplete = serverContent.optBoolean("turnComplete", false)
                val interrupted = serverContent.optBoolean("interrupted", false)
                
                if (interrupted) {
                    Log.d(TAG, "Interrupted by User speech (Barge-in VAD)")
                    clearPlaybackQueue()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse sever message", e)
        }
    }

    private fun startRecording(context: Context) {
        if (isRecording) return
        isRecording = true
        _state.value = VoiceState.LISTENING

        recordJob = managerScope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(inputSampleRate, inputChannelConfig, inputAudioFormat)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size for AudioRecord")
                return@launch
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val attributionContext = context.createAttributionContext("microphone")
                    audioRecord = AudioRecord.Builder()
                        .setContext(attributionContext)
                        .setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(inputAudioFormat)
                                .setSampleRate(inputSampleRate)
                                .setChannelMask(inputChannelConfig)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize * 2)
                        .build()
                } else {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        inputSampleRate,
                        inputChannelConfig,
                        inputAudioFormat,
                        bufferSize * 2
                    )
                }

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord could not shut initialize. Please verify permissions.")
                    _errorFlow.value = "فشل تشغيل الميكروفون. يرجى تفعيل صلاحية تسجيل الصوت للمتجر."
                    _state.value = VoiceState.ERROR
                    stopSession()
                    return@launch
                }

                // Enable Acoustic Echo Canceler if available on device
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        val echoCanceler = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
                        echoCanceler?.enabled = true
                        Log.d(TAG, "Acoustic Echo Canceler successfully enabled")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to initialize Acoustic Echo Canceler: ${e.message}")
                    }
                }

                audioRecord?.startRecording()
                val readBuffer = ByteArray(2048) // Around 64ms of audio

                while (isRecording && activeContextCoroutines()) {
                    val readBytes = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (readBytes > 0) {
                        // Dynamically update UI state to speaking/listening based on active output vs input
                        if (!isPlaying && _state.value == VoiceState.SPEAKING) {
                            _state.value = VoiceState.LISTENING
                        }
                        
                        // Send PCM bytes via Base64 to Socket if WebSocket is connected
                        val actualBytes = readBuffer.copyOfRange(0, readBytes)
                        val base64Audio = Base64.encodeToString(actualBytes, Base64.NO_WRAP)
                        
                        sendAudioChunk(base64Audio)
                    }
                    delay(40L) // Limit loop frequency
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for recording", e)
                _errorFlow.value = "يرجى منح صلاحية الميكروفون من إعدادات الهاتف لتشغيل الاتصال الصوتي."
                _state.value = VoiceState.ERROR
            } catch (e: Exception) {
                Log.e(TAG, "Error recording audio", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) { /* ignore */ }
                audioRecord = null
                isRecording = false
            }
        }
    }

    private fun activeContextCoroutines(): Boolean {
        return webSocket != null && _state.value != VoiceState.DISCONNECTED && _state.value != VoiceState.ERROR
    }

    private fun sendAudioChunk(base64Data: String) {
        val socket = webSocket ?: return
        try {
            val chunkObj = JSONObject().apply {
                put("mimeType", "audio/pcm")
                put("data", base64Data)
            }
            val mediaChunks = JSONArray().apply {
                put(chunkObj)
            }
            val realtimeInputObj = JSONObject().apply {
                put("mediaChunks", mediaChunks)
            }
            val rootMsg = JSONObject().apply {
                put("realtimeInput", realtimeInputObj)
            }
            socket.send(rootMsg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio chunk", e)
        }
    }

    private fun initAudioTrack(context: Context) {
        if (audioTrack != null) return
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                outputSampleRate,
                outputChannelConfig,
                outputAudioFormat
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val attributionContext = context.createAttributionContext("microphone")
                audioTrack = AudioTrack.Builder()
                    .setContext(attributionContext)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(outputAudioFormat)
                            .setSampleRate(outputSampleRate)
                            .setChannelMask(outputChannelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    outputSampleRate,
                    outputChannelConfig,
                    outputAudioFormat,
                    minBufSize * 2,
                    AudioTrack.MODE_STREAM
                )
            }
            
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
            } else {
                Log.e(TAG, "AudioTrack setup failure")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    private fun startPlaybackConsumer(context: Context) {
        playbackJob?.cancel()
        playbackJob = managerScope.launch {
            initAudioTrack(context)
            val track = audioTrack ?: return@launch
            try {
                // Loop through the channel to consume incoming raw PCM buffers
                for (audioBytes in audioQueue) {
                    track.write(audioBytes, 0, audioBytes.size)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Playback consumer cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in playback consumer", e)
            }
        }
    }

    private fun enqueueAudioBytes(bytes: ByteArray) {
        managerScope.launch {
            audioQueue.send(bytes)
            
            // Cancel any pending turn-off details
            resetStateJob?.cancel()

            if (_state.value != VoiceState.SPEAKING && _state.value != VoiceState.ERROR && _state.value != VoiceState.DISCONNECTED) {
                _state.value = VoiceState.SPEAKING
            }
            isPlaying = true
            
            // Wait 500ms. If no subsequent audio chunk arrives, revert state to LISTENING
            resetStateJob = managerScope.launch {
                delay(500L)
                isPlaying = false
                if (_state.value == VoiceState.SPEAKING) {
                    _state.value = VoiceState.LISTENING
                }
            }
        }
    }

    private fun clearPlaybackQueue() {
        Log.d(TAG, "Clearing playback queue")
        resetStateJob?.cancel()
        // Drain channel
        while (true) {
            val result = audioQueue.tryReceive()
            if (result.isFailure) break
        }
        isPlaying = false
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack", e)
        }
        if (_state.value == VoiceState.SPEAKING) {
            _state.value = VoiceState.LISTENING
        }
    }

    fun stopSession() {
        stopSessionInternal()
        if (_state.value != VoiceState.ERROR) {
            _state.value = VoiceState.DISCONNECTED
        }
    }

    private fun stopSessionInternal() {
        Log.d(TAG, "Stopping session internal")
        isRecording = false
        isPlaying = false
        resetStateJob?.cancel()
        
        try {
            recordJob?.cancel()
            recordJob = null
        } catch (e: Exception) { /* ignore */ }

        try {
            playbackJob?.cancel()
            playbackJob = null
        } catch (e: Exception) { /* ignore */ }

        // Drain channel items remaining
        while (true) {
            val result = audioQueue.tryReceive()
            if (result.isFailure) break
        }

        try {
            webSocket?.close(1000, "Done")
            webSocket = null
        } catch (e: Exception) { /* ignore */ }

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) { /* ignore */ }
        audioRecord = null

        try {
            audioTrack?.apply {
                stop()
                flush()
                release()
            }
        } catch (e: Exception) { /* ignore */ }
        audioTrack = null
    }
}
