package com.example.strivo.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.strivo.core.ui.components.AccentButton
import com.example.strivo.core.ui.components.StrivoSnackbarHost
import com.example.strivo.core.ui.components.showColored
import com.example.strivo.core.ui.theme.AppColors
import com.example.strivo.core.ui.theme.AppShapes
import com.example.strivo.core.ui.theme.AppType
import kotlinx.coroutines.launch

private val EmailRegex = Regex("^[\\w-.]+@([\\w-]+\\.)+[\\w-]{2,4}$")

private fun validateEmail(value: String): String? = when {
    value.isEmpty() -> "Email is required"
    !EmailRegex.matches(value) -> "Enter a valid email"
    else -> null
}

private fun validatePassword(value: String): String? = when {
    value.isEmpty() -> "Password is required"
    value.length < 6 -> "Password must be at least 6 characters"
    else -> null
}

@Composable
fun AuthScreen(authViewModel: AuthViewModel) {
    var isLogin by rememberSaveable { mutableStateOf(true) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = AppColors.Background,
        snackbarHost = { StrivoSnackbarHost(snackbarHost) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(48.dp))
                BrandMark()
                Spacer(Modifier.height(36.dp))

                AnimatedContent(
                    targetState = isLogin,
                    transitionSpec = {
                        (fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 20 }) togetherWith
                            (fadeOut(tween(600)) + slideOutVertically(tween(600)) { it / 20 })
                    },
                    label = "auth-card",
                ) { login ->
                    if (login) {
                        LoginCard(
                            authViewModel = authViewModel,
                            onFailure = { message -> scope.launch { snackbarHost.showColored(message) } },
                        )
                    } else {
                        RegisterCard(
                            authViewModel = authViewModel,
                            onFailure = { message -> scope.launch { snackbarHost.showColored(message) } },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { isLogin = !isLogin },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.TextPrimary),
                ) {
                    Text(
                        text = if (isLogin) "New to Strivo? Create an account" else "Already have an account? Log in",
                        style = AppType.BodyStrong,
                        color = AppColors.Accent,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun LoginCard(authViewModel: AuthViewModel, onFailure: (String) -> Unit) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    AuthCard(title = "Welcome back", subtitle = "Log in to pick up where you left off.") {
        AuthTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = "Email",
            icon = Icons.Outlined.Email,
            error = emailError,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = "Password",
            icon = Icons.Outlined.Lock,
            error = passwordError,
            isPassword = true,
        )
        Spacer(Modifier.height(24.dp))
        AuthSubmit(text = "Log in", isLoading = auth.isLoading) {
            emailError = validateEmail(email)
            passwordError = validatePassword(password)
            if (emailError == null && passwordError == null) {
                scope.launch {
                    val error = authViewModel.login(email.trim(), password.trim())
                    if (error != null) onFailure(error)
                }
            }
        }
    }
}

@Composable
private fun RegisterCard(authViewModel: AuthViewModel, onFailure: (String) -> Unit) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    AuthCard(title = "Create your account", subtitle = "Plan your workouts, track every set, and see your progress.") {
        AuthTextField(
            value = name,
            onValueChange = { name = it; nameError = null },
            label = "Name",
            icon = Icons.Outlined.Person,
            error = nameError,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = email,
            onValueChange = { email = it; emailError = null },
            label = "Email",
            icon = Icons.Outlined.Email,
            error = emailError,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = "Password",
            icon = Icons.Outlined.Lock,
            error = passwordError,
            isPassword = true,
        )
        Spacer(Modifier.height(24.dp))
        AuthSubmit(text = "Create account", isLoading = auth.isLoading) {
            nameError = if (name.isEmpty()) "Name is required" else null
            emailError = validateEmail(email)
            passwordError = validatePassword(password)
            if (nameError == null && emailError == null && passwordError == null) {
                scope.launch {
                    val error = authViewModel.register(name.trim(), email.trim(), password.trim())
                    if (error != null) onFailure(error)
                }
            }
        }
    }
}

/** The app's mark: an accent tile with a dumbbell, and the name beside it. */
@Composable
private fun BrandMark() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(AppColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = Color.Black, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("Strivo", style = AppType.Title, color = AppColors.TextPrimary)
    }
}

/** Title, a line of context, then the form, left-aligned on the page rather than boxed in a card. */
@Composable
private fun AuthCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Text(title, style = AppType.LargeTitle, color = AppColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = AppType.Body, color = AppColors.TextSecondary)
        Spacer(Modifier.height(28.dp))
        content()
    }
}

@Composable
private fun AuthSubmit(text: String, isLoading: Boolean, onClick: () -> Unit) {
    if (isLoading) {
        Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    } else {
        AccentButton(
            text = text,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            height = 54.dp,
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    error: String?,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val shape = AppShapes.Control
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = AppColors.TextSecondary) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        isError = error != null,
        supportingText = error?.let { { Text(it, color = AppColors.Danger) } },
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            errorTextColor = AppColors.TextPrimary,
            focusedContainerColor = AppColors.Surface,
            unfocusedContainerColor = AppColors.Surface,
            errorContainerColor = AppColors.Surface,
            focusedBorderColor = AppColors.Accent,
            unfocusedBorderColor = AppColors.Surface,
            errorBorderColor = AppColors.Danger,
            focusedLabelColor = AppColors.TextSecondary,
            unfocusedLabelColor = AppColors.TextSecondary,
            errorLabelColor = AppColors.TextSecondary,
            cursorColor = AppColors.Accent,
        ),
    )
}
