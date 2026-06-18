package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.viewmodel.PosViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen navigation tracking
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog state for backup protection
    var showBackupDialog by remember { mutableStateOf(false) }

    // App state variables for AI Gatekeeper validation
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGatekeeperErrorDialog by remember { mutableStateOf(false) }
    var gatekeeperDetailedError by remember { mutableStateOf<String?>(null) }
    var isCheckingGatekeeper by remember { mutableStateOf(false) }
    var isAiVerified by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "الكاشير الذكي ونقاط البيع",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                actions = {
                    // "إعدادات الذكاء الاصطناعي" Button
                    IconButton(
                        onClick = { showSettingsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "إعدادات مستشار الذكاء الاصطناعي",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // "حماية البيانات" (Data Backup) Button
                    TextButton(
                        onClick = { showBackupDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = "حماية البيانات",
                                modifier = Modifier.size(18.dp)
                              )
                            Text(
                                "حماية البيانات",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                tonalElevation = 8.dp
            ) {
                // Tab 1: Selling POS
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.PointOfSale, contentDescription = "شاشة البيع") },
                    label = { Text("شاشة البيع", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )

                // Tab 2: Inventory
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = "المنتجات والمخزن") },
                    label = { Text("المنتجات والمخازن", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )

                // Tab 3: Reports & Accounts
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "التقارير") },
                    label = { Text("التقارير المالية", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )

                // Tab 4: AI Advisor (The Gatekeeper)
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        val key = com.example.data.util.GeminiService.getSavedApiKey(context)
                        if (isAiVerified) {
                            selectedTab = 3
                        } else {
                            if (key.isBlank()) {
                                gatekeeperDetailedError = "لم يتم العثور على أي مفتاح واجهة برمجة تطبيقات (API Key) محفوظ. يرجى إدخال مفتاح Gemini الخاص بك في صفحة الإعدادات أولاً."
                                showGatekeeperErrorDialog = true
                            } else {
                                isCheckingGatekeeper = true
                                coroutineScope.launch {
                                    val err = com.example.data.util.GeminiService.verifyApiKeyDetailed(key)
                                    isCheckingGatekeeper = false
                                    if (err == null) {
                                        isAiVerified = true
                                        selectedTab = 3
                                    } else {
                                        gatekeeperDetailedError = err
                                        showGatekeeperErrorDialog = true
                                    }
                                }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "المستشار الذكي") },
                    label = { Text("المستشار الذكي", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> PosScreen(viewModel = viewModel, paddingValues = innerPadding)
            1 -> InventoryScreen(viewModel = viewModel, paddingValues = innerPadding)
            2 -> ReportsScreen(viewModel = viewModel, paddingValues = innerPadding)
            3 -> AiAdvisorScreen(
                viewModel = viewModel,
                paddingValues = innerPadding,
                onOpenSettings = { showSettingsDialog = true }
            )
        }
    }

    // --- ENCRYPTED DATA BACKUP & RECOVERY DIALOG ---
    if (showBackupDialog) {
        BackupRestoreDialog(
            viewModel = viewModel,
            onDismiss = { showBackupDialog = false }
        )
    }

    // --- SETTINGS DIALOG (AI PARAMETERS SETUP) ---
    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            onKeyVerified = {
                isAiVerified = true
                selectedTab = 3
            }
        )
    }

    // --- SILENT CONNECT OVERLAY ---
    if (isCheckingGatekeeper) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "جاري استئناف البوابة وفحص المستشار...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // --- GATEKEEPER FAILURE DIALOG ---
    if (showGatekeeperErrorDialog) {
        AlertDialog(
            onDismissRequest = { showGatekeeperErrorDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "المستشار الذكي غير متصل",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "يرجى التأكد من تشغيل الإنترنت وصلاحية مفتاح الـ API لخدمة Gemini في صفحة الإعدادات لتثبيت وبدء مناقشة الحسابات.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    if (gatekeeperDetailedError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "التفاصيل: ${gatekeeperDetailedError!!}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    onClick = {
                        showGatekeeperErrorDialog = false
                        showSettingsDialog = true
                    }
                ) {
                    Text("ضبط مفتاح الـ API")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGatekeeperErrorDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    var encryptedBackupString by remember { mutableStateOf("") }
    var inputRestoreString by remember { mutableStateOf("") }
    var isGeneratingState by remember { mutableStateOf(false) }
    var isRestoringState by remember { mutableStateOf(false) }

    var showConfirmRestoreWarning by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "⚙️ حماية البيانات الفائقة وعمل نسخة احتياطية",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "يقوم النظام بتشفير فائق لقاعدة البيانات (المنتجات، الفواتير، الكريدي) إلى نص مشفر يمكنك مشاركته لحفظه واسترجاعه في أي وقت بجهاز آخر.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // --- GENERATION ACTION ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "1. توليد النسخة الاحتياطية الحالية:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                     if (encryptedBackupString.isNotEmpty()) {
                        OutlinedTextField(
                            value = encryptedBackupString,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("النص المشفر للنسخة الاحتياطية", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 9.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        // Ensure standard trim clipboard manager set primary clip
                                        val clip = ClipData.newPlainText("POS Backup", encryptedBackupString.trim())
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "تم كبس ونسخ الرمز الاحتياطي بنجاح!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Share Intent button requested by the user
                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "نسخة الكاشير الذكي الاحتياطية")
                                    putExtra(Intent.EXTRA_TEXT, encryptedBackupString)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة كود النسخة الاحتياطية الكامل"))
                                Toast.makeText(context, "جاري فتح نافذة المشاركة الآمنة...", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "مشاركة",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مشاركة كود النسخة الكامل 📤", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            isGeneratingState = true
                            coroutineScope.launch {
                                try {
                                    encryptedBackupString = viewModel.exportEncryptedBackup()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "عملية توليد الكود فشلت", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isGeneratingState = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !isGeneratingState
                    ) {
                        Text(if (isGeneratingState) "جاري توليد الكود المشفر..." else "توليد كود الحماية والنسخ")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // --- RESTORATION ACTION ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "2. استعادة البيانات من الكود:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = inputRestoreString,
                        onValueChange = { inputRestoreString = it },
                        label = { Text("الصق الكود الاحتياطي المشفر هنا...") },
                        textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            if (inputRestoreString.isBlank()) {
                                Toast.makeText(context, "الرجاء إلصاق النص أولاً", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            showConfirmRestoreWarning = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !isRestoringState && inputRestoreString.isNotBlank()
                    ) {
                        Text(if (isRestoringState) "جاري الاستئناف..." else "استعادة قاعدة البيانات")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق وإلغاء")
                }
            }
        }
    }

    // Double warning check before actually wiping the DB and restoring
    if (showConfirmRestoreWarning) {
        AlertDialog(
            onDismissRequest = { showConfirmRestoreWarning = false },
            title = { Text("⚠️ تحذير شديد الخطورة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("هل أنت متأكد؟ هذه الخطوة ستقوم بمسح كامل لجميع المنتجات والديون والبيوعات الحالية في الهاتف وتعويضها بالكامل بما في الكود!") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showConfirmRestoreWarning = false
                        isRestoringState = true
                        coroutineScope.launch {
                            val success = viewModel.importEncryptedBackup(inputRestoreString)
                            isRestoringState = false
                            if (success) {
                                Toast.makeText(context, "🎉 عظيم! تم استعادة كامل البيانات والمبيعات بنجاح التام!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "⚠️ فشلت الاستعادة. الكود تالف أو تم تعديله وغير متطابق.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("نعم، استعد ومسح الحالي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRestoreWarning = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onKeyVerified: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var apiKeyInput by remember { mutableStateOf(com.example.data.util.GeminiService.getSavedApiKey(context)) }
    var keyVisibility by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var verifyResultText by remember { mutableStateOf<String?>(null) }

    // Color extractions to avoid calling Composable getters in non-composable onClick blocks
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    var verifyResultColor by remember { mutableStateOf(Color.Gray) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "⚙️ إعدادات مستشار الذكاء الاصطناعي",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "لتفعيل المستشار المالي الفوري، نرجو تزويد مفتاح API للاتصال الآمن بخدمة Gemini. يمكنك استخراج مفتاحك الخاص مجاناً من Google AI Studio.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Right
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { 
                        apiKeyInput = it
                        verifyResultText = null 
                    },
                    label = { Text("أدخل مفتاح Gemini API Key الخاص بك") },
                    singleLine = true,
                    visualTransformation = if (keyVisibility) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisibility = !keyVisibility }) {
                            Icon(
                                imageVector = if (keyVisibility) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (verifyResultText != null) {
                    Text(
                        text = verifyResultText!!,
                        fontSize = 11.sp,
                        color = verifyResultColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                 Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (apiKeyInput.isBlank()) {
                                Toast.makeText(context, "الرجاء إدخال المفتاح أولاً", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isVerifying = true
                            verifyResultText = "جاري الاتصال والتحقق الصامت من المفتاح..."
                            verifyResultColor = primaryColor
                            coroutineScope.launch {
                                val errMessage = com.example.data.util.GeminiService.verifyApiKeyDetailed(apiKeyInput)
                                isVerifying = false
                                if (errMessage == null) {
                                    com.example.data.util.GeminiService.saveApiKey(context, apiKeyInput)
                                    verifyResultText = "🎉 ممتاز! تم التحقق بنجاح وتفعيل القناة الذكية."
                                    verifyResultColor = Color(0xFF2E7D32)
                                    onKeyVerified()
                                } else {
                                    verifyResultText = "❌ فشل الاتصال: $errMessage"
                                    verifyResultColor = errorColor
                                    Toast.makeText(context, "$errMessage", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isVerifying,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("التحقق والتنشيط ⚡", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Force Save / Skip verification button requested by user
                    Button(
                        onClick = {
                            if (apiKeyInput.isBlank()) {
                                Toast.makeText(context, "الرجاء إدخال المفتاح أولاً", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            com.example.data.util.GeminiService.saveApiKey(context, apiKeyInput)
                            Toast.makeText(context, "تم تخطي الفحص والاتصال وحفظ المفتاح بنجاح!", Toast.LENGTH_LONG).show()
                            verifyResultText = "💾 تم الحفظ وتخطي الفحص بنجاح."
                            verifyResultColor = Color(0xFF2E7D32)
                            onKeyVerified()
                        },
                        enabled = !isVerifying,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        modifier = Modifier.weight(0.9f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("تخطي وحفظ 💾", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            com.example.data.util.GeminiService.saveApiKey(context, "")
                            apiKeyInput = ""
                            verifyResultText = "تم مسح مفتاحك بنجاح من الذاكرة."
                            verifyResultColor = errorColor
                        },
                        modifier = Modifier.weight(0.45f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                    ) {
                        Text("حذف", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إغلاق الإعدادات")
                    }
                }
            }
        }
    }
}

