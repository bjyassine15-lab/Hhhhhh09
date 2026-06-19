package com.example.data.util

import android.content.Context
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

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

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

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

    fun saveApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("api_key", key).apply()
    }

    fun getSavedApiKey(context: Context): String {
        val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("api_key", "") ?: ""
        if (saved.isNotBlank()) return saved
        val buildKey = BuildConfig.GEMINI_API_KEY
        return if (buildKey == "MY_GEMINI_API_KEY") "" else buildKey
    }

    fun saveSelectedModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_model", model).apply()
    }

    fun getSelectedModel(context: Context): String {
        val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        return prefs.getString("selected_model", "gemini-3.5-flash") ?: "gemini-3.5-flash"
    }

    suspend fun verifyApiKeyDetailed(apiKey: String, model: String = "gemini-3.5-flash"): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "مفتاح API فارغ"
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Hello"))))
        )
        try {
            val response = api.generateContent(model, apiKey, request)
            if (response.candidates != null) {
                null
            } else {
                "استجابة غير صالحة من الخدمة"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.localizedMessage ?: e.message ?: e.toString()
            "فشل الاتصال بخوادم جوجل: $msg"
        }
    }

    /**
     * Parse the standard summary metadata string returned by buildStoreDataSummary
     */
    private fun parseHeaderSummary(contextStr: String): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        try {
            val firstLine = contextStr.substringBefore("\n")
            if (firstLine.startsWith("SYS_SUM:")) {
                val cleaned = firstLine.replace("SYS_SUM:", "").replace(";", ",")
                val parts = cleaned.split(",")
                for (part in parts) {
                    val kv = part.split("=")
                    if (kv.size == 2) {
                        kv[1].toDoubleOrNull()?.let { valD ->
                            map[kv[0].trim()] = valD
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    /**
     * Generate structured, smart rule-based advice locally from database statistics
     * when offline or using bypassed API key configuration.
     */
    fun generateLocalAdvice(prompt: String, dbSummaryContext: String): String {
        val stats = parseHeaderSummary(dbSummaryContext)
        val p = stats["P"]?.toInt() ?: 0
        val l = stats["L"]?.toInt() ?: 0
        val b = stats["B"] ?: 0.0
        val s = stats["S"] ?: 0.0
        val pr = stats["Pr"] ?: 0.0
        val inv = stats["INV"]?.toInt() ?: 0
        val sv = stats["SV"] ?: 0.0
        val cc = stats["CC"] ?: 0.0
        val d = stats["D"]?.toInt() ?: 0
        val dv = stats["DV"] ?: 0.0

        val textLower = prompt.lowercase()

        val isDebtRelated = textLower.contains("دين") || textLower.contains("ديون") || textLower.contains("كريدي") || textLower.contains("مدين")
        val isStockRelated = textLower.contains("مخزن") || textLower.contains("مخزون") || textLower.contains("كمي") || textLower.contains("منتج") || textLower.contains("بضاعة")
        val isProfitRelated = textLower.contains("ربح") || textLower.contains("أرباح") || textLower.contains("مكسب") || textLower.contains("فائدة")
        val isSalesRelated = textLower.contains("بيع") || textLower.contains("مبيع") || textLower.contains("فاتورة") || textLower.contains("فواتير")

        val sb = StringBuilder()
        sb.append("📊 **المستشار المالي الذكي (وضع التحليل المباشر الآمن):**\n\n")

        if (isDebtRelated) {
            sb.append("💡 **تحليل الديون والكريدي المالي الحالي:**\n")
            sb.append("• **عدد زبائن الكريدي النشطين:** $d زبائن.\n")
            sb.append("• **إجمالي المبالغ غير المستردة (الديون المعلقة):** ${"%,.2f".format(dv)} د.إ.\n")
            if (dv > 0) {
                sb.append("📈 **التوجيه المالي الموصى به:**\n")
                sb.append("1. **إرسال تذكيرات دورية:** تواصل مع الزبائن النشطين، وخاصة المدينين بأكثر من المتوسط.\n")
                sb.append("2. **وضع سقف ائتماني:** ننصح بوضع حد أقصى للديون (مثلاً 500 د.إ للزبون الواحد) لمنع تضخم الخسائر المباشرة.\n")
                sb.append("3. **تحفيز السداد النقدي:** قدم خصمًا بسيطاً (مثلاً 2%) عند الدفع الفوري كاش لتقليل نسبة الشراء بالكريدي.\n")
            } else {
                sb.append("🎉 تهانينا! ليس لديك أي ديون معلقة في النظام حالياً. يحافظ متجرك على سيولة ممتازة بنسبة 100%.\n")
            }
        } else if (isStockRelated) {
            sb.append("📦 **تحليل حالة المخزن والمنتجات:**\n")
            sb.append("• **إجمالي أنواع المنتجات بالمتجر:** $p منتجات.\n")
            sb.append("• **منتجات شارف مخزونها على النفاذ (كمية <= 5):** $l منتجات.\n")
            sb.append("• **القيمة التقريبية للمخزن بسعر الشراء:** ${"%,.2f".format(b)} د.إ.\n")
            sb.append("• **القيمة التقريبية للمخزن بسعر البيع:** ${"%,.2f".format(s)} د.إ.\n")
            if (l > 0) {
                sb.append("\n⚠️ **توصيات المخزون المتدني:**\n")
                sb.append("لديك حالياً $l منتجات مخزونها منخفض جداً. يرجى مراجعة صفحة المخزن لطلب طلبيات تكميلية وتجنب توقف المبيعات لهذه السلع الأساسية.\n")
            } else {
                sb.append("👍 مخزونك ممتاز ومتوازن، لا توجد حالياً سلع حرجة ذات كمية منخفضة.\n")
            }
        } else if (isProfitRelated) {
            sb.append("💰 **تحليل الأرباح المتوقعة والاستثمار الكامن:**\n")
            sb.append("• **القيمة الإجمالية المتوقعة للأرباح عند بيع كامل المخزون الحالي في الرفوف:** ${"%,.2f".format(pr)} د.إ.\n")
            sb.append("• **هامش الربح الإجمالي المتوقع للمتجر:** " + (if (b > 0) "${"%.1f".format((pr / b) * 100)}%" else "0%") + "\n")
            sb.append("\n✨ **نصيحة الخبير لزيادة الهامش:**\n")
            sb.append("حاول التركيز على المنتجات ذات هامش الربح الأعلى (الفرق بين الشراء والبيع) وعرضها في الخزانة الأمامية لزيادة معدل دورانها المباشر.\n")
        } else if (isSalesRelated) {
            sb.append("🧾 **تحليل المبيعات والتدفقات النقدية الفورية:**\n")
            sb.append("• **عدد فواتير البيع الحالية:** $inv فواتير.\n")
            sb.append("• **حجم المبيعات الفعلي:** ${"%,.2f".format(sv)} د.إ.\n")
            sb.append("• **النقد المجمع فعلياً (الكاش المستلم):** ${"%,.2f".format(cc)} د.إ.\n")
            val unpaid = sv - cc
            if (unpaid > 0) {
                sb.append("• **جزء من المبيعات لم يسدد (كريدي):** ${"%,.2f".format(unpaid)} د.إ (${"%.1f".format((unpaid / sv) * 100)}% من المبيعات).\n")
            } else {
                sb.append("🎉 جميع مبيعاتك تم تسديدها بنجاح نقداً، مما يعني سيولة مباشرة وسريعة الدوران بمتجرك.\n")
            }
        } else {
            // General overview responses covering user greeting/help
            sb.append("👋 مرحباً بك! أنا مستشارك الحسابي والمالي الذكي المدمج في تطبيق الكاشير.\n\n")
            sb.append("إليك ملخصاً سريعاً لحالة متجرك الاقتصادية والمالية الفورية:\n")
            sb.append("• **مخزنك:** يحتوي على **$p** منتجاً بقيمة شراء تبلغ **${"%,.2f".format(b)} د.إ** وأرباح كامنة تبلغ **${"%,.2f".format(pr)} د.إ**.\n")
            sb.append("• **مبيعاتك:** تم تسجيل **$inv** عملية بيع بقيمة **${"%,.2f".format(sv)} د.إ**، تم تحصيل **${"%,.2f".format(cc)} د.إ** نقداً منها.\n")
            sb.append("• **الديون (الكريدي):** مسجل في ذمتك **$d** زبائن مدينين بمبلغ إجمالي **${"%,.2f".format(dv)} د.إ** يعيق تدفقك الكاش.\n\n")
            sb.append("💬 *يمكنك سؤالي المباشر عن أي جانب (مثلاً: 'كيف أتعامل مع ديوني'، 'كيف حالة مخزني'، أو 'كيف هي أرباحي الكامنة') وسأجيبك فورياً بتحليل دقيق ومفصل.*")
        }
        return sb.toString()
    }

    /**
     * Executes AI prompt by injecting store data context and system instruction,
     * utilizing SharedPreferences saved api key with a fallback to BuildConfig.GEMINI_API_KEY.
     */
    suspend fun getAdvice(
        context: Context,
        prompt: String,
        systemInstructionText: String,
        dbSummaryContext: String,
        history: List<Pair<String, String>> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getSavedApiKey(context)
        if (apiKey.isBlank() || apiKey == "BYPASS") {
            return@withContext generateLocalAdvice(prompt, dbSummaryContext)
        }

        var activeModel = getSelectedModel(context)
        val initialModelChosen = activeModel

        // Smart auto-retry & dynamic model-switching fallback strategy
        var lastException: Exception? = null
        val maxRetryAttempts = 3
        var currentAttempt = 1
        var wasModelSwapped = false

        val contentsList = mutableListOf<GeminiContent>()

        // Add history
        history.forEach { (_, msg) ->
            if (msg.isNotBlank()) {
                contentsList.add(GeminiContent(parts = listOf(GeminiPart(text = msg))))
            }
        }

        // Add context + prompt
        val fullMsg = "$dbSummaryContext\n\nالسؤال الحالي من المستخدم:\n$prompt"
        contentsList.add(GeminiContent(parts = listOf(GeminiPart(text = fullMsg))))

        val request = GeminiRequest(
            contents = contentsList,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText)))
        )

        while (currentAttempt <= maxRetryAttempts) {
            try {
                val response = api.generateContent(activeModel, apiKey, request)
                val responseText = response.candidates?.getOrNull(0)?.content?.parts?.getOrNull(0)?.text
                if (!responseText.isNullOrBlank()) {
                    return@withContext if (wasModelSwapped) {
                        "$responseText\n\n🔄 *(تنبيه: تم تقديم الرد بنجاح عبر النموذج البديل المتاح تلقائياً نتيجة ضغط أو حد حصة النموذج الأساسي)*"
                    } else {
                        responseText
                    }
                } else {
                    lastException = Exception("لم يتلقَ المستشارة أي رد ذكي من الخوادم في المحاولة رقم $currentAttempt.")
                }
            } catch (e: HttpException) {
                e.printStackTrace()
                lastException = e
                val code = e.code()
                // If Rate Limit (HTTP 429) or Server Error (HTTP 5xx), carry out a silent model swap
                if (code == 429 || code >= 500) {
                    val alternativeModel = if (activeModel == "gemini-3.5-flash") {
                        "gemini-3.1-flash-lite-preview"
                    } else {
                        "gemini-3.5-flash"
                    }
                    if (activeModel != alternativeModel) {
                        activeModel = alternativeModel
                        wasModelSwapped = true
                    }
                    // Wait for 2 seconds to avoid slamming the servers and respect Rate Limit / Safe Cooldown rules
                    delay(2000L)
                } else {
                    // For other HTTP errors (e.g., 400 Bad Request / 404 Not Found), try switching to alternative model too
                    val alternativeModel = if (activeModel == "gemini-3.5-flash") {
                        "gemini-3.1-flash-lite-preview"
                    } else {
                        "gemini-3.5-flash"
                    }
                    if (activeModel != alternativeModel) {
                        activeModel = alternativeModel
                        wasModelSwapped = true
                    }
                    delay(1200L * currentAttempt)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lastException = e
                // For direct networking/socket or timeout issues, also attempt dynamic fallback
                val alternativeModel = if (activeModel == "gemini-3.5-flash") {
                    "gemini-3.1-flash-lite-preview"
                } else {
                    "gemini-3.5-flash"
                }
                if (activeModel != alternativeModel) {
                    activeModel = alternativeModel
                    wasModelSwapped = true
                }
                delay(1200L * currentAttempt)
            }
            currentAttempt++
        }

        // Fallback elegantly to offline local database-based direct analytical generator if APIs are totally inaccessible
        val localAdvice = generateLocalAdvice(prompt, dbSummaryContext)
        val errorMessage = lastException?.localizedMessage ?: lastException?.message ?: "مشكلة في الربط والحصة المفرطة"
        return@withContext "$localAdvice\n\n⚠️ *(تنبيه تلقائي: تم تقديم هذا التحليل الذكي محلياً وبثبات تام نتيجة تعذر الاتصال بـ API: $errorMessage)*"
    }
}

