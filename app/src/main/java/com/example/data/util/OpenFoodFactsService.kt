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

import java.io.File

@JsonClass(generateAdapter = true)
data class OFFProduct(
    val product_name: String? = null,
    val product_name_ar: String? = null,
    val product_name_fr: String? = null,
    val product_name_en: String? = null,
    val image_url: String? = null,
    val image_front_url: String? = null,
    val image_small_url: String? = null,
    val brands: String? = null,
    val quantity: String? = null
)

@JsonClass(generateAdapter = true)
data class OFFResponse(
    val status: Int? = null,
    val status_verbose: String? = null,
    val product: OFFProduct? = null
)

data class ProductOnlineData(
    val name: String?,
    val imageUrl: String?
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

    suspend fun fetchProductOnlineData(barcode: String): ProductOnlineData? = withContext(Dispatchers.IO) {
        try {
            val response = api.getProduct(barcode)
            if (response.status == 1 && response.product != null) {
                val baseName = response.product.product_name_ar?.takeIf { it.isNotBlank() }
                    ?: response.product.product_name?.takeIf { it.isNotBlank() }
                    ?: response.product.product_name_fr?.takeIf { it.isNotBlank() }
                    ?: response.product.product_name_en?.takeIf { it.isNotBlank() }
                
                val brand = response.product.brands?.takeIf { it.isNotBlank() }
                val qty = response.product.quantity?.takeIf { it.isNotBlank() }

                val mergedName = buildString {
                    if (brand != null) {
                        append(brand)
                    }
                    if (baseName != null) {
                        if (isNotEmpty()) {
                            append(" - ")
                        }
                        append(baseName)
                    }
                    if (qty != null) {
                        if (isNotEmpty()) {
                            append(" (")
                            append(qty)
                            append(")")
                        } else {
                            append(qty)
                        }
                    }
                }.takeIf { it.isNotBlank() }

                val img = response.product.image_url?.takeIf { it.isNotBlank() }
                    ?: response.product.image_front_url?.takeIf { it.isNotBlank() }
                    ?: response.product.image_small_url?.takeIf { it.isNotBlank() }
                
                ProductOnlineData(mergedName, img)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadImageToLocal(context: android.content.Context, urlString: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doInput = true
            connection.connect()
            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val file = File(context.filesDir, "prod_img_${System.currentTimeMillis()}.jpg")
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                file.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
