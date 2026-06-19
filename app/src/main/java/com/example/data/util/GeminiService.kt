package com.example.data.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Independent Data Models for Gemini REST API ---

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

// --- Retrofit Interface ---
interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

/**
 * Isolated service for the IA Advisor.
 * Implements strict Error Resilience, retry policy, and Key Gatekeeper verification.
 */
object GeminiService {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApi::class.java)
    }

    // --- SharedPreferences Helpers for the API Key with Resilient Encryption fallback ---
    private const val PREFS_NAME = "smart_cashier_ai_prefs"
    private const val KEY_API_KEY = "gemini_api_key_pos"

    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "secure_smart_cashier_ai_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful resilient fallback: standard app sandboxed preferences
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getSavedApiKey(context: Context): String {
        val securePrefs = getEncryptedPrefs(context)
        var key = securePrefs.getString(KEY_API_KEY, "")
        // Migrator layer: transparently read and upgrade from legacy plain configuration
        if (key.isNullOrBlank()) {
            val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            key = plainPrefs.getString(KEY_API_KEY, "")
            if (!key.isNullOrBlank()) {
                securePrefs.edit().putString(KEY_API_KEY, key.trim()).apply()
            }
        }
        return key.orEmpty().trim()
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val securePrefs = getEncryptedPrefs(context)
        securePrefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
        
        // Wipe legacy insecure reference
        val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (plainPrefs.contains(KEY_API_KEY)) {
            plainPrefs.edit().remove(KEY_API_KEY).apply()
        }
    }

    /**
     * Silent API Verification Check (The Gatekeeper).
     * Performs a very fast, tiny check with the entered key to test validity before launching chat.
     */
    suspend fun verifyApiKey(apiKey: String): Boolean {
        return verifyApiKeyDetailed(apiKey) == null
    }

    /**
     * Silent API Verification Check (The Gatekeeper) returning detailed error if any.
     * Returns null if success, or detailed error message string if failed.
     */
    suspend fun verifyApiKeyDetailed(apiKey: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "مفتاح الـ API فارغ."
        try {
            val testRequest = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = "Hi")))
                )
            )
            val response = api.generateContent(apiKey, testRequest)
            val textResult = response.candidates?.getOrNull(0)?.content?.parts?.getOrNull(0)?.text
            if (textResult.isNullOrBlank()) {
                "Connection succeeded, but AI generation response was empty."
            } else {
                null // null indicates success
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val rawMessage = e.localizedMessage ?: e.message ?: e.toString()
            val baseMessage = rawMessage.lowercase()
            when {
                baseMessage.contains("unable to resolve host") || baseMessage.contains("unknownhostexception") || baseMessage.contains("connect") -> 
                    "No internet connection (check your network setup)."
                baseMessage.contains("400") -> "Key incorrect or unsupported format (HTTP 400 Bad Request). Raw API Response: $rawMessage"
                baseMessage.contains("403") -> "Key invalid or unauthorized (HTTP 403 Forbidden). Raw API Response: $rawMessage"
                baseMessage.contains("503") -> "Gemini API unavailable temporarily (HTTP 503 Service Unavailable). Raw API Response: $rawMessage"
                baseMessage.contains("429") -> "Quota exceeded for this API key (HTTP 429 Too Many Requests). Raw API Response: $rawMessage"
                baseMessage.contains("timeout") || baseMessage.contains("timeout") -> "Request timed out (Timeout)."
                else -> "Detailed exception from Google: $rawMessage"
            }
        }
    }

    /**
     * Chat Prompt Execution with Retry Policy and Custom Error Handling.
     */
    suspend fun getAdvice(
        apiKey: String,
        prompt: String,
        systemInstructionText: String,
        dbSummaryContext: String,
        history: List<Pair<String, String>> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val contentsList = mutableListOf<GeminiContent>()

        // Add history turns (User / Model)
        history.forEach { (sender, msg) ->
            val rolePart = GeminiPart(text = msg)
            contentsList.add(GeminiContent(parts = listOf(rolePart))) 
        }

        // Add contemporary context + prompt
        val fullMessagePrompt = "$dbSummaryContext\n\nامسار الحالي من المستخدم:\n$prompt"
        contentsList.add(GeminiContent(parts = listOf(GeminiPart(text = fullMessagePrompt))))

        val request = GeminiRequest(
            contents = contentsList,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText)))
        )

        var retries = 2
        var currentDelay = 1000L

        while (true) {
            try {
                val response = api.generateContent(apiKey, request)
                val responseText = response.candidates?.getOrNull(0)?.content?.parts?.getOrNull(0)?.text
                if (!responseText.isNullOrBlank()) {
                    return@withContext responseText
                } else {
                    return@withContext "لم يتلقَ المستشار أي رد ذكي من خوادم الذكاء الاصطناعي."
                }
            } catch (e: Exception) {
                // Parse errors details
                val errorMessage = e.message ?: ""
                val isRetryable = errorMessage.contains("429") || errorMessage.contains("503") || errorMessage.contains("timeout") || e is java.io.IOException

                if (isRetryable && retries > 0) {
                    retries--
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2
                    continue
                }

                // Handle status codes according to resilience rules
                return@withContext when {
                    errorMessage.contains("429") -> "عذراً، الضغط عالٍ على السيرفر (تجاوزت الحصة المتاحة للمفتاح)، يرجى إعادة المحاولة بعد دقيقة."
                    errorMessage.contains("503") -> "خادم Gemini غير متاح مؤقتاً بالخدمة الآن، سأحاول مجدداً لاحقاً..."
                    errorMessage.contains("400") || errorMessage.contains("403") -> "فشل في التحقق من صحة مفتاح الـ API أو صلاحياته الحالية. تفقد الإعدادات."
                    else -> "عذراً، حدث خطأ ما أثناء الاتصال بالمستشار الذكي. تأكد من تفعيل الإنترنت أو مراجعة مفتاح الـ API في الإعدادات."
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        "فشل الاتصال."
    }
}
