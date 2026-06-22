package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.viewmodel.PosViewModel
import com.example.ui.components.VoiceAssistantManager
import com.example.ui.components.VoiceState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: PosViewModel,
    onThemeToggle: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Permissions for Record Audio
    val recordAudioPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    // Voice conversation states
    val voiceState by VoiceAssistantManager.state.collectAsState()
    val voiceStatusText by VoiceAssistantManager.statusText.collectAsState()
    val voiceRecognizedText by VoiceAssistantManager.recognizedText.collectAsState()
    val isVoiceSessionActive = remember(voiceState) {
        voiceState != VoiceState.INACTIVE && voiceState != VoiceState.ERROR
    }

    // Track when Gemini replies
    val chatMessages by viewModel.aiChatMessages.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    // Interactive Pulse Animation configurations for hands-free mode
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by if (isVoiceSessionActive) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    val pulseAlpha by if (isVoiceSessionActive) {
        infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    } else {
        remember { mutableStateOf(0.15f) }
    }

    // Screen navigation tracking
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog state for backup protection
    var showBackupDialog by remember { mutableStateOf(false) }
    val pendingAction by viewModel.pendingAction.collectAsState()

    // Stop speaking/listening when leaving voice mode or when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            VoiceAssistantManager.destroy()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    Box(
                        modifier = Modifier.padding(start = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient Breathing Glow when Live Voice session is active
                        if (isVoiceSessionActive) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .scale(pulseScale)
                                    .alpha(pulseAlpha)
                                    .background(Color(0xFFE040FB), shape = CircleShape)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    color = if (selectedTab == 3 || isVoiceSessionActive) Color(0xFFE040FB).copy(alpha = if (isVoiceSessionActive) 0.35f else 0.15f) else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        selectedTab = if (selectedTab == 3) 0 else 3
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (recordAudioPermissionState.status.isGranted) {
                                            if (isVoiceSessionActive) {
                                                VoiceAssistantManager.stopSession()
                                            } else {
                                                val sysIns = viewModel.getLiveSystemInstruction()
                                                val apiKey = com.example.data.util.GeminiService.getSavedApiKey(context)
                                                VoiceAssistantManager.startSession(
                                                    context = context,
                                                    apiKey = apiKey,
                                                    systemInstruction = sysIns,
                                                    onCommandExtracted = { responseText ->
                                                        viewModel.handleAiResponseAndExtractCommands(responseText)
                                                    }
                                                )
                                            }
                                        } else {
                                            recordAudioPermissionState.launchPermissionRequest()
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "المستشار الذكي",
                                tint = if (selectedTab == 3 || isVoiceSessionActive) Color(0xFFE040FB) else Color(0xFF8A8A8A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Prime Ledger",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 1.sp,
                            color = Color(0xFF00E5FF),
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 12f
                                )
                            ),
                            textAlign = TextAlign.Center
                        )
                        if (isVoiceSessionActive) {
                            Text(
                                text = voiceStatusText,
                                color = Color(0xFFE040FB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(
                                        color = Color(0xFFE040FB).copy(alpha = 0.4f),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 6f
                                    )
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                },
                actions = {
                    val isDarkThemeNow = MaterialTheme.colorScheme.background == Color(0xFF000000)
                    IconButton(
                        onClick = onThemeToggle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkThemeNow) Icons.Default.WbSunny else Icons.Default.DarkMode,
                            contentDescription = "تبديل المظهر",
                            tint = if (isDarkThemeNow) Color.White else Color(0xFF12151C),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Card(
                        onClick = { showBackupDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B5E20).copy(alpha = 0.15f),
                            contentColor = Color(0xFF4CAF50)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "حماية البيانات",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "حماية البيانات",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = if (MaterialTheme.colorScheme.background == Color(0xFF000000)) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
                )
            )
        },
        bottomBar = {
            val isDark = MaterialTheme.colorScheme.background == Color(0xFF000000)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = if (isDark) Color(0xFF000000) else Color(0xFFF4F6F9)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(if (isDark) Color(0xFF0D0D0D) else Color(0xFFFFFFFF), shape = RoundedCornerShape(20.dp))
                        .border(if (isDark) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, Color(0xFFE2E8F0)), shape = RoundedCornerShape(20.dp))
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Triple(0, Icons.Default.PointOfSale, "شاشة البيع"),
                        Triple(1, Icons.Default.Inventory2, "المخازن"),
                        Triple(2, Icons.Default.BarChart, "التقارير")
                    )
                    
                    tabs.forEach { (tabIndex, icon, label) ->
                        val isSelected = selectedTab == tabIndex
                        val activeTabColor = when (tabIndex) {
                            0 -> Color(0xFF4CAF50) // Emerald Green for sales
                            1 -> Color(0xFFFF9800) // Amber for inventory
                            2 -> Color(0xFFE040FB) // Fuchsia/Purple for reports
                            else -> Color(0xFF8A8A8A)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { selectedTab = tabIndex }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = isSelected,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(220, delayMillis = 40)) + 
                                     scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 40)))
                                        .togetherWith(fadeOut(animationSpec = tween(140)) + 
                                                      scaleOut(targetScale = 0.92f, animationSpec = tween(140)))
                                },
                                label = "tab_transition"
                            ) { selected ->
                                if (selected) {
                                    Row(
                                        modifier = Modifier
                                            .background(Color(0xFF171717), shape = RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (tabIndex == 2) {
                                            ColoredBarChartIcon(
                                                modifier = Modifier.size(16.dp),
                                                isSelected = true
                                            )
                                        } else {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = activeTabColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = label,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        if (tabIndex == 2) {
                                            ColoredBarChartIcon(
                                                modifier = Modifier.size(20.dp),
                                                isSelected = false
                                            )
                                        } else {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = Color(0xFF8A8A8A),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = label,
                                            color = Color(0xFF8A8A8A),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
                onOpenSettings = {}
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

    // --- DIALOG: CONFIRMATION BARRIER (PENDING ACTION EXECUTED FROM VOICE / ADVISOR) ---
    pendingAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "موافقة مطلوبة لتعديل البيانات",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = pending.description,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingAction() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("تأكيد العملية")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelPendingAction() }
                ) {
                    Text("إلغاء الأمر", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // Voice overlay is removed for a clean background hands-free conversation experience.
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
                    text = "حماية البيانات الفائقة وعمل نسخة احتياطية",
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
            title = { Text("تحذير شديد الخطورة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
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
                                Toast.makeText(context, "تم استعادة كامل البيانات والمبيعات بنجاح التام!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "فشلت الاستعادة. الكود تالف أو تم تعديله وغير متطابق.", Toast.LENGTH_LONG).show()
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

@Composable
fun ColoredBarChartIcon(modifier: Modifier = Modifier, isSelected: Boolean) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        val barCount = 3
        val spacing = width * 0.15f
        val barWidth = (width - (spacing * (barCount - 1))) / barCount
        
        val hRatios = listOf(0.4f, 0.9f, 0.65f)
        
        val barColors = if (isSelected) {
            listOf(
                Color(0xFF4CAF50), // Emerald Green
                Color(0xFFFF9800), // Amber
                Color(0xFFE040FB)  // Fuchsia
            )
        } else {
            listOf(
                Color(0xFF8A8A8A).copy(alpha = 0.8f),
                Color(0xFF8A8A8A).copy(alpha = 0.8f),
                Color(0xFF8A8A8A).copy(alpha = 0.8f)
            )
        }
        
        for (i in 0 until barCount) {
            val barHeight = height * hRatios[i]
            val x = i * (barWidth + spacing)
            val y = height - barHeight
            
            drawRoundRect(
                color = barColors[i],
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth * 0.25f, barWidth * 0.25f)
            )
        }
    }
}


