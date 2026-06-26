package com.scalendar.ui.screen.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.res.stringResource
import com.scalendar.R

// ── Auth step state machine ───────────────────────────────────────────
private enum class AuthStep { LANDING, LOGIN, REGISTER }

// ═══════════════════════════════════════════════════════════════════════
// Root — AuthScreen
// ═══════════════════════════════════════════════════════════════════════
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var step            by remember { mutableStateOf(AuthStep.LANDING) }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword    by remember { mutableStateOf(false) }

    // Back navigation
    BackHandler(enabled = step != AuthStep.LANDING) {
        when (step) {
            AuthStep.LOGIN    -> { step = AuthStep.LANDING; password = "" }
            AuthStep.REGISTER -> { step = AuthStep.LOGIN; confirmPassword = "" }
            else -> {}
        }
        viewModel.clearError()
    }

    // Google Sign-In
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn
                    .getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                account.idToken?.let { viewModel.signInWithGoogle(it) }
            } catch (_: ApiException) { /* cancelled */ }
        }
    }
    fun launchGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            googleLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    // Error → Snackbar (auto-switch to REGISTER when account not found)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMsg) {
        val msg = uiState.errorMsg ?: return@LaunchedEffect
        if (step == AuthStep.LOGIN && "Không tìm thấy tài khoản" in msg) {
            step = AuthStep.REGISTER
            viewModel.clearError()
            return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState    = step,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal)
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                else
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            },
            label    = "authStep",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { currentStep ->
            when (currentStep) {
                AuthStep.LANDING -> LandingStep(
                    isLoading       = uiState.isLoading,
                    onContinueEmail = { step = AuthStep.LOGIN; viewModel.clearError() },
                    onGoogleSignIn  = ::launchGoogle,
                )
                AuthStep.LOGIN -> LoginStep(
                    email              = email,
                    onEmailChange      = { email = it },
                    password           = password,
                    onPasswordChange   = { password = it },
                    showPassword       = showPassword,
                    onTogglePassword   = { showPassword = !showPassword },
                    isLoading          = uiState.isLoading,
                    onBack             = { step = AuthStep.LANDING; password = ""; viewModel.clearError() },
                    onLogin            = { keyboard?.hide(); viewModel.signIn(email, password) },
                    onSwitchToRegister = { step = AuthStep.REGISTER; viewModel.clearError() },
                )
                AuthStep.REGISTER -> RegisterStep(
                    email            = email,
                    onEmailChange    = { email = it },
                    password         = password,
                    confirmPassword  = confirmPassword,
                    showPassword     = showPassword,
                    onPasswordChange = { password = it },
                    onConfirmChange  = { confirmPassword = it },
                    onTogglePassword = { showPassword = !showPassword },
                    isLoading        = uiState.isLoading,
                    onBack           = { step = AuthStep.LOGIN; confirmPassword = ""; viewModel.clearError() },
                    onRegister       = { keyboard?.hide(); viewModel.signUp(email, password) },
                    onSwitchToLogin  = { step = AuthStep.LOGIN; confirmPassword = ""; viewModel.clearError() },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Step 1 — Landing: logo + hero + action buttons
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun LandingStep(
    isLoading      : Boolean,
    onContinueEmail: () -> Unit,
    onGoogleSignIn : () -> Unit,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        // App logo + name
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AutoStories, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Scalendar",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.weight(1f))

        // Hero illustration
        HeroIllustration()

        Spacer(Modifier.height(28.dp))

        Text(
            stringResource(R.string.auth_hero_title),
            style     = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 38.sp,
            ),
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.auth_hero_subtitle),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        // Tiếp tục với Email
        Button(
            onClick  = onContinueEmail,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape    = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Email, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.auth_continue_email), style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))

        // Tiếp tục với Google
        GoogleButton(onClick = onGoogleSignIn, enabled = !isLoading)

        Spacer(Modifier.height(40.dp))
    }
}

