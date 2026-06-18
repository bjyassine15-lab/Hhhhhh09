package com.example.data.util

import android.util.Base64
import com.example.data.dao.PosDao
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object BackupRestoreUtil {

    private const val ALGORITHM = "AES"
    
    // 16-byte secure symmetric key for local POS backup mobility.
    // Represents a fixed key so that the backup can be taken from one device
    // and easily restored on another of the user's devices without data lock.
    private val KEY_BYTES = byteArrayOf(
        75, 97, 115, 104, 105, 101, 114, 83, 109, 97, 114, 116, 80, 79, 83, 49
    ) // "CashierSmartPOS1" in ASCII

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val backupDataAdapter = moshi.adapter(BackupData::class.java)

    /**
     * Encrypts plain text using AES and returns base64 string
     */
    fun encrypt(plainText: String): String {
        val secretKey = SecretKeySpec(KEY_BYTES, ALGORITHM)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    /**
     * Decrypts AES encrypted base64 string and returns plain text
     */
    fun decrypt(encryptedText: String): String {
        val secretKey = SecretKeySpec(KEY_BYTES, ALGORITHM)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val decodedBytes = Base64.decode(encryptedText, Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    /**
     * Fetches all POS databases and compiles them into an encrypted base 64 JSON string
     */
    suspend fun generateBackup(posDao: PosDao): String {
        val products = posDao.getProductsList()
        val customers = posDao.getCustomersList()
        val invoices = posDao.getInvoicesList()
        val items = posDao.getInvoiceItemsList()
        val payments = posDao.getDebtPaymentsList()

        val backupData = BackupData(
            products = products,
            customers = customers,
            invoices = invoices,
            invoiceItems = items,
            debtPayments = payments
        )

        val jsonString = backupDataAdapter.toJson(backupData)
        return encrypt(jsonString)
    }

    /**
     * Decrypts, validates and restores the database from an encrypted backup string.
     * Throws an exception or returns false if data is corrupt.
     */
    suspend fun restoreBackup(posDao: PosDao, encryptedString: String): Boolean {
        return try {
            val decryptedJson = decrypt(encryptedString.trim())
            val backupData = backupDataAdapter.fromJson(decryptedJson) ?: return false
            
            // Execute the Room Transaction to clear and re-insert everything atomically
            posDao.restoreDatabase(
                products = backupData.products,
                customers = backupData.customers,
                invoices = backupData.invoices,
                items = backupData.invoiceItems,
                payments = backupData.debtPayments
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
