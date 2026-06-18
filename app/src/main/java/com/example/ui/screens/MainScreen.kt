package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> PosScreen(viewModel = viewModel, paddingValues = innerPadding)
            1 -> InventoryScreen(viewModel = viewModel, paddingValues = innerPadding)
            2 -> ReportsScreen(viewModel = viewModel, paddingValues = innerPadding)
        }
    }

    // --- ENCRYPTED DATA BACKUP & RECOVERY DIALOG ---
    if (showBackupDialog) {
        BackupRestoreDialog(
            viewModel = viewModel,
            onDismiss = { showBackupDialog = false }
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
                                        val clip = ClipData.newPlainText("POS Backup", encryptedBackupString)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "تم نسخ الرمز بنجاح! شاركه واحفظه بسلام", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
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