// Hero: 3 icon cards (Notes top-left, Calendar center, Journal bottom-right)
@Composable
private fun HeroIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        // Notes card — top-left
        Box(
            modifier         = Modifier
                .size(82.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .align(Alignment.TopStart)
                .offset(x = 16.dp, y = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.EditNote, null,
                tint     = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(42.dp),
            )
        }
        // Calendar card — center (main)
        Box(
            modifier         = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.CalendarMonth, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(76.dp),
            )
        }
        // Journal card — bottom-right
        Box(
            modifier         = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .align(Alignment.BottomEnd)
                .offset(x = (-16).dp, y = (-12).dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook, null,
                tint     = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(46.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Step 2 — Login: email + password
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun LoginStep(
    email             : String,
    onEmailChange     : (String) -> Unit,
    password          : String,
    onPasswordChange  : (String) -> Unit,
    showPassword      : Boolean,
    onTogglePassword  : () -> Unit,
    isLoading         : Boolean,
    onBack            : () -> Unit,
    onLogin           : () -> Unit,
    onSwitchToRegister: () -> Unit,
) {
    val emailFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { emailFocus.requestFocus() }

    val isValid = email.contains("@") && email.contains(".") && password.length >= 6

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.auth_login_title),
            style    = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.auth_login_welcome),
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value           = email,
            onValueChange   = onEmailChange,
            label           = { Text(stringResource(R.string.auth_email)) },
            modifier        = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocus),
            singleLine      = true,
            shape           = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction    = ImeAction.Next,
            ),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value         = password,
            onValueChange = onPasswordChange,
            label         = { Text(stringResource(R.string.auth_password)) },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(14.dp),
            visualTransformation = if (showPassword) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (isValid) onLogin() }),
            trailingIcon  = {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                    )
                }
            },
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick  = onLogin,
            enabled  = isValid && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape    = RoundedCornerShape(14.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color       = MaterialTheme.colorScheme.onPrimary,
                    modifier    = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.auth_tab_signin), style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onSwitchToRegister) {
            Text(
                stringResource(R.string.auth_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Step 3 — Register: email + password + confirm
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun RegisterStep(
    email           : String,
    onEmailChange   : (String) -> Unit,
    password        : String,
    confirmPassword : String,
    showPassword    : Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmChange : (String) -> Unit,
    onTogglePassword: () -> Unit,
    isLoading       : Boolean,
    onBack          : () -> Unit,
    onRegister      : () -> Unit,
    onSwitchToLogin : () -> Unit,
) {
    val emailFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (email.isBlank()) emailFocus.requestFocus() }

    val passwordsMatch = confirmPassword.isEmpty() || password == confirmPassword
    val isValid        = email.contains("@") && email.contains(".") &&
                         password.length >= 6 && confirmPassword.length >= 6 &&
                         password == confirmPassword

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.auth_register_title),
            style    = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.auth_password_min),
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        // Email (pre-filled from LOGIN step, editable)
        OutlinedTextField(
            value           = email,
            onValueChange   = onEmailChange,
            label           = { Text(stringResource(R.string.auth_email)) },
            modifier        = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocus),
            singleLine      = true,
            shape           = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction    = ImeAction.Next,
            ),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value         = password,
            onValueChange = onPasswordChange,
            label         = { Text(stringResource(R.string.auth_password)) },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(14.dp),
            visualTransformation = if (showPassword) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Next,
            ),
            trailingIcon  = {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                    )
                }
            },
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value         = confirmPassword,
            onValueChange = onConfirmChange,
            label         = { Text(stringResource(R.string.auth_confirm_password)) },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(14.dp),
            isError       = !passwordsMatch,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (isValid) onRegister() }),
            supportingText  = if (!passwordsMatch) {
                { Text(stringResource(R.string.auth_password_mismatch), fontSize = 12.sp) }
            } else null,
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick  = onRegister,
            enabled  = isValid && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape    = RoundedCornerShape(14.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color       = MaterialTheme.colorScheme.onPrimary,
                    modifier    = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.auth_tab_signup), style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onSwitchToLogin) {
            Text(
                stringResource(R.string.auth_has_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Shared — Google button
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun GoogleButton(onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape    = RoundedCornerShape(14.dp),
    ) {
        GoogleLogo(modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.auth_continue_google),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
        )
    }
}

@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2f;   val cy = h / 2f;  val r = w / 2f
        val rect = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)
        val sz   = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        val tl   = androidx.compose.ui.geometry.Offset(cx - r, cy - r)
        drawArc(androidx.compose.ui.graphics.Color(0xFF4285F4), -30f,  120f, true, tl, sz)
        drawArc(androidx.compose.ui.graphics.Color(0xFF34A853),  90f,  120f, true, tl, sz)
        drawArc(androidx.compose.ui.graphics.Color(0xFFFBBC05), 210f,   60f, true, tl, sz)
        drawArc(androidx.compose.ui.graphics.Color(0xFFEA4335), 270f,   60f, true, tl, sz)
        drawCircle(androidx.compose.ui.graphics.Color.White, r * 0.55f,
            androidx.compose.ui.geometry.Offset(cx, cy))
    }
}
