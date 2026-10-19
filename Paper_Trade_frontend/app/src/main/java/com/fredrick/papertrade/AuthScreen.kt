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
import androidx.compose.ui.text.input.KeyboardType
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

data class User(val id: Long, val name: String, val email: String, val balance: Double)

// Separates HTTP/authentication failures from network and parsing failures so the UI can react appropriately.
class AuthException(message: String, val code: Int) : Exception(message)

// Converts the backend's public user JSON into the model used by Compose screens.
private fun parseUser(json: JSONObject) =
    User(json.getLong("id"), json.getString("name"), json.getString("email"), json.getDouble("balance"))

/** Sends a request to the backend and returns the JSON body, or throws AuthException with the server's message. */
suspend fun request(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject =
    withContext(Dispatchers.IO) {
        val conn = URL(BuildConfig.BACKEND_URL + path).openConnection() as HttpURLConnection
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
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DarkGreen,
        unfocusedBorderColor = SubtleText,
        focusedLabelColor = DarkGreen,
        unfocusedLabelColor = SubtleText,
        cursorColor = DarkGreen,
        focusedTextColor = TextGreen,
        unfocusedTextColor = TextGreen
    )

    fun submit() {
        // Validation is immediate for user feedback; the backend validates the same request again.
        error = null
        if (email.isBlank() || password.isBlank() || (isSignup && name.isBlank())) {
            error = "Please fill in all fields"
            return
        }
        if (isSignup && password.length < 6) {
            error = "Password must be at least 6 characters"
            return
        }
        loading = true
        scope.launch {
            try {
                val body = JSONObject().put("email", email).put("password", password)
                if (isSignup) body.put("name", name)
                val (token, user) = callAuth(if (isSignup) "/auth/signup" else "/auth/login", body)
                onAuthenticated(token, user)
            } catch (e: Exception) {
                error = if (e is java.io.IOException) "Can't reach the server" else e.message
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
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    colors = fieldColors, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = fieldColors, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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

            TextButton(onClick = { isSignup = !isSignup; error = null }) {
                Text(
                    if (isSignup) "Already have an account? Log in" else "New here? Create an account",
                    color = DarkGreen
                )
            }
        }
        BottomStrand()
    }
}
