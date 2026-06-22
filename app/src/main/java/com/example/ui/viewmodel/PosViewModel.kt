package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.dao.PosDao
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.data.model.CartItem
import com.example.data.relation.CustomerWithDebt
import com.example.data.relation.InvoiceWithItems
import com.example.data.util.BackupRestoreUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user" or "advisor", or "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class AppCommand {
    data class SortProducts(val criteria: String) : AppCommand()
    data class FilterProducts(val query: String) : AppCommand()
    data class ViewMetrics(val section: String) : AppCommand()
    
    data class DeleteProduct(val barcode: String, val productName: String) : AppCommand()
    data class ModifyProductPrice(val barcode: String, val productName: String, val newSalePrice: Double) : AppCommand()
    data class AddManualDebt(val customerName: String, val amount: Double, val note: String) : AppCommand()
    data class RecordDebtPayment(val customerName: String, val amountPaid: Double, val note: String?) : AppCommand()
    data class AddProducts(val productsList: List<Product>) : AppCommand()
    data class SetStockAlertThreshold(val threshold: Int) : AppCommand()
}

data class PendingAction(
    val command: AppCommand,
    val description: String
)

class PosViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val posDao = db.posDao()

    // --- STATE MANAGEMENT ---
    // Sorting & Filtering state flows
    private val _productSortOrder = MutableStateFlow<String>("none")
    val productSortOrder = _productSortOrder.asStateFlow()

    private val _productSearchQuery = MutableStateFlow<String>("")
    val productSearchQuery = _productSearchQuery.asStateFlow()

    // Products Flow
    val allProducts: StateFlow<List<Product>> = combine(
        posDao.getAllProducts(),
        _productSortOrder,
        _productSearchQuery
    ) { products, sortOrder, query ->
        var list = products
        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.barcode.contains(query, ignoreCase = true)
            }
        }
        when (sortOrder) {
            "price_asc" -> list.sortedBy { it.salePrice }
            "price_desc" -> list.sortedByDescending { it.salePrice }
            "stock_asc" -> list.sortedBy { it.stockQuantity ?: 0 }
            "stock_desc" -> list.sortedByDescending { it.stockQuantity ?: 0 }
            "name_asc" -> list.sortedBy { it.name }
            else -> list
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Stock alert threshold state (default to 5)
    private val _stockAlertThreshold = MutableStateFlow<Int>(5)
    val stockAlertThreshold = _stockAlertThreshold.asStateFlow()

    fun setStockAlertThreshold(newThreshold: Int) {
        _stockAlertThreshold.value = newThreshold
    }

    // Low stock flow
    @OptIn(ExperimentalCoroutinesApi::class)
    val lowStockProducts: StateFlow<List<Product>> = _stockAlertThreshold
        .flatMapLatest { threshold ->
            posDao.getLowStockProducts(threshold)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Customers Flow
    val allCustomers: StateFlow<List<Customer>> = posDao.getAllCustomers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Customers with Debt Flow
    val customersWithDebt: StateFlow<List<CustomerWithDebt>> = posDao.getAllCustomersWithDebt().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Invoices Flow
    val allInvoices: StateFlow<List<InvoiceWithItems>> = posDao.getAllInvoicesWithItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- CURRENT CART ENGINE ---
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    // Total price state deriving from _cartItems
    val cartTotal: StateFlow<Double> = _cartItems.combine(MutableStateFlow(0.0)) { items, _ ->
        items.sumOf { it.totalAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Scan memory to avoid loop scanning same product
    private var lastScannedBarcode: String? = null
    private var lastScannedTime: Long = 0

    // Sound Tone
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            Log.e("PosViewModel", "Failed to init ToneGenerator", e)
        }
    }

    /**
     * Beep sound for barcode scans
     */
    fun playBeep() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            Log.e("PosViewModel", "Beep play failed", e)
        }
    }

    /**
     * Handle scan barcode event on POS screen
     */
    fun scanProductBarcode(barcode: String, onMatched: (Product) -> Unit = {}, onNotFound: (String) -> Unit = {}) {
        viewModelScope.launch {
            val product = withContext(Dispatchers.IO) {
                posDao.getProductByBarcode(barcode)
            }
            if (product != null) {
                // Play confirmation beep tone ONLY on successful SQLite lookup
                playBeep()
                
                // Atomically update state flow / cart
                addToCart(product)
                
                // Notify UI state successful matching
                onMatched(product)
            } else {
                // Notify UI not found barcode
                onNotFound(barcode)
            }
        }
    }

    fun forceResetScanMemory() {
        lastScannedBarcode = null
        lastScannedTime = 0
    }

    // --- CART ACTIONS ---
    fun addToCart(product: Product) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(quantity = currentList[index].quantity + 1)
        } else {
            currentList.add(CartItem(product = product, quantity = 1))
        }
        _cartItems.value = currentList
    }

    fun incrementCartItem(product: Product) {
        addToCart(product)
    }

    fun decrementCartItem(product: Product) {
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id }
        if (index != -1) {
            val item = currentList[index]
            if (item.quantity > 1) {
                currentList[index] = item.copy(quantity = item.quantity - 1)
            } else {
                currentList.removeAt(index)
            }
            _cartItems.value = currentList
        }
    }

    fun removeFromCart(product: Product) {
        val currentList = _cartItems.value.toMutableList()
        currentList.removeAll { it.product.id == product.id }
        _cartItems.value = currentList
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        forceResetScanMemory()
    }

    // --- SALES SETTLEMENT ---
    suspend fun completeCashSale(): Boolean {
        if (_cartItems.value.isEmpty()) return false
        val total = cartTotal.value

        return withContext(Dispatchers.IO) {
            try {
                val invoiceNumber = "INV-" + System.currentTimeMillis().toString().takeLast(6)
                val invoice = Invoice(
                    invoiceNumber = invoiceNumber,
                    totalAmount = total,
                    isDebt = false,
                    customerId = null,
                    paidAmount = total
                )
                val invoiceId = posDao.insertInvoice(invoice)

                val items = _cartItems.value.map { cart ->
                    InvoiceItem(
                        invoiceId = invoiceId,
                        productId = cart.product.id,
                        productName = cart.product.name,
                        productBarcode = cart.product.barcode,
                        quantity = cart.quantity,
                        salePrice = cart.product.salePrice,
                        purchasePrice = cart.product.purchasePrice
                    )
                }
                posDao.insertInvoiceItems(items)

                // Update stock inventory
                _cartItems.value.forEach { cart ->
                    if (cart.product.stockQuantity != null) {
                        val remaining = maxOf(0, cart.product.stockQuantity - cart.quantity)
                        posDao.updateProduct(cart.product.copy(stockQuantity = remaining))
                    }
                }

                _cartItems.value = emptyList()
                forceResetScanMemory()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun completeCreditSale(customerId: Long): Boolean {
        if (_cartItems.value.isEmpty()) return false
        val total = cartTotal.value

        return withContext(Dispatchers.IO) {
            try {
                val invoiceNumber = "INV-CR-" + System.currentTimeMillis().toString().takeLast(6)
                val invoice = Invoice(
                    invoiceNumber = invoiceNumber,
                    totalAmount = total,
                    isDebt = true,
                    customerId = customerId,
                    paidAmount = 0.0
                )
                val invoiceId = posDao.insertInvoice(invoice)

                val items = _cartItems.value.map { cart ->
                    InvoiceItem(
                        invoiceId = invoiceId,
                        productId = cart.product.id,
                        productName = cart.product.name,
                        productBarcode = cart.product.barcode,
                        quantity = cart.quantity,
                        salePrice = cart.product.salePrice,
                        purchasePrice = cart.product.purchasePrice
                    )
                }
                posDao.insertInvoiceItems(items)

                // Update stock inventory
                _cartItems.value.forEach { cart ->
                    if (cart.product.stockQuantity != null) {
                        val remaining = maxOf(0, cart.product.stockQuantity - cart.quantity)
                        posDao.updateProduct(cart.product.copy(stockQuantity = remaining))
                    }
                }

                _cartItems.value = emptyList()
                forceResetScanMemory()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // --- PRODUCTS MANAGEMENT ---
    fun saveProduct(
        id: Long = 0,
        name: String,
        barcode: String,
        purchasePrice: Double,
        salePrice: Double,
        stockQuantity: Int?,
        imagePath: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val product = Product(
                id = if (id == 0L) 0 else id,
                name = name,
                barcode = barcode,
                purchasePrice = purchasePrice,
                salePrice = salePrice,
                stockQuantity = stockQuantity,
                imagePath = imagePath
            )
            posDao.insertProduct(product)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun deleteProduct(product: Product, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            posDao.deleteProduct(product)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }


    // --- CUSTOMER & DEBTS MANAGEMENT ---
    fun addCustomer(name: String, phone: String?, onSuccess: (Customer) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val customer = Customer(name = name, phone = phone)
            val id = posDao.insertCustomer(customer)
            val created = customer.copy(id = id)
            withContext(Dispatchers.Main) {
                onSuccess(created)
            }
        }
    }

    fun addManualDebt(customerId: Long, amount: Double, note: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val invoiceNumber = "INV-MAN-" + System.currentTimeMillis().toString().takeLast(6)
                val invoice = Invoice(
                    invoiceNumber = invoiceNumber,
                    totalAmount = amount,
                    isDebt = true,
                    customerId = customerId,
                    paidAmount = 0.0
                )
                val invoiceId = posDao.insertInvoice(invoice)

                val item = InvoiceItem(
                    invoiceId = invoiceId,
                    productId = null,
                    productName = "دين يدوي: $note",
                    productBarcode = "",
                    quantity = 1,
                    salePrice = amount,
                    purchasePrice = 0.0
                )
                posDao.insertInvoiceItems(listOf(item))
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun recordDebtPayment(customerId: Long, amountPaid: Double, note: String?, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val payment = DebtPayment(
                customerId = customerId,
                amountPaid = amountPaid,
                note = note
            )
            posDao.insertDebtPayment(payment)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun deleteInvoice(invoice: Invoice, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            posDao.deleteInvoice(invoice)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun deleteDebtPayment(payment: DebtPayment, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            posDao.deleteDebtPayment(payment)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun getDebtPaymentsForCustomer(customerId: Long) = posDao.getDebtPaymentsForCustomer(customerId)


    // --- DATA RECOVERY (BACKUP & RESTORE) ---
    suspend fun exportEncryptedBackup(): String {
        return withContext(Dispatchers.IO) {
            BackupRestoreUtil.generateBackup(posDao)
        }
    }

    suspend fun importEncryptedBackup(encryptedBase64: String): Boolean {
        return withContext(Dispatchers.IO) {
            BackupRestoreUtil.restoreBackup(posDao, encryptedBase64)
        }
    }

    // --- PHOTO SAVE CONVENIENCE ---
    fun getProductImageDirectory(): File {
        val context = getApplication<Application>()
        val dir = File(context.filesDir, "product_photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // --- AI ADVISOR CHAT STATE & IMPLEMENTATION ---
    private val _aiChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val aiChatMessages: StateFlow<List<ChatMessage>> = _aiChatMessages.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    fun setPendingAction(action: PendingAction?) {
        _pendingAction.value = action
    }

    fun confirmPendingAction() {
        val pending = _pendingAction.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val cmd = pending.command
            _pendingAction.value = null
            executeCommandDirectly(cmd)
        }
    }

    fun cancelPendingAction() {
        _pendingAction.value = null
    }

    fun addSystemChatMessage(text: String) {
        val currentList = _aiChatMessages.value.toMutableList()
        currentList.add(ChatMessage(sender = "system", text = text))
        _aiChatMessages.value = currentList
    }

    fun executeCommand(command: AppCommand) {
        val requireConfirmation = when (command) {
            is AppCommand.SortProducts -> false
            is AppCommand.FilterProducts -> false
            is AppCommand.ViewMetrics -> false
            else -> true
        }

        if (requireConfirmation) {
            val description = when (command) {
                is AppCommand.DeleteProduct -> "هل توافق على حذف المنتج '${command.productName}' ذو الباركود (${command.barcode}) نهائياً من قاعدة البيانات؟"
                is AppCommand.ModifyProductPrice -> "هل توافق على تعديل سعر بيع المنتج '${command.productName}' إلى ${command.newSalePrice} د.إ؟"
                is AppCommand.AddManualDebt -> "هل توافق على تسجيل دين جديد بقيمة ${command.amount} د.إ للزبون '${command.customerName}' بملاحظة: ${command.note}؟"
                is AppCommand.RecordDebtPayment -> "هل توافق على تسجيل دفعة سداد دين بقيمة ${command.amountPaid} د.إ من الزبون '${command.customerName}'؟"
                is AppCommand.AddProducts -> "هل توافق على إضافة عدد (${command.productsList.size}) من المنتجات المقترحة من Gemini نهائياً لبيانات المخزن؟"
                is AppCommand.SetStockAlertThreshold -> "يقترح الذكاء الاصطناعي تغيير حد تنبيه نقص المخزون لتنبيهك عندما يقل المخزون عن (${command.threshold}) قطع، هل توافق؟"
                else -> "هل توافق على تنفيذ هذا الإجراء المقترح من Gemini؟"
            }
            _pendingAction.value = PendingAction(command, description)
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                executeCommandDirectly(command)
            }
        }
    }

    private suspend fun executeCommandDirectly(command: AppCommand) {
        try {
            when (command) {
                is AppCommand.SortProducts -> {
                    _productSortOrder.value = command.criteria
                    addSystemChatMessage("⚡ تم إجراء ترتيب تلقائي لقائمة المنتجات: ${
                        when (command.criteria) {
                            "price_asc" -> "السعر (من الأقل للأعلى)"
                            "price_desc" -> "السعر (من الأعلى للأقل)"
                            "stock_asc" -> "المخزون (من الأقل للأعلى)"
                            "stock_desc" -> "المخزون (من الأعلى للأقل)"
                            "name_asc" -> "الاسم أبجدياً"
                            else -> "الترتيب الافتراضي"
                        }
                    }")
                }
                is AppCommand.FilterProducts -> {
                    _productSearchQuery.value = command.query
                    addSystemChatMessage("⚡ تم فلترة قائمة المنتجات بكلمة البحث: '${command.query}'")
                }
                is AppCommand.ViewMetrics -> {
                    addSystemChatMessage("⚡ تم عرض مؤشرات الأداء الحسابية الخاصة بـ: ${command.section}")
                }
                is AppCommand.DeleteProduct -> {
                    val p = posDao.getProductByBarcode(command.barcode)
                    if (p != null) {
                        posDao.deleteProduct(p)
                        addSystemChatMessage("🗑️ تم حذف المنتج '${command.productName}' بنجاح تمهيداً لأمر Gemini.")
                    } else {
                        addSystemChatMessage("⚠️ لم نجد أي منتج يحمل الباركود ${command.barcode} لتنفيذ الحذف.")
                    }
                }
                is AppCommand.ModifyProductPrice -> {
                    val p = posDao.getProductByBarcode(command.barcode)
                    if (p != null) {
                        posDao.updateProduct(p.copy(salePrice = command.newSalePrice))
                        addSystemChatMessage("✏️ تم تعديل سعر بيع المنتج '${command.productName}' إلى ${command.newSalePrice} د.إ بنجاح.")
                    } else {
                        addSystemChatMessage("⚠️ لم نجد للمنتج (${command.productName}) باركود مطبّق لتعديل السعر.")
                    }
                }
                is AppCommand.AddManualDebt -> {
                    var customer = posDao.getCustomerByName(command.customerName)
                    if (customer == null) {
                        val newId = posDao.insertCustomer(Customer(name = command.customerName, phone = null))
                        customer = Customer(id = newId, name = command.customerName, phone = null)
                    }
                    val invoiceNumber = "INV-AI-" + System.currentTimeMillis().toString().takeLast(6)
                    val invoiceId = posDao.insertInvoice(Invoice(
                        invoiceNumber = invoiceNumber,
                        totalAmount = command.amount,
                        isDebt = true,
                        customerId = customer.id,
                        paidAmount = 0.0
                    ))
                    posDao.insertInvoiceItems(listOf(InvoiceItem(
                        invoiceId = invoiceId,
                        productId = null,
                        productName = "دين يدوي مقترح من Gemini: ${command.note}",
                        productBarcode = "",
                        quantity = 1,
                        salePrice = command.amount,
                        purchasePrice = 0.0
                    )))
                    addSystemChatMessage("💸 تم تسجيل دين جديد بنجاح بقيمة ${command.amount} د.إ للزبون '${command.customerName}'.")
                }
                is AppCommand.RecordDebtPayment -> {
                    val customer = posDao.getCustomerByName(command.customerName)
                    if (customer != null) {
                        val payment = DebtPayment(
                            customerId = customer.id,
                            amountPaid = command.amountPaid,
                            note = command.note ?: "سداد حساب عبر Gemini"
                        )
                        posDao.insertDebtPayment(payment)
                        addSystemChatMessage("✅ تم بنجاح تسجيل دفعة سداد بقيمة ${command.amountPaid} د.إ من الزبون '${command.customerName}'.")
                    } else {
                        addSystemChatMessage("⚠️ تعذر استكمال السداد، لم نجد في السجلات زبون يحمل الاسم المباشر '${command.customerName}'.")
                    }
                }
                is AppCommand.AddProducts -> {
                    command.productsList.forEach { p ->
                        posDao.insertProduct(p)
                    }
                    addSystemChatMessage("📦 تم بنجاح إضافة عدد (${command.productsList.size}) من المنتجات الجديدة المقترحة إلى المخزن بنجاح.")
                }
                is AppCommand.SetStockAlertThreshold -> {
                    _stockAlertThreshold.value = command.threshold
                    addSystemChatMessage("⚙️ تم بنجاح تعديل حد منبه كمية المخزون المنخفض وتعيينه عند: ${command.threshold} قطع.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            addSystemChatMessage("⚠️ فشل تنفيذ الأمر: ${e.localizedMessage}")
        }
    }

    private fun handleAiResponseAndExtractCommands(rawResponse: String) {
        var cleanText = rawResponse
        var command: AppCommand? = null

        try {
            val commandMarker = "COMMAND:"
            val index = rawResponse.indexOf(commandMarker)
            if (index != -1) {
                val jsonStr = rawResponse.substring(index + commandMarker.length).trim()
                cleanText = rawResponse.substring(0, index).trim()

                val json = org.json.JSONObject(jsonStr)
                val action = json.optString("action")
                command = when (action) {
                    "sort_products" -> {
                        val criteria = json.optString("criteria", "none")
                        AppCommand.SortProducts(criteria)
                    }
                    "filter_products" -> {
                        val query = json.optString("query", "")
                        AppCommand.FilterProducts(query)
                    }
                    "view_metrics" -> {
                        val section = json.optString("section", "")
                        AppCommand.ViewMetrics(section)
                    }
                    "delete_product" -> {
                        val barcode = json.optString("barcode", "")
                        val productName = json.optString("productName", "")
                        AppCommand.DeleteProduct(barcode, productName)
                    }
                    "modify_price" -> {
                        val barcode = json.optString("barcode", "")
                        val productName = json.optString("productName", "")
                        val newSalePrice = json.optDouble("newSalePrice", 0.0)
                        AppCommand.ModifyProductPrice(barcode, productName, newSalePrice)
                    }
                    "add_debt" -> {
                        val customerName = json.optString("customerName", "")
                        val amount = json.optDouble("amount", 0.0)
                        val note = json.optString("note", "")
                        AppCommand.AddManualDebt(customerName, amount, note)
                    }
                    "record_payment" -> {
                        val customerName = json.optString("customerName", "")
                        val amountPaid = json.optDouble("amountPaid", 0.0)
                        val note = json.optString("note", null)
                        AppCommand.RecordDebtPayment(customerName, amountPaid, note)
                    }
                    "add_products" -> {
                        val arr = json.optJSONArray("productsList")
                        val list = mutableListOf<Product>()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                list.add(Product(
                                    name = obj.optString("name", "منتج جديد"),
                                    barcode = obj.optString("barcode", System.currentTimeMillis().toString().takeLast(6)),
                                    purchasePrice = obj.optDouble("purchasePrice", 0.0),
                                    salePrice = obj.optDouble("salePrice", 0.0),
                                    stockQuantity = if (obj.has("stockQuantity")) obj.optInt("stockQuantity") else null
                                ))
                            }
                        }
                        AppCommand.AddProducts(list)
                    }
                    "set_alert_threshold" -> {
                        val threshold = json.optInt("threshold", 5)
                        AppCommand.SetStockAlertThreshold(threshold)
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PosViewModel", "Failed to parse command JSON: ${e.message}")
        }

        val currentList = _aiChatMessages.value.toMutableList()
        currentList.add(ChatMessage(sender = "advisor", text = cleanText))
        _aiChatMessages.value = currentList

        if (command != null) {
            executeCommand(command)
        }
    }

    fun clearAiChat() {
        _aiChatMessages.value = emptyList()
    }

    fun buildStoreDataSummary(): String {
        val productsList = allProducts.value
        val debtsList = customersWithDebt.value
        val invoicesList = allInvoices.value

        val totalProductsCount = productsList.size
        val lowStockCount = productsList.count { (it.stockQuantity ?: 0) <= _stockAlertThreshold.value }
        val totalStockBuyValue = productsList.sumOf { (it.stockQuantity ?: 0) * it.purchasePrice }
        val totalStockSellValue = productsList.sumOf { (it.stockQuantity ?: 0) * it.salePrice }
        val estimatedTotalProfit = totalStockSellValue - totalStockBuyValue

        val totalInvoicesCount = invoicesList.size
        val totalSalesVolume = invoicesList.sumOf { it.invoice.totalAmount }
        val totalCashCollected = invoicesList.sumOf { it.invoice.paidAmount }

        val customersWithDebtCount = debtsList.size
        val totalUnpaidDebtValue = debtsList.sumOf { it.totalDebt }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)

        val sb = java.lang.StringBuilder()
        // Header summary for system state
        sb.append("SYS_SUM:P=$totalProductsCount,L=$lowStockCount,B=${"%.1f".format(totalStockBuyValue)},S=${"%.1f".format(totalStockSellValue)},Pr=${"%.1f".format(estimatedTotalProfit)};")
        sb.append("INV=$totalInvoicesCount,SV=${"%.1f".format(totalSalesVolume)},CC=${"%.1f".format(totalCashCollected)};")
        sb.append("D=$customersWithDebtCount,DV=${"%.1f".format(totalUnpaidDebtValue)}\n")

        // 1. Detailed Products List
        sb.append("[ALL_PRODUCTS_DETAILS]\n")
        if (productsList.isEmpty()) {
            sb.append("No products in database.\n")
        } else {
            productsList.forEach { p ->
                sb.append("${p.name}|${p.barcode}|${p.purchasePrice}|${p.salePrice}|${p.stockQuantity ?: 0}\n")
            }
        }

        // 2. Detailed Invoices & Transactions List
        sb.append("[ALL_INVOICES_DETAILS]\n")
        if (invoicesList.isEmpty()) {
            sb.append("No sales transactions yet.\n")
        } else {
            invoicesList.forEach { inv ->
                val dateStr = sdf.format(java.util.Date(inv.invoice.timestamp))
                val itemsStr = inv.items.joinToString(",") { "${it.productName}(x${it.quantity})" }
                sb.append("ID:${inv.invoice.id},No:${inv.invoice.invoiceNumber},T:$dateStr,Tot:${inv.invoice.totalAmount},Debt:${inv.invoice.isDebt},Paid:${inv.invoice.paidAmount},Cust:${inv.invoice.customerId ?: ""},Items:[$itemsStr]\n")
            }
        }

        // 3. Detailed Debtors List
        sb.append("[DEBTORS_DETAILS]\n")
        if (debtsList.isEmpty()) {
            sb.append("No active debtors in database.\n")
        } else {
            debtsList.forEach { d ->
                sb.append("Name:${d.name},Phone:${d.phone ?: ""},Debt:${d.totalDebt}\n")
            }
        }

        return sb.toString().trim()
    }

    fun sendPromptToAi(promptText: String, context: Context, onComplete: () -> Unit = {}) {
        val textCleaned = promptText.trim()
        if (textCleaned.isEmpty()) return

        val currentList = _aiChatMessages.value.toMutableList()
        currentList.add(ChatMessage(sender = "user", text = textCleaned))
        _aiChatMessages.value = currentList

        _isAiLoading.value = true

        // Strict Coroutines background execution (Dispatchers.IO) preventing any main thread lag/ANR
        viewModelScope.launch(Dispatchers.IO) {
            val systemInstructionText = """
                أنت مستشار مالي ومسؤول مخزن منفّذ ومساعد ذكي لتطبيق كاشير (Action-Taker). أنت الآن تملك صلاحية اقتراح وتنفيذ إجراءات على قاعدة البيانات ومخزن التطبيق بناءً على كلام المستخدم والتحليل الحسابي الملحق.
                مرفق لك قائمة بأسماء كل المنتجات بباركوداتها وأسعارها، وقائمة بكل فاتورة تم بيعها وتاريخها، وقائمة بأسماء أصحاب الديون.
                
                تنبيه هام حول الأوامر:
                عندما يطلب العميل أي تعديل أو ترتيب أو فلترة أو حذف في المخزن أو حسابات الديون، يجب عليك تضمين الإجراء المناسب في سطر مستقل في نهاية رسالتك يبدأ بـ COMMAND: يليه كائن JSON مباشر يصف الإجراء دون أي علامات ترميز إضافية أو كتل برمجية (no Markdown code blocks).
                
                صيغ الأوامر المدعومة المسموحة بالـ JSON هي:
                1. ترتيب المنتجات: COMMAND:{"action":"sort_products","criteria":"price_asc"} (الخيارات المتاحة لـ criteria: price_asc, price_desc, stock_asc, stock_desc, name_asc)
                2. فلترة المنتجات بنص: COMMAND:{"action":"filter_products","query":"النص للبحث"}
                3. حذف منتج: COMMAND:{"action":"delete_product","barcode":"الباركود","productName":"اسم المنتج"}
                4. تعديل سعر منتج: COMMAND:{"action":"modify_price","barcode":"الباركود","productName":"اسم المنتج","newSalePrice":15.0}
                5. إضافة دين يدوي لزبون: COMMAND:{"action":"add_debt","customerName":"اسم الزبون","amount":250.0,"note":"ملاحظة الدين الكسبي"}
                6. تسجيل سداد دين من زبون: COMMAND:{"action":"record_payment","customerName":"اسم الزبون","amountPaid":100.0,"note":"البيان"}
                7. إضافة منتجات متعددة مرة واحدة: COMMAND:{"action":"add_products","productsList":[{"name":"اسم المنتج","barcode":"الباركود","purchasePrice":10.0,"salePrice":15.0,"stockQuantity":50}]}
                8. تغيير حد تنبيه نقص المخزون: COMMAND:{"action":"set_alert_threshold","threshold":10}
                
                مثال:
                أبشر يا فندم، سأقوم بترتيب مخزن المنتجات حسب الأسعار لك تصاعدياً.
                COMMAND:{"action":"sort_products","criteria":"price_asc"}
                
                ملاحظات بالغة الأهمية:
                - لا تلفق وتخلق بيانات كاذبة! استخدم أسماء الزبائن وثمن البيع الفعلي وأسماء المنتجات والباركودات من قائمة البيانات الحقيقية المبينة بالملخص الملحق بدقة فائقة.
                - تذكر: المخزون (0) يعني أن المنتج مسجل ومعروف وموجود ولكنه نفد، فلا تقل أبداً أن المنتج غير موجود، بل تعامل معه بحرفية.
                - أكتب نص الرد الحواري بلباقة باللغة العربية واشرح الإجراء، ثم ألحق COMMAND: في السطر الأخير.
            """.trimIndent()

            val dbSummaryContext = buildStoreDataSummary()
            val historyList = _aiChatMessages.value.map { it.sender to it.text }

            try {
                val response = com.example.data.util.GeminiService.getAdvice(
                    context = context,
                    prompt = textCleaned,
                    systemInstructionText = systemInstructionText,
                    dbSummaryContext = dbSummaryContext,
                    history = historyList.dropLast(1) // exclude the newly added user message
                )

                withContext(Dispatchers.Main) {
                    handleAiResponseAndExtractCommands(response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val detailedErr = e.localizedMessage ?: e.message ?: e.toString()
                withContext(Dispatchers.Main) {
                    val updatedList = _aiChatMessages.value.toMutableList()
                    updatedList.add(ChatMessage(sender = "advisor", text = "⚠️ حدث خطأ في النظام: $detailedErr"))
                    _aiChatMessages.value = updatedList
                }
            } finally {
                _isAiLoading.value = false
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Safe silence
        }
    }
}

class PosViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
