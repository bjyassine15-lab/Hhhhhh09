package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class VoiceState {
    INACTIVE,
    INITIALIZING,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

object VoiceAssistantManager {
    private const val TAG = "VoiceAssistantManager"

    private val _state = MutableStateFlow(VoiceState.INACTIVE)
    val state = _state.asStateFlow()

    private val _statusText = MutableStateFlow("المساعد الصوتي غير نشط")
    val statusText = _statusText.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText = _recognizedText.asStateFlow()

    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var sessionScope: CoroutineScope? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    private val audioBufferChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private var accumulatedResponseText = ""
    private var onCommandExtractedListener: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startSession(
        context: Context,
        apiKey: String,
        systemInstruction: String,
        onCommandExtracted: (String) -> Unit
    ) {
        if (apiKey.isBlank()) {
            _state.value = VoiceState.ERROR
            _statusText.value = "الرجاء تكوين مفتاح API الخاص بـ Gemini أولاً من المخزن أو الإعدادات."
            return
        }

        // Clean up previous sessions if any
        stopSession()

        Log.d(TAG, "Starting Gemini Live WebSocket Session...")
        _state.value = VoiceState.INITIALIZING
        _statusText.value = "جاري الاتصال الصوتي بـ Gemini Live..."
        _recognizedText.value = ""
        accumulatedResponseText = ""
        onCommandExtractedListener = onCommandExtracted

        sessionScope = CoroutineScope(Dispatchers.Default)

        // Initialize OkHttp with high timeout
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .build()
        okHttpClient = client

        // Gemini Multimodal Live API endpoint over WebSocket
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Connection Opened successfully.")
                sendSetupMessage(webSocket, systemInstruction)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.localizedMessage}", t)
                _state.value = VoiceState.ERROR
                _statusText.value = "حدث خطأ في الاتصال بالبث المباشر: ${t.localizedMessage}"
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Connection Closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Connection Closed: $code / $reason")
                _state.value = VoiceState.INACTIVE
            }
        })
    }

    private fun sendSetupMessage(webSocket: WebSocket, systemInstruction: String) {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/gemini-2.0-flash-exp")
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Aoede")
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemInstruction)
                            })
                        })
                    })
                })
            }
            webSocket.send(setupJson.toString())
            Log.d(TAG, "Setup config message sent successfully. Waiting for setupComplete response...")

            sessionScope?.launch(Dispatchers.Main) {
                _statusText.value = "تم إرسال حزمة التكوين، بانتظار تأكيد الخادم لقنوات الصوت..."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = VoiceState.ERROR
            _statusText.value = "فشل في إرسال حزمة التكوين."
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioHardware(ws: WebSocket) {
        // 1. Setup AudioTrack for Playback (24kHz Mono 16-bit PCM)
        try {
            val sampleRateOut = 24000
            val minBufOut = AudioTrack.getMinBufferSize(
                sampleRateOut,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSizeOut = minBufOut.coerceAtLeast(4096)

            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRateOut)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSizeOut)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack state is uninitialized")
                throw IllegalStateException("AudioTrack initialization failed")
            }

            audioTrack = track
            track.play()

            playbackJob = sessionScope?.launch(Dispatchers.Default) {
                try {
                    for (pcmBytes in audioBufferChannel) {
                        track.write(pcmBytes, 0, pcmBytes.size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Playback error: ${e.localizedMessage}")
                }
            }
            Log.d(TAG, "AudioTrack initiated and running successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating AudioTrack: ${e.localizedMessage}")
        }

        // 2. Setup AudioRecord for Microphone streaming input (16kHz Mono 16bit PCM)
        try {
            val sampleRateIn = 16000
            val minBufIn = AudioRecord.getMinBufferSize(
                sampleRateIn,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSizeIn = minBufIn.coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateIn,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeIn
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord state is uninitialized")
                throw IllegalStateException("AudioRecord initialization failed")
            }

            audioRecord = recorder
            recorder.startRecording()
            Log.d(TAG, "AudioRecord started recording successfully.")

            recordingJob = sessionScope?.launch(Dispatchers.IO) {
                val pcmChunkSize = 1024 // Low latency 32ms chunk size at 16kHz Mono 16bit
                val buffer = ByteArray(pcmChunkSize)
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val pcmBytes = buffer.copyOf(read)
                        val base64Data = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)

                        val inputJson = JSONObject().apply {
                            put("realtimeInput", JSONObject().apply {
                                put("mediaChunks", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("mimeType", "audio/pcm")
                                        put("data", base64Data)
                                    })
                                })
                            })
                        }
                        try {
                            ws.send(inputJson.toString())
                        } catch (e: Exception) {
                            Log.e(TAG, "WebSocket write error: ${e.localizedMessage}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.localizedMessage}")
            _state.value = VoiceState.ERROR
            _statusText.value = "لم نتمكن من الوصول للميكروفون لتنفيذ البث المباشر."
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Check if setup is complete
            val setupComplete = json.optJSONObject("setupComplete")
            if (setupComplete != null) {
                Log.d(TAG, "Gemini Live setup complete received from server. Starting audio hardware...")
                sessionScope?.launch(Dispatchers.Main) {
                    _state.value = VoiceState.LISTENING
                    _statusText.value = "البث المباشر نشط وصوت Gemini يتحدث، تكلم الآن..."
                    webSocket?.let { startAudioHardware(it) }
                }
                return
            }

            val serverContent = json.optJSONObject("serverContent")
            if (serverContent != null) {
                // If model of serverContent says interrupted, immediately flush playback queue!
                val interrupted = serverContent.optBoolean("interrupted", false)
                if (interrupted) {
                    Log.d(TAG, "Gemini detected interruption! Flushing local audio playbacks...")
                    clearPlaybackBuffer()
                    accumulatedResponseText = ""
                    _recognizedText.value = "تمت المقاطعة صوتاً..."
                    _state.value = VoiceState.LISTENING
                    _statusText.value = "يستمع..."
                    return
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    _state.value = VoiceState.SPEAKING
                    _statusText.value = "Gemini يجيب صوتياً الآن..."

                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue

                            val textPart = part.optString("text")
                            if (!textPart.isNullOrEmpty()) {
                                accumulatedResponseText += textPart
                                _recognizedText.value = accumulatedResponseText
                            }

                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val b64Data = inlineData.optString("data", "")
                                if (b64Data.isNotEmpty()) {
                                    try {
                                        val pcmBytes = Base64.decode(b64Data, Base64.DEFAULT)
                                        audioBufferChannel.trySend(pcmBytes)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                }

                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete) {
                    _state.value = VoiceState.LISTENING
                    _statusText.value = "البث نشط ويستمع إليك..."

                    val fullText = accumulatedResponseText
                    if (fullText.contains("COMMAND:")) {
                        sessionScope?.launch(Dispatchers.Main) {
                            onCommandExtractedListener?.invoke(fullText)
                        }
                    }
                    accumulatedResponseText = "" // reset turn
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleServerMessage error: ${e.localizedMessage}")
        }
    }

    private fun clearPlaybackBuffer() {
        while (audioBufferChannel.tryReceive().isSuccess) { /* flush */ }
        try {
            audioTrack?.apply {
                stop()
                flush()
                play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopSession() {
        _state.value = VoiceState.INACTIVE
        _statusText.value = "المساعد الصوتي غير نشط"
        _recognizedText.value = ""

        recordingJob?.cancel()
        recordingJob = null

        playbackJob?.cancel()
        playbackJob = null

        sessionScope?.cancel()
        sessionScope = null

        while (audioBufferChannel.tryReceive().isSuccess) { /* clear */ }

        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    try { stop() } catch(e: Exception){}
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null

        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    try {
                        stop()
                        flush()
                    } catch(e: Exception){}
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null

        try {
            webSocket?.close(1000, "App closed session")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webSocket = null
        okHttpClient = null
    }

    fun destroy() {
        stopSession()
    }
}
