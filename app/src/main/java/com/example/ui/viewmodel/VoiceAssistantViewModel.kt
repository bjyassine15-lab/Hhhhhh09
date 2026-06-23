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
}
