package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.data.entity.Customer
import com.example.data.entity.Product
import com.example.ui.components.CameraScannerView
import com.example.ui.viewmodel.PosViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State collections
    val cartItems by viewModel.cartItems.collectAsState()
    val totalAmount by viewModel.cartTotal.collectAsState()
    val allCustomers by viewModel.allCustomers.collectAsState()

    // Dialog states
    var showDebtDialog by remember { mutableStateOf(false) }
    var showScanErrorDialog by remember { mutableStateOf(false) }
    var unknownBarcode by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var isDebtSettledSuccess by remember { mutableStateOf(false) }

    var isCameraVisible by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Section 1 Left: Camera Scanner (Continuous Scanning & 2s cooldown)
            if (isCameraVisible) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Black)
                ) {
                    CameraScannerView(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        onBarcodeDetected = { barcode, onComplete ->
                            viewModel.scanProductBarcode(
                                barcode = barcode,
                                onMatched = { product ->
                                    Toast.makeText(context, "🛒 ${product.name} تمت إضافته بنجاح", Toast.LENGTH_SHORT).show()
                                    onComplete(true)
                                },
                                onNotFound = { badBarcode ->
                                    unknownBarcode = badBarcode
                                    showScanErrorDialog = true
                                    onComplete(false)
                                }
                            )
                        }
                    )

                    // Close/X button to hide camera
                    IconButton(
                        onClick = { isCameraVisible = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق الكاميرا", tint = Color.White)
                    }

                    // Force reset scan memory
                    IconButton(
                        onClick = {
                            viewModel.forceResetScanMemory()
                            Toast.makeText(context, "تم إعادة تهيئة الذاكرة المؤقتة للمسح", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset scanning", tint = Color.White)
                    }
                }
            }

            // Section 2 Right: Cart List / checkout
            Column(
                modifier = Modifier
                    .weight(if (isCameraVisible) 1.2f else 2f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                CartHeader(
                    cartItemsCount = cartItems.sumOf { it.quantity },
                    isCameraVisible = isCameraVisible,
                    onOpenCamera = { isCameraVisible = true }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (cartItems.isEmpty()) {
                        EmptyCartView()
                    } else {
                        CartList(
                            cartItems = cartItems,
                            viewModel = viewModel
                        )
                    }
                }

                if (cartItems.isNotEmpty()) {
                    CheckoutBottomBar(
                        totalAmount = totalAmount,
                        cartItems = cartItems,
                        onSettleCash = {
                            coroutineScope.launch {
                                val success = viewModel.completeCashSale()
                                if (success) {
                                    isDebtSettledSuccess = false
                                    showSuccessDialog = true
                                } else {
                                    Toast.makeText(context, "حدث خطأ أثناء إتمام عملية البيع", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSettleDebt = { showDebtDialog = true }
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // SECTION 1: Camera Preview on Portrait (at top, weight 0.40f)
            if (isCameraVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.40f)
                        .background(Color.Black)
                ) {
                    CameraScannerView(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        onBarcodeDetected = { barcode, onComplete ->
                            viewModel.scanProductBarcode(
                                barcode = barcode,
                                onMatched = { product ->
                                    Toast.makeText(context, "🛒 ${product.name} تمت إضافته بنجاح", Toast.LENGTH_SHORT).show()
                                    onComplete(true)
                                },
                                onNotFound = { badBarcode ->
                                    unknownBarcode = badBarcode
                                    showScanErrorDialog = true
                                    onComplete(false)
                                }
                            )
                        }
                    )

                    // Close/X button to hide camera
                    IconButton(
                        onClick = { isCameraVisible = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق الكاميرا", tint = Color.White)
                    }

                    // Force reset scan memory
                    IconButton(
                        onClick = {
                            viewModel.forceResetScanMemory()
                            Toast.makeText(context, "تم إعادة تهيئة الذاكرة المؤقتة للمسح", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset scanning", tint = Color.White)
                    }
                }
            }

            // SECTION 2: Cart List / checkout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isCameraVisible) 0.60f else 1f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                CartHeader(
                    cartItemsCount = cartItems.sumOf { it.quantity },
                    isCameraVisible = isCameraVisible,
                    onOpenCamera = { isCameraVisible = true }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (cartItems.isEmpty()) {
                        EmptyCartView()
                    } else {
                        CartList(
                            cartItems = cartItems,
                            viewModel = viewModel
                        )
                    }
                }

                if (cartItems.isNotEmpty()) {
                    CheckoutBottomBar(
                        totalAmount = totalAmount,
                        cartItems = cartItems,
                        onSettleCash = {
                            coroutineScope.launch {
                                val success = viewModel.completeCashSale()
                                if (success) {
                                    isDebtSettledSuccess = false
                                    showSuccessDialog = true
                                } else {
                                    Toast.makeText(context, "حدث خطأ أثناء إتمام عملية البيع", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSettleDebt = { showDebtDialog = true }
                    )
                }
            }
        }
    }

    // --- DIALOGS SECTION ---

    // 1. Double verification & customer picker for credits
    if (showDebtDialog) {
        DebtSettlementDialog(
            customers = allCustomers,
            onDismiss = { showDebtDialog = false },
            onConfirmNewCustomer = { name, phone ->
                viewModel.addCustomer(name, phone) { customer ->
                    coroutineScope.launch {
                        val ok = viewModel.completeCreditSale(customer.id)
                        showDebtDialog = false
                        if (ok) {
                            isDebtSettledSuccess = true
                            showSuccessDialog = true
                        } else {
                            Toast.makeText(context, "فشل تسجيل الدين للعميل", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onConfirmExistingCustomer = { customerId ->
                coroutineScope.launch {
                    val ok = viewModel.completeCreditSale(customerId)
                    showDebtDialog = false
                    if (ok) {
                        isDebtSettledSuccess = true
                        showSuccessDialog = true
                    } else {
                        Toast.makeText(context, "فشل تسجيل الدين للعميل", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // 2. Scan Error Alert on invalid barcodes
    if (showScanErrorDialog) {
        val helperText = "المنتج ذو الباركود ($unknownBarcode) غير مسجل في النظام"
        AlertDialog(
            onDismissRequest = { showScanErrorDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("المنتج غير موجود", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = { Text(helperText, textAlign = TextAlign.Center) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showScanErrorDialog = false
                        viewModel.forceResetScanMemory()
                    }
                ) {
                    Text("حسناً")
                }
            }
        )
    }

    // 3. Purchase Success feedback
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(48.dp)) },
            title = {
                Text(
                    text = if (isDebtSettledSuccess) "تم تسجيل الكريدي بنجاح" else "تمت عملية البيع بنجاح",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text("تم حفظ الفاتورة وتناقص كميات المخزون بنجاح.", textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = { showSuccessDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
fun CartItemRow(
    item: com.example.data.model.CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Image Preview (Local Path)
            val painter = rememberAsyncImagePainter(
                model = if (!item.product.imagePath.isNullOrEmpty()) File(item.product.imagePath) else null
            )

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!item.product.imagePath.isNullOrEmpty()) {
                    Image(
                        painter = painter,
                        contentDescription = "تصوير المنتج",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Text Info Columns
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format("سعر القطعة: %.2f د.ت", item.product.salePrice),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("المجموع: %.2f د.ت", item.totalAmount),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Quantity adjust controllers (Compact)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "أنقص الكمية",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = item.quantity.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 20.dp)
                )

                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "أضف قطة",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف الكل",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtSettlementDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onConfirmNewCustomer: (String, String?) -> Unit,
    onConfirmExistingCustomer: (Long) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerPhone by remember { mutableStateOf("") }

    var expandedDropdown by remember { mutableStateOf(false) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "تسجيل الفاتورة كدين (كريدي)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Selector Tabs
                TabRow(
                    selectedTabIndex = tabIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("عميل مسجل", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("عميل جديد", fontSize = 13.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (tabIndex == 0) {
                    // --- EXISTING CUSTOMER FLOW ---
                    if (customers.isEmpty()) {
                        Text(
                            text = "لا يوجد عملاء مسجلين حالياً. يرجى إنشاء عميل جديد.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCustomer?.name ?: "إختر العميل...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("اختر عميل") },
                                trailingIcon = {
                                    IconButton(onClick = { expandedDropdown = !expandedDropdown }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedDropdown = !expandedDropdown }
                            )

                            DropdownMenu(
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                customers.forEach { customer ->
                                    DropdownMenuItem(
                                        text = { Text(customer.name) },
                                        onClick = {
                                            selectedCustomer = customer
                                            expandedDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("إلغاء")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                selectedCustomer?.let {
                                    onConfirmExistingCustomer(it.id)
                                }
                            },
                            enabled = selectedCustomer != null
                        ) {
                            Text("تسجيل الدين")
                        }
                    }

                } else {
                    // --- NEW CUSTOMER FLOW ---
                    OutlinedTextField(
                        value = newCustomerName,
                        onValueChange = { newCustomerName = it },
                        label = { Text("اسم العميل الجديد") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newCustomerPhone,
                        onValueChange = { newCustomerPhone = it },
                        label = { Text("الهاتف (اختياري)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("إلغاء")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newCustomerName.trim().isNotEmpty()) {
                                    onConfirmNewCustomer(
                                        newCustomerName.trim(),
                                        newCustomerPhone.trim().ifEmpty { null }
                                    )
                                }
                            },
                            enabled = newCustomerName.trim().isNotEmpty()
                        ) {
                            Text("إنشاء وتسجيل")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartHeader(
    cartItemsCount: Int,
    isCameraVisible: Boolean,
    onOpenCamera: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "سلة المشتريات ($cartItemsCount عناصر)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (!isCameraVisible) {
            Button(
                onClick = onOpenCamera,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "مسح باركود 📷",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyCartView() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "السلة فارغة. قم بمسح الرمز التعريفي للمنتج بالكاميرا لإضافته مباشرة",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun CartList(
    cartItems: List<com.example.data.model.CartItem>,
    viewModel: PosViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(cartItems, key = { it.product.id }) { item ->
            CartItemRow(
                item = item,
                onIncrement = { viewModel.incrementCartItem(item.product) },
                onDecrement = { viewModel.decrementCartItem(item.product) },
                onDelete = { viewModel.removeFromCart(item.product) }
            )
        }
    }
}

@Composable
fun CheckoutBottomBar(
    totalAmount: Double,
    cartItems: List<com.example.data.model.CartItem>,
    onSettleCash: () -> Unit,
    onSettleDebt: () -> Unit
) {
    val context = LocalContext.current
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الإجمالي المستحق:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = String.format("%.2f د.ت", totalAmount),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSettleCash,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "إنهاء البيع (نقداً)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onSettleDebt,
                        modifier = Modifier.weight(0.8f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "الكريدي (ديون)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
