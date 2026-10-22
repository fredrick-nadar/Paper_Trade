package com.fredrick.papertrade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Background = Color(0xFFCFE8D2)
private val DarkGreen = Color(0xFF315D3C)
private val TextGreen = Color(0xFF1F3D2A)
private val SubtleText = Color(0xFF4A6B55)
private val ErrorRed = Color(0xFFC62828)
private const val BACKEND_CONNECT_TIMEOUT_MILLIS = 30_000
private const val BACKEND_READ_TIMEOUT_MILLIS = 30_000

data class User(val id: Long, val publicId: String, val name: String, val email: String, val balance: Double)

// Separates HTTP/authentication failures from network and parsing failures so the UI can react appropriately.
class AuthException(message: String, val code: Int) : Exception(message)

private const val MIN_PASSWORD_LENGTH = 6
private const val MAX_NAME_LENGTH = 50

private fun nameError(name: String) = when {
    name.isBlank() -> "Enter your name"
    name.trim().length > MAX_NAME_LENGTH -> "Name must be $MAX_NAME_LENGTH characters or fewer"
    else -> null
}

private fun emailError(email: String) = when {
    email.isBlank() -> "Enter your email"
    !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email, like name@example.com"
    else -> null
}

// Login only checks for presence so users with older passwords are never blocked client-side.
private fun passwordError(password: String, isSignup: Boolean) = when {
    password.isEmpty() -> "Enter your password"
    isSignup && password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters"
    else -> null
}

/** Turns a failed request into a message the user can act on. */
private fun friendlyError(e: Exception): String = when (e) {
    is java.net.SocketTimeoutException ->
        "The server is taking too long to respond. It may be waking up, so please try again in a moment."
    is java.net.UnknownHostException -> "No internet connection. Check your network and try again."
    is java.io.IOException -> "Can't reach the server. Check your connection and try again."
    is AuthException -> if (e.code >= 500) "The server hit a problem. Please try again shortly." else e.message.orEmpty()
    else -> "Something went wrong. Please try again."
}

// Converts the backend's public user JSON into the model used by Compose screens.
private fun parseUser(json: JSONObject) =
    User(json.getLong("id"), json.getString("publicId"), json.getString("name"), json.getString("email"), json.getDouble("balance"))

/** Sends a request to the backend and returns the JSON body, or throws AuthException with the server's message. */
suspend fun request(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject =
    withContext(Dispatchers.IO) {
        val conn = URL(Backend.baseUrl() + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            // Render free services can take time to wake after being idle.
            conn.connectTimeout = BACKEND_CONNECT_TIMEOUT_MILLIS
            conn.readTimeout = BACKEND_READ_TIMEOUT_MILLIS
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }

            if (conn.responseCode in 200..299) {
                JSONObject(conn.inputStream.bufferedReader().readText())
            } else {
                val text = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                val message = runCatching { JSONObject(text).getString("message") }.getOrNull()
                // Spring's default error body has no "message", so map the common statuses here.
                throw AuthException(
                    when (conn.responseCode) {
                        400 -> "Please check your details"
                        401 -> message ?: "Invalid email or password"
                        409 -> message ?: "Email already registered"
                        else -> message ?: "Something went wrong"
                    },
                    conn.responseCode
                )
            }
        } catch (e: java.io.IOException) {
            // Re-detect local vs production on the next request.
            Backend.reset()
            throw e
        } finally {
            conn.disconnect()
        }
    }

/** Login/signup: returns the JWT and the user. */
private suspend fun callAuth(path: String, body: JSONObject): Pair<String, User> {
    val json = request("POST", path, body)
    return json.getString("token") to parseUser(json.getJSONObject("user"))
}

/** Checks a saved JWT with the backend. Throws AuthException(401) if it is invalid or expired. */
suspend fun fetchCurrentUser(token: String): User = parseUser(request("GET", "/auth/me", token = token))

/** Displays signup/login fields and returns the authenticated user to MainActivity. */
@Composable
fun AuthScreen(onAuthenticated: (token: String, user: User) -> Unit) {
    var isSignup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nameErr by remember { mutableStateOf<String?>(null) }
    var emailErr by remember { mutableStateOf<String?>(null) }
    var passwordErr by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DarkGreen,
        unfocusedBorderColor = SubtleText,
        focusedLabelColor = DarkGreen,
        unfocusedLabelColor = SubtleText,
        cursorColor = DarkGreen,
        focusedTextColor = TextGreen,
        unfocusedTextColor = TextGreen,
        errorBorderColor = ErrorRed,
        errorLabelColor = ErrorRed,
        errorSupportingTextColor = ErrorRed,
        errorCursorColor = ErrorRed
    )

    fun submit() {
        // Validation is immediate for user feedback; the backend validates the same request again.
        if (loading) return
        error = null
        nameErr = if (isSignup) nameError(name) else null
        emailErr = emailError(email)
        passwordErr = passwordError(password, isSignup)
        if (nameErr != null || emailErr != null || passwordErr != null) return

        focusManager.clearFocus()
        loading = true
        scope.launch {
            try {
                val body = JSONObject().put("email", email.trim()).put("password", password)
                if (isSignup) body.put("name", name.trim())
                val (token, user) = callAuth(if (isSignup) "/auth/signup" else "/auth/login", body)
                // onAuthenticated runs the CleverTap login first, so these events land on the right profile.
                onAuthenticated(token, user)
                if (isSignup) Analytics.signedUp(context) else Analytics.loggedIn(context)
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isSignup) "Create account" else "Welcome back",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = TextGreen
            )
            Text(
                if (isSignup) "Sign up and get $1,000 virtual money" else "Log in to continue",
                fontSize = 15.sp,
                color = SubtleText
            )
            Spacer(Modifier.height(24.dp))

            if (isSignup) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; nameErr = null },
                    label = { Text("Name") }, singleLine = true,
                    isError = nameErr != null,
                    supportingText = nameErr?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it; emailErr = null },
                label = { Text("Email") }, singleLine = true,
                isError = emailErr != null,
                supportingText = emailErr?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = fieldColors, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it; passwordErr = null },
                label = { Text("Password") }, singleLine = true,
                isError = passwordErr != null,
                supportingText = (passwordErr
                    ?: if (isSignup) "At least $MIN_PASSWORD_LENGTH characters" else null)?.let { { Text(it) } },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide" else "Show", color = DarkGreen)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = fieldColors, modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = ErrorRed, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = ::submit,
                enabled = !loading,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (loading) "Please wait..." else if (isSignup) "Sign up" else "Log in", fontSize = 17.sp)
            }

            TextButton(onClick = {
                isSignup = !isSignup
                error = null; nameErr = null; emailErr = null; passwordErr = null
            }) {
                Text(
                    if (isSignup) "Already have an account? Log in" else "New here? Create an account",
                    color = DarkGreen
                )
            }
        }
        BottomStrand()
    }
}
