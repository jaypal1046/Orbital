package com.orbital.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.R
import com.orbital.automation.OrbitalAccessibilityService
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage
import com.orbital.overlay.OverlayService

enum class OnboardingStep {
    WELCOME,
    MODELS_AND_KEYS,
    PERMISSIONS_AND_ACCESSIBILITY,
    CHARACTER_SELECTION
}

class SetupWizardActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_PROVIDERS = "open_providers"
    }

    private lateinit var secureStorage: SecureStorage
    private lateinit var llmRepository: LlmRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStorage = SecureStorage(this)
        llmRepository = LlmRepository(secureStorage)
        val opensProviders = intent.getBooleanExtra(EXTRA_OPEN_PROVIDERS, false)

        setContent {
            OrbitalTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding(),
                    color = OrbitalTokens.Background
                ) {
                    OnboardingWizard(
                        secureStorage = secureStorage,
                        llmRepository = llmRepository,
                        initialStep = if (opensProviders) OnboardingStep.MODELS_AND_KEYS else OnboardingStep.WELCOME,
                        isOnboarding = !opensProviders,
                        onClose = { finish() },
                        onComplete = { chosenChar ->
                            secureStorage.markSetupComplete()
                            secureStorage.saveSelectedCharacter(chosenChar)
                            val mainIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra("character_id", chosenChar)
                            }
                            startActivity(mainIntent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingWizard(
    secureStorage: SecureStorage,
    llmRepository: LlmRepository,
    initialStep: OnboardingStep = OnboardingStep.WELCOME,
    isOnboarding: Boolean = true,
    onClose: () -> Unit = {},
    onComplete: (String) -> Unit
) {
    var currentStep by remember(initialStep) { mutableStateOf(initialStep) }
    var selectedCharacter by remember { mutableStateOf(secureStorage.getSelectedCharacter() ?: "aether") }
    val context = LocalContext.current

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
            } else {
                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> width } + fadeOut()
            }
        },
        label = "OnboardingTransition"
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> {
                WelcomeStepScreen(
                    onNext = { currentStep = OnboardingStep.MODELS_AND_KEYS }
                )
            }
            OnboardingStep.MODELS_AND_KEYS -> {
                KeyManagementScreen(
                    secureStorage = secureStorage,
                    llmRepository = llmRepository,
                    isOnboarding = isOnboarding,
                    onBack = { if (isOnboarding) currentStep = OnboardingStep.WELCOME else onClose() },
                    onContinue = { if (isOnboarding) currentStep = OnboardingStep.PERMISSIONS_AND_ACCESSIBILITY else onClose() }
                )
            }
            OnboardingStep.PERMISSIONS_AND_ACCESSIBILITY -> {
                PermissionsAndAccessibilityStepScreen(
                    onBack = { currentStep = OnboardingStep.MODELS_AND_KEYS },
                    onNext = { currentStep = OnboardingStep.CHARACTER_SELECTION }
                )
            }
            OnboardingStep.CHARACTER_SELECTION -> {
                CharacterSelectionStepScreen(
                    selectedCharacter = selectedCharacter,
                    onSelect = { charId ->
                        selectedCharacter = charId
                        secureStorage.saveSelectedCharacter(charId)
                    },
                    onBack = { currentStep = OnboardingStep.PERMISSIONS_AND_ACCESSIBILITY },
                    onFinish = {
                        secureStorage.saveSelectedCharacter(selectedCharacter)
                        onComplete(selectedCharacter)
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingStepProgressHeader(
    currentStepIndex: Int,
    totalSteps: Int = 4,
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Step progress pill bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (step in 1..totalSteps) {
                val isCompleted = step < currentStepIndex
                val isCurrent = step == currentStepIndex
                val color = when {
                    isCompleted -> Color(0xFF10B981)
                    isCurrent -> Color(0xFF8B5CF6)
                    else -> Color(0xFF1E2540)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STEP $currentStepIndex OF $totalSteps",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA78BFA),
                    letterSpacing = 1.sp
                )
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions
            )
        }
    }
}

@Composable
fun WelcomeStepScreen(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Brand Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF13172A))
                    .border(2.dp, Color(0xFF8B5CF6).copy(alpha = 0.8f), CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.orbital_launcher_v2),
                    contentDescription = "Orbital App Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Welcome to Orbital",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Autonomous AI Companion & Mobile Automation Engine",
                color = Color(0xFF94A3B8),
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center
            )
        }

        // Feature Highlights
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OnboardingFeatureCard(
                icon = "🔒",
                title = "Local-First & Private",
                desc = "No telemetry or middleman servers. Keys encrypted directly on hardware."
            )
            OnboardingFeatureCard(
                icon = "🤖",
                title = "Autonomous Device Automation",
                desc = "Navigates apps, taps buttons, and completes workflows hands-free."
            )
            OnboardingFeatureCard(
                icon = "🛰️",
                title = "Laptop AI Bridge (MCP)",
                desc = "Connect wirelessly to Antigravity, Claude Code, or Cursor for live testing."
            )
        }

        // Action Button
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Get Started", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
fun PermissionsAndAccessibilityStepScreen(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isAccessibilityActive by remember { mutableStateOf(OrbitalAccessibilityService.isEnabled(context)) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var isOverlayActive by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityActive = OrbitalAccessibilityService.isEnabled(context)
                isOverlayActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            OnboardingStepProgressHeader(
                currentStepIndex = 3,
                title = "Automation Powers",
                subtitle = "Optional permissions for hands-free device navigation & mascot overlay",
                onBack = onBack
            )
        },
        containerColor = Color(0xFF090B13),
        bottomBar = {
            Surface(
                color = Color(0xFF0F1322),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFF1E243D))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNext,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E3856))
                    ) {
                        Text("Skip for Now", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1.5f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("Continue", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Accessibility Card
            Surface(
                color = Color(0xFF12162A),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    if (isAccessibilityActive) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF263056)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("🤖", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Screen Automation",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Accessibility Service (Optional)",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Live Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isAccessibilityActive) Color(0xFF10B981).copy(alpha = 0.2f)
                                    else Color(0xFF64748B).copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isAccessibilityActive) "🟢 Active" else "⚪ Not Enabled",
                                color = if (isAccessibilityActive) Color(0xFF34D399) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Allows Orbital to read visible UI elements and execute taps, text entry, and app navigation on your behalf.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Step-by-Step Instructions
                    Surface(
                        color = Color(0xFF171D36),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF28335C)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "📋 How to Enable:",
                                color = Color(0xFFA78BFA),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("1. Tap button below to open Accessibility settings.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            Text("2. Look under 'Downloaded apps' or 'Installed services'.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            Text("3. Tap 'Orbital' and toggle ON.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showAccessibilityDisclosure = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAccessibilityActive) Color(0xFF10B981) else Color(0xFF7C3AED)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isAccessibilityActive) "✅ Accessibility Active (Open Settings)" else "⚙️ Enable Accessibility in Settings",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Android 13+ Restricted Setting Helper
                    if (!isAccessibilityActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF22172B),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "🔒 Toggle grayed out? (Restricted Setting)",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Open App Info → tap (⋮) top-right → tap 'Allow restricted settings'.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF8B5CF6)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🔓 Open App Info to Allow Restricted Settings", fontSize = 11.sp, color = Color(0xFFA78BFA), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // Floating Overlay Permission Card
            Surface(
                color = Color(0xFF12162A),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    if (isOverlayActive) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF263056)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("🎭", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Floating Mascot Overlay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Draw Over Other Apps (Optional)", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Live Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isOverlayActive) Color(0xFF10B981).copy(alpha = 0.2f)
                                    else Color(0xFF64748B).copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isOverlayActive) "🟢 Active" else "⚪ Not Enabled",
                                color = if (isOverlayActive) Color(0xFF34D399) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Allows your chosen companion mascot to float on top of other apps for quick actions and status feedback.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayActive) Color(0xFF1E293B) else Color(0xFF1E2644)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isOverlayActive) "✅ Overlay Permission Granted" else "🎭 Enable Overlay Permission",
                            fontSize = 12.sp,
                            color = if (isOverlayActive) Color(0xFF34D399) else Color(0xFFCBD5E1),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(onDismiss = { showAccessibilityDisclosure = false })
    }
}

