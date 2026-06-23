package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.util.VoiceAssistantManager
import com.example.data.util.VoiceAssistantPreferences
import com.example.data.util.VoiceState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class VoiceAssistantViewModel : ViewModel() {

    private val voiceManager = VoiceAssistantManager.getInstance()

    val currentVoiceState: StateFlow<VoiceState> = voiceManager.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VoiceState.DISCONNECTED
        )

    val currentErrorMessage: StateFlow<String?> = voiceManager.errorFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val currentLiveTranscript: StateFlow<String> = voiceManager.liveTranscript
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    fun startVoiceSession(context: Context) {
        voiceManager.startSession(context)
    }

    fun stopVoiceSession() {
        voiceManager.stopSession()
    }

    // Helper functions to retrieve settings
    fun getSavedVoiceApiKey(context: Context): String {
        return VoiceAssistantPreferences.getVoiceApiKey(context)
    }

    fun saveVoiceApiKey(context: Context, key: String) {
        VoiceAssistantPreferences.saveVoiceApiKey(context, key)
    }

    fun getVoiceModel(context: Context): String {
        return VoiceAssistantPreferences.getVoiceModel(context)
    }

    fun saveVoiceModel(context: Context, model: String) {
        VoiceAssistantPreferences.saveVoiceModel(context, model)
    }

    fun getVoiceName(context: Context): String {
        return VoiceAssistantPreferences.getVoiceName(context)
    }

    fun saveVoiceName(context: Context, voiceName: String) {
        VoiceAssistantPreferences.saveVoiceName(context, voiceName)
    }

    fun testVoiceConnection(
        context: Context,
        apiKey: String,
        model: String,
        voiceName: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (apiKey.isBlank()) {
            onResult(false, "مفتاح API لا يمكن أن يكون فارغاً.")
            return
        }
        
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
            
        var completed = false
        val formattedModel = if (model.startsWith("models/")) model else "models/$model"
        
        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (completed) return
                // Try sending setup to see if model or other issues exist
                try {
                    val setupObj = JSONObject().apply {
                        put("model", formattedModel)
                        put("generationConfig", JSONObject().apply {
                            put("responseModalities", JSONArray(listOf("AUDIO")))
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
                    webSocket.send(rootMsg.toString())
                    
                    // Connected successfully and accepted key! Let's wait a second for any error response
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!completed) {
                            completed = true
                            webSocket.close(1000, "Test Done")
                            onResult(true, "✅ تم الاتصال وسحب تهيئة الصوت بنجاح!")
                        }
                    }, 1200)

                } catch (e: Exception) {
                    completed = true
                    webSocket.close(1001, "Error Setup")
                    onResult(false, "❌ فشل إرسال تهيئة الإعدادات: ${e.localizedMessage}")
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (completed) return
                try {
                    val json = JSONObject(text)
                    if (json.has("error")) {
                        completed = true
                        val errObj = json.getJSONObject("error")
                        val errMsg = errObj.optString("message", "خطأ غير معروف")
                        webSocket.close(1001, "Server Error")
                        onResult(false, "❌ رفض خادم Google: $errMsg")
                    }
                } catch (e: Exception) {
                    // Ignore parsing failures of partial responses
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (completed) return
                completed = true
                val responseMsg = response?.let { " (رمز الحالة: ${it.code} - ${it.message})" } ?: ""
                onResult(false, "❌ فشل الاتصال: ${t.localizedMessage ?: "تأكد من الاتصال ومن صلاحية المفتاح"}$responseMsg")
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (completed) return
                if (code != 1000) {
                    completed = true
                    onResult(false, "❌ تم قطع الاتصال من قبل الخادم: $reason (رمز: $code)")
                }
            }
        })
    }
}
