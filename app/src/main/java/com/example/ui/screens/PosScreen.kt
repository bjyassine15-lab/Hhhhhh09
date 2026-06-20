package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
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
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension

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
        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val (cameraRef, cartRef) = createRefs()

            if (isCameraVisible) {
                // Section 1 Left: Camera Scanner (Continuous Scanning & 2s cooldown)
                // Restrained strictly below top bar inside layout bounds, clipped with corner radius
                Box(
                    modifier = Modifier
                        .constrainAs(cameraRef) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(cartRef.start, margin = 8.dp)
                            width = Dimension.percent(0.42f)
                            height = Dimension.fillToConstraints
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CameraScannerView(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(4f / 3f, matchHeightConstraintsFirst = true),
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
                    .constrainAs(cartRef) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        if (isCameraVisible) {
                            start.linkTo(cameraRef.end)
                            width = Dimension.fillToConstraints
                        } else {
                            start.linkTo(parent.start)
                            width = Dimension.fillToConstraints
                        }
                        end.linkTo(parent.end)
                        height = Dimension.fillToConstraints
                    }
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
        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val (cameraRef, cartRef) = createRefs()

            if (isCameraVisible) {
                // Section 1 Top: Camera Scanner (Continuous Scanning & 1.5s cooldown)
                // Restrained strictly below top bar inside layout bounds, clipped with corner radius
                Box(
                    modifier = Modifier
                        .constrainAs(cameraRef) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .aspectRatio(4f / 3f) // Maintains a crisp, constant 4:3 aspect ratio
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
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
                    .constrainAs(cartRef) {
                        if (isCameraVisible) {
                            top.linkTo(cameraRef.bottom, margin = 8.dp)
                        } else {
                            top.linkTo(parent.top)
                        }
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    }
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
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var tabIndex by remember { mutableIntStateOf(0) }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerPhone by remember { mutableStateOf("") }

    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) {
            customers
        } else {
            customers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                (it.phone?.contains(searchQuery) == true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.85f else 0.95f)
                .padding(vertical = if (isLandscape) 4.dp else 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isLandscape) 12.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "تسجيل الفاتورة كدين (كريدي)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Selector Tabs
                TabRow(
                    selectedTabIndex = tabIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("عميل مسجل", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("عميل جديد", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (tabIndex == 0) {
                    // --- EXISTING CUSTOMER FLOW (Search + Scrollable LazyColumn) ---
                    if (customers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا يوجد عملاء مسجلين حالياً. يرجى إنشاء عميل جديد.",
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Professional Live Search Bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("بحث عن اسم العميل أو الهاتف...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "بحث",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )

                            // Modern elegant scrollable list (RecyclerView style)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = if (isLandscape) 130.dp else 240.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredCustomers) { customer ->
                                    val isSelected = selectedCustomer?.id == customer.id
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedCustomer = customer }
                                            .padding(horizontal = 2.dp, vertical = 2.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Avatar circle
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(16.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = customer.name.firstOrNull()?.toString() ?: "?",
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = customer.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (!customer.phone.isNullOrEmpty()) {
                                                    Text(
                                                        text = "📞 " + customer.phone,
                                                        fontSize = 10.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (filteredCustomers.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "لا توجد نتائج مطابقة لبحثك",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("إلغاء", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                selectedCustomer?.let {
                                    onConfirmExistingCustomer(it.id)
                                }
                            },
                            enabled = selectedCustomer != null,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تسجيل الدين ككريدي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                } else {
                    // --- NEW CUSTOMER FLOW ---
                    OutlinedTextField(
                        value = newCustomerName,
                        onValueChange = { newCustomerName = it },
                        label = { Text("اسم العميل الجديد", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newCustomerPhone,
                        onValueChange = { newCustomerPhone = it },
                        label = { Text("الهاتف (اختياري)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("إلغاء", fontWeight = FontWeight.Bold)
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
                            enabled = newCustomerName.trim().isNotEmpty(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إنشاء وتسجيل الدين", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(12.dp),
                    clip = false
                )
                .background(
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "سلة المشتريات",
                    color = Color(0xFFE0E0E0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(28.dp)
                        .background(
                            color = Color(0xFF000000),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cartItemsCount.toString(),
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

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
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "مسح الباركود",
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
