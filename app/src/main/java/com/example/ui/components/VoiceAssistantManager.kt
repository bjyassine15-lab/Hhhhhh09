package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

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

    private val _statusText = MutableStateFlow("")
    val statusText = _statusText.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText = _recognizedText.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isTtsSpeaking = false

    private var onSpeechResultListener: ((String) -> Unit)? = null
    private var activeContext: Context? = null

    fun initializeTts(context: Context) {
        if (textToSpeech != null) return
        activeContext = context.applicationContext
        
        _state.value = VoiceState.INITIALIZING
        _statusText.value = "جاري تهيئة الصوت..."
        
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeAr = Locale("ar")
                val result = textToSpeech?.setLanguage(localeAr)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Arabic language is not supported on this device.")
                    textToSpeech?.language = Locale.getDefault()
                }
                isTtsInitialized = true
                _state.value = VoiceState.INACTIVE
                _statusText.value = "المساعد الصوتي جاهز"
                
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isTtsSpeaking = true
                        _state.value = VoiceState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        isTtsSpeaking = false
                        _state.value = VoiceState.INACTIVE
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isTtsSpeaking = false
                        _state.value = VoiceState.INACTIVE
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        isTtsSpeaking = false
                        _state.value = VoiceState.INACTIVE
                    }
                })
            } else {
                Log.e(TAG, "TTS Initialization failed.")
                _state.value = VoiceState.ERROR
                _statusText.value = "فشل تهيئة التحدث المباشر"
            }
        }
    }

    fun startListening(context: Context, onResult: (String) -> Unit) {
        activeContext = context
        onSpeechResultListener = onResult
        
        // Ensure TTS is initialized
        initializeTts(context)
        
        // Stop any current speaking before listening
        stopSpeaking()

        _recognizedText.value = ""
        _state.value = VoiceState.LISTENING
        _statusText.value = "جاري فتح الميكروفون والاصغاء..."

        try {
            // SpeechRecognizer needs to be created and invoked on Main Thread
            if (speechRecognizer != null) {
                speechRecognizer?.destroy()
                speechRecognizer = null
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = VoiceState.LISTENING
                        _statusText.value = "تحدث الآن، المساعد الصوتي يستمع إليك..."
                    }

                    override fun onBeginningOfSpeech() {
                        _state.value = VoiceState.LISTENING
                        _statusText.value = "جاري التقاط موجات صوتك..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Visual feedback can use this rms value if needed
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _state.value = VoiceState.PROCESSING
                        _statusText.value = "جاري تجميع الكلمات وتحليلها..."
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "خطأ في تسجيل الصوت"
                            SpeechRecognizer.ERROR_CLIENT -> "خطأ في الاتصال بالهاتف"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية استخدام الميكروفون مرفوضة"
                            SpeechRecognizer.ERROR_NETWORK -> "خطأ في اتصال الشبكة"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انتهت مهلة اتصال الشبكة"
                            SpeechRecognizer.ERROR_NO_MATCH -> "لم نتمكن من فهم الكلام، يرجى إعادة المحاولة"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "المعالج مشغول حالياً"
                            SpeechRecognizer.ERROR_SERVER -> "خطأ من خادم التعرف على الصوت"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم يتم رصد أي كلام، يرجى تشغيل الميكروفون والتحدث"
                            else -> "حدث خطأ غير معروف في رصد الصوت"
                        }
                        
                        Log.e(TAG, "Speech recognition error code: $error - $message")
                        _state.value = VoiceState.ERROR
                        _statusText.value = message
                        
                        // Speak error feedback locally
                        speak(message, context)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val textResult = matches?.firstOrNull()
                        if (!textResult.isNullOrEmpty()) {
                            _recognizedText.value = textResult
                            _state.value = VoiceState.PROCESSING
                            _statusText.value = "سمعتك تقول: \"$textResult\""
                            onSpeechResultListener?.invoke(textResult)
                        } else {
                            _state.value = VoiceState.ERROR
                            _statusText.value = "عذراً، لم يتم رصد أي نص مفهوم."
                            speak("عذراً، لم أفهم كلامك جيداً. يرجى إعادة المحاولة.", context)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        if (partial.isNotEmpty()) {
                            _recognizedText.value = partial
                            _statusText.value = "جاري الاستماع: $partial..."
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-AE") // Gulf / General Arabic profile
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ar")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            
            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = VoiceState.ERROR
            _statusText.value = "تعذر تشغيل خدمة الميكروفون المدمجة"
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(text: String, context: Context) {
        val cleanText = text.substringBefore("COMMAND:").trim()
        if (cleanText.isEmpty()) return
        
        initializeTts(context)
        
        if (isTtsInitialized) {
            _state.value = VoiceState.SPEAKING
            _statusText.value = "جاري التحدث مع العميل..."
            textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "POS_SPEECH_ID")
        } else {
            // Try again after a short delay
            _statusText.value = "جاري تحضير المحرك الصوتي..."
        }
    }

    fun stopSpeaking() {
        try {
            if (isTtsSpeaking) {
                textToSpeech?.stop()
                isTtsSpeaking = false
                _state.value = VoiceState.INACTIVE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            textToSpeech?.shutdown()
            textToSpeech = null
            isTtsInitialized = false
            _state.value = VoiceState.INACTIVE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