@Composable
fun CharacterSelectionStepScreen(
    selectedCharacter: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    Scaffold(
        topBar = {
            OnboardingStepProgressHeader(
                currentStepIndex = 4,
                title = "Choose Companion Mascot",
                subtitle = "Select your active AI persona and on-screen mascot personality",
                onBack = onBack
            )
        },
        containerColor = Color(0xFF090B13),
        bottomBar = {
            Surface(
                color = Color(0xFF0F1322),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFF1E243D))
            ) {
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                ) {
                    Text("🚀 Launch Orbital", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CharacterChoiceCard(
                id = "aether",
                name = "Aether",
                title = "The Celestial Overseer",
                desc = "Analytical, precise, and executive. Ideal for device automation, coding, and system control.",
                isSelected = selectedCharacter == "aether",
                onClick = { onSelect("aether") }
            )

            CharacterChoiceCard(
                id = "lumy",
                name = "Lumy",
                title = "The Luminous Guide",
                desc = "Friendly, creative, and proactive companion with expressive emotional animations.",
                isSelected = selectedCharacter == "lumy",
                onClick = { onSelect("lumy") }
            )

            CharacterChoiceCard(
                id = "volo",
                name = "Volo",
                title = "The Dynamic Tactician",
                desc = "High-energy, fast responses, tailored for rapid workflows, tasks, and notifications.",
                isSelected = selectedCharacter == "volo",
                onClick = { onSelect("volo") }
            )
        }
    }
}

@Composable
fun CharacterChoiceCard(
    id: String,
    name: String,
    title: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Color(0xFF1E1738) else Color(0xFF111424),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Color(0xFF8B5CF6) else Color(0xFF222842)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val spriteRes = MascotSpriteHelper.getSprite(id, MascotState.IDLE)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF7C3AED).copy(alpha = 0.3f) else Color(0xFF1E2540))
                    .border(
                        1.dp,
                        if (isSelected) Color(0xFF8B5CF6) else Color(0xFF333E63),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = spriteRes),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                Text(title, color = Color(0xFF38BDF8), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, color = Color(0xFF94A3B8), fontSize = 11.5.sp, lineHeight = 15.sp)
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun OnboardingFeatureCard(icon: String, title: String, desc: String) {
    Surface(
        color = Color(0xFF111424),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF202640)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, color = Color(0xFF94A3B8), fontSize = 11.5.sp, lineHeight = 15.sp)
            }
        }
    }
}
