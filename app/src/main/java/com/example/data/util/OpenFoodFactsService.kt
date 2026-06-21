package com.example.data.util

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OFFProduct(
    val product_name: String? = null,
    val product_name_ar: String? = null,
    val product_name_fr: String? = null,
    val product_name_en: String? = null
)

@JsonClass(generateAdapter = true)
data class OFFResponse(
    val status: Int? = null,
    val status_verbose: String? = null,
    val product: OFFProduct? = null
)

interface OpenFoodFactsApi {
    @GET("api/v0/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String
    ): OFFResponse
}

object OpenFoodFactsService {
    private const val BASE_URL = "https://world.openfoodfacts.org/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val api: OpenFoodFactsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenFoodFactsApi::class.java)
    }

    suspend fun fetchProductName(barcode: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = api.getProduct(barcode)
            if (response.status == 1 && response.product != null) {
                val name = response.product.product_name_ar?.takeIf { it.isNotBlank() }
                    ?: response.product.product_name?.takeIf { it.isNotBlank() }
                    ?: response.product.product_name_fr?.takeIf { it.isNotBlank() }
                    ?: response.product.product_name_en?.takeIf { it.isNotBlank() }
                name
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
