package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import coil.compose.rememberAsyncImagePainter
import com.example.data.entity.Product
import com.example.ui.components.CameraScannerView
import com.example.ui.viewmodel.PosViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: PosViewModel,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val products by viewModel.allProducts.collectAsState()

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val rootBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF000000),
                Color(0xFF070C10),
                Color(0xFF020406)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF4F6F9),
                Color(0xFFECEFF1),
                Color(0xFFF4F6F9)
            )
        )
    }
    val cardBg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF1E1E1E) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color.White else Color(0xFF12151C)
    val textSecondary = if (isDark) Color(0xFF8A8A8A) else Color(0xFF64748B)

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedProductForEdit by remember { mutableStateOf<Product?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchScannerDialog by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, searchQuery) {
        products.filter { product ->
            product.name.contains(searchQuery, ignoreCase = true) ||
            product.barcode.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        containerColor = Color.Transparent, // Transparent because we use custom gradient on Column
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedProductForEdit = null
                    showAddEditDialog = true
                },
                containerColor = Color(0xFFFF9800),
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .size(60.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        clip = false,
                        spotColor = Color(0xFFFF9800),
                        ambientColor = Color(0xFFFF9800)
                    )
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة منتج", modifier = Modifier.size(28.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(brush = rootBrush)
        ) {
            // REDESIGNED HEADER: Products and Stores card with integrated badge
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "المنتجات والمخازن",
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Modern Badge with custom glowing border
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFFF9800).copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${products.size} منتج",
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Dual Search Bar
            if (products.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("بحث باسم المنتج أو الباركود...", color = textSecondary, fontSize = 13.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "بحث",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "مسح",
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = cardBorder,
                            focusedContainerColor = cardBg,
                            unfocusedContainerColor = cardBg,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Scanner Button
                    IconButton(
                        onClick = { showSearchScannerDialog = true },
                        modifier = Modifier
                            .size(52.dp)
                            .background(cardBg, RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "مسح بالباركود للبحث",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(Color(0xFFFF9800).copy(alpha = 0.05f), CircleShape)
                                .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Inventory,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "لا توجد منتجات بالمخزن",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا يوجد أي منتج مسجل في النظام حالياً. إضغط على الزر العائم (+) في الأسفل لإضافة أول منتج.",
                            color = textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .background(Color(0xFFF44336).copy(alpha = 0.05f), CircleShape)
                                .border(1.dp, Color(0xFFF44336).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "لم يتم العثور على نتائج للبحث",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لم نجد أي منتج يطابق القيمة (${searchQuery}). يرجى التحقق من المدخلات أو المحاولة مرة أخرى.",
                            color = textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductItemRow(
                            product = product,
                            onEdit = {
                                selectedProductForEdit = product
                                showAddEditDialog = true
                            },
                            onDelete = {
                                viewModel.deleteProduct(product) {
                                    Toast.makeText(context, "تم حذف المنتج بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Search scanner dialog logic for list searching
    if (showSearchScannerDialog) {
        Dialog(onDismissRequest = { showSearchScannerDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(BorderStroke(1.dp, Color(0xFF262626)), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171717))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "البحث بواسطة الباركود",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, Color(0xFFFF9800), RoundedCornerShape(14.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        CameraScannerView(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            onBarcodeDetected = { code, onComplete ->
                                searchQuery = code
                                viewModel.playBeep()
                                onComplete(true)
                                showSearchScannerDialog = false
                            }
                        )
                    }
                    
                    Button(
                        onClick = { showSearchScannerDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800), contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إلغاء", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- Add/Edit Dialog ---
    if (showAddEditDialog) {
        AddEditProductDialog(
            product = selectedProductForEdit,
            viewModel = viewModel,
            onDismiss = { showAddEditDialog = false },
            onSave = {
                showAddEditDialog = false
                Toast.makeText(context, "تم حفظ بيانات المنتج بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ProductItemRow(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val cardBg = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF1E1E1E) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color.White else Color(0xFF12151C)
    val textSecondary = if (isDark) Color(0xFF8A8A8A) else Color(0xFF64748B)
    val innerContainerBg = if (isDark) Color(0xFF171717) else Color(0xFFF1F5F9)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Local Image preview
            val painter = rememberAsyncImagePainter(
                model = if (!product.imagePath.isNullOrEmpty()) File(product.imagePath) else null
            )

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(innerContainerBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imagePath.isNullOrEmpty()) {
                    Image(
                        painter = painter,
                        contentDescription = "صورة المنتج",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "باركود: ${product.barcode}",
                    fontSize = 11.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("شراء: %.2f د.ت", product.purchasePrice),
                        fontSize = 12.sp,
                        color = Color(0xFFF44336).copy(alpha = 0.8f)
                    )
                    Text(
                        text = String.format("بيع: %.2f د.ت", product.salePrice),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                }
                
                // Optional stock indicator
                if (product.stockQuantity != null) {
                    val lowStock = product.stockQuantity <= 5
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(
                                color = if (lowStock) Color(0xFFF44336).copy(alpha = 0.15f) else Color(0xFF4CAF50).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (lowStock) Color(0xFFF44336).copy(alpha = 0.3f) else Color(0xFF4CAF50).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "المخزن: ${product.stockQuantity} قطع",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (lowStock) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // Edit & Delete icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .background(innerContainerBg, RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "تعديل المنتج",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(innerContainerBg, RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف المنتج",
                        tint = Color(0xFFF44336).copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف المنتج", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف المنتج (${product.name})؟ سيتم حذفه محلياً وتفادي عرضه في البيوعات.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("نعم، احذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductDialog(
    product: Product?,
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current

    // Forms fields
    var name by remember { mutableStateOf(product?.name ?: "") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
    var purchasePriceStr by remember { mutableStateOf(product?.purchasePrice?.toString() ?: "") }
    var salePriceStr by remember { mutableStateOf(product?.salePrice?.toString() ?: "") }
    var stockQuantityStr by remember { mutableStateOf(product?.stockQuantity?.toString() ?: "") }
    var capturedImagePath by remember { mutableStateOf(product?.imagePath) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = File(context.filesDir, "prod_img_${System.currentTimeMillis()}.jpg")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                capturedImagePath = file.absolutePath
            } catch (e: Exception) {
                Toast.makeText(context, "فشل حفظ الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showBarcodeScannerDialog by remember { mutableStateOf(false) }
    var autoFetchEnabled by remember { mutableStateOf(true) }
    var isFetchingProduct by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun fetchProductData(scannedBarcode: String) {
        if (!autoFetchEnabled) return
        val trimmed = scannedBarcode.trim()
        if (trimmed.isEmpty()) return

        isFetchingProduct = true
        coroutineScope.launch {
            try {
                val onlineData = com.example.data.util.OpenFoodFactsService.fetchProductOnlineData(trimmed)
                if (onlineData != null) {
                    var foundSomething = false
                    if (!onlineData.name.isNullOrBlank()) {
                        name = onlineData.name
                        foundSomething = true
                    }
                    if (!onlineData.imageUrl.isNullOrBlank()) {
                        val localPath = com.example.data.util.OpenFoodFactsService.downloadImageToLocal(context, onlineData.imageUrl)
                        if (localPath != null) {
                            capturedImagePath = localPath
                            foundSomething = true
                        }
                    }
                    if (foundSomething) {
                        Toast.makeText(context, "تم جلب بيانات المنتج تلقائياً", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "لم يتم العثور على بيانات للمنتج، يرجى كتابة الاسم يدوياً", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "لم يتم العثور على بيانات للمنتج، يرجى كتابة الاسم يدوياً", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "لا يوجد اتصال بالإنترنت أو حدث خطأ في الاتصال", Toast.LENGTH_SHORT).show()
            } finally {
                isFetchingProduct = false
            }
        }
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
    val dialogBg = MaterialTheme.colorScheme.surface
    val dialogBorder = MaterialTheme.colorScheme.outlineVariant
    val textPrimary = if (isDark) Color.White else Color(0xFF12151C)
    val textSecondary = if (isDark) Color(0xFF8A8A8A) else Color(0xFF64748B)
    val inputBg = if (isDark) Color(0xFF121212) else Color(0xFFF1F5F9)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .border(BorderStroke(1.dp, dialogBorder), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = dialogBg)
        ) {
            // Correctly declare styling inside Composable card content scope
            val inputShape = RoundedCornerShape(20.dp)
            val inputColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF9800),
                unfocusedBorderColor = dialogBorder,
                focusedLabelColor = Color(0xFFFF9800),
                unfocusedLabelColor = textSecondary,
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                cursorColor = Color(0xFFFF9800),
                focusedContainerColor = inputBg,
                unfocusedContainerColor = inputBg
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (product == null) "إضافة منتج جديد" else "تعديل منتج",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Professional circular button (camera/gallery icon) at the top
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(inputBg)
                                .border(1.5.dp, Color(0xFFFF9800), CircleShape)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!capturedImagePath.isNullOrEmpty()) {
                                Image(
                                    painter = rememberAsyncImagePainter(File(capturedImagePath!!)),
                                    contentDescription = "صورة المنتج",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                               ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "إضافة صورة",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "أضف صورة",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Mandatory fields
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        enabled = !isFetchingProduct,
                        label = { Text("اسم المنتج *", fontSize = 13.sp) },
                        singleLine = true,
                        shape = inputShape,
                        colors = inputColors,
                        trailingIcon = if (isFetchingProduct) {
                            {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFF9800)
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Toggle Switch for Auto Fetch on scan
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF1F5F9))
                            .border(BorderStroke(1.dp, dialogBorder), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "جلب بيانات المنتج تلقائياً (إنترنت)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        }
                        Switch(
                            checked = autoFetchEnabled,
                            onCheckedChange = { autoFetchEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFF9800),
                                checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.4f),
                                uncheckedThumbColor = textSecondary,
                                uncheckedTrackColor = if (isDark) Color(0xFF2C2C2C) else Color(0xFFE2E8F0)
                            )
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("رمز الباركود *", fontSize = 13.sp) },
                        singleLine = true,
                        shape = inputShape,
                        colors = inputColors,
                        trailingIcon = {
                            IconButton(
                                onClick = { showBarcodeScannerDialog = true },
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(36.dp)
                                    .background(inputBg, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "مسح باركود",
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it },
                            label = { Text("سعر الشراء *", fontSize = 11.sp) },
                            singleLine = true,
                            shape = inputShape,
                            colors = inputColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = salePriceStr,
                            onValueChange = { salePriceStr = it },
                            label = { Text("سعر البيع *", fontSize = 11.sp) },
                            singleLine = true,
                            shape = inputShape,
                            colors = inputColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Optional Field: Stock Quantity
                item {
                    OutlinedTextField(
                        value = stockQuantityStr,
                        onValueChange = { stockQuantityStr = it },
                        label = { Text("الكمية بالمخزن (اختياري)", fontSize = 13.sp) },
                        singleLine = true,
                        shape = inputShape,
                        colors = inputColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Action controls at bottom
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "إلغاء",
                                color = textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        Button(
                            onClick = {
                                val purchaseVal = purchasePriceStr.toDoubleOrNull()
                                val saleVal = salePriceStr.toDoubleOrNull()
                                val stockVal = stockQuantityStr.toIntOrNull()

                                if (name.trim().isEmpty()) {
                                    Toast.makeText(context, "يرجى تعبئة اسم المنتج", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (barcode.trim().isEmpty()) {
                                    Toast.makeText(context, "يرجى مسح أو إدخال باركود", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (purchaseVal == null || purchaseVal < 0) {
                                    Toast.makeText(context, "يرجى إدخال سعر شراء صحيح", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (saleVal == null || saleVal < 0) {
                                    Toast.makeText(context, "يرجى إدخال سعر بيع صحيح", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                viewModel.saveProduct(
                                    id = product?.id ?: 0,
                                    name = name.trim(),
                                    barcode = barcode.trim(),
                                    purchasePrice = purchaseVal,
                                    salePrice = saleVal,
                                    stockQuantity = stockVal,
                                    imagePath = capturedImagePath,
                                    onSuccess = onSave
                                )
                            },
                            enabled = name.isNotEmpty() && barcode.isNotEmpty(),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(46.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800),
                                contentColor = Color.Black,
                                disabledContainerColor = Color(0xFFFF9800).copy(alpha = 0.3f),
                                disabledContentColor = Color.Black.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                "حفظ البيانات",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBarcodeScannerDialog) {
        Dialog(onDismissRequest = { showBarcodeScannerDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "مسح باركود المنتج",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        CameraScannerView(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            onBarcodeDetected = { code, onComplete ->
                                barcode = code
                                viewModel.playBeep()
                                onComplete(true)
                                showBarcodeScannerDialog = false
                                if (autoFetchEnabled) {
                                    fetchProductData(code)
                                }
                            }
                        )
                    }
                    
                    Button(
                        onClick = { showBarcodeScannerDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("إلغاء", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
