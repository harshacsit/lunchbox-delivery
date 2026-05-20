package com.lunchbox.delivery.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.lunchbox.delivery.R;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private TextView tvError;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Guard flag — prevents double-tap crash
    private boolean isAuthInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // ── Safe Firebase init ──
        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        btnLogin    = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        tvError     = findViewById(R.id.tvError);

        btnLogin.setOnClickListener(v -> {
            hideKeyboard();
            if (!isAuthInProgress) attemptLogin();
        });
    }

    private void attemptLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // ── Client-side validation (fast, no network needed) ──
        if (TextUtils.isEmpty(email)) {
            showError("Please enter your email address");
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address");
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showError("Please enter your password");
            etPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        hideError();
        setLoading(true);
        isAuthInProgress = true;

        // ── Firebase Authentication ──
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    // Auth passed — now fetch Firestore role
                    String uid = authResult.getUser().getUid();
                    fetchUserRoleAndNavigate(uid);
                })
                .addOnFailureListener(e -> {
                    isAuthInProgress = false;
                    setLoading(false);
                    handleAuthError(e);
                });
    }

    private void fetchUserRoleAndNavigate(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    isAuthInProgress = false;
                    setLoading(false);

                    if (doc == null || !doc.exists()) {
                        // Auth worked but Firestore doc missing
                        showError("Your account is not set up yet.\nContact your admin.");
                        mAuth.signOut();
                        return;
                    }

                    String role = doc.getString("role");

                    if ("delivery".equals(role)) {
                        // ── SUCCESS: go to main screen ──
                        navigateTo(MainActivity.class);
                    } else if ("admin".equals(role)) {
                        showError("Admins use the web dashboard.\nThis app is for delivery staff only.");
                        mAuth.signOut();
                    } else {
                        showError("Unknown account role. Contact your admin.");
                        mAuth.signOut();
                    }
                })
                .addOnFailureListener(e -> {
                    isAuthInProgress = false;
                    setLoading(false);
                    // Firestore failed but auth worked — show specific message
                    showError("Login successful but could not load profile.\nCheck your internet connection and try again.");
                    mAuth.signOut();
                });
    }

    private void handleAuthError(Exception e) {
        // Map Firebase errors to human-readable messages
        if (e instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            if ("ERROR_USER_NOT_FOUND".equals(code)) {
                showError("No account found with this email.\nContact your admin to get your credentials.");
            } else if ("ERROR_USER_DISABLED".equals(code)) {
                showError("This account has been disabled.\nContact your admin.");
            } else {
                showError("Account not found. Contact your admin.");
            }
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("password") || msg.contains("INVALID_LOGIN_CREDENTIALS")) {
                showError("Wrong password. Please try again.\nContact your admin if you forgot it.");
            } else {
                showError("Invalid email or password.");
            }
        } else if (e.getMessage() != null && e.getMessage().contains("network")) {
            showError("No internet connection.\nPlease check your Wi-Fi or mobile data.");
        } else if (e.getMessage() != null && e.getMessage().contains("TOO_MANY_REQUESTS")) {
            showError("Too many failed attempts. Try again in a few minutes.");
        } else {
            showError("Login failed. Please check your credentials and try again.");
        }
    }

    // ── Navigation ──
    private void navigateTo(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // ── UI Helpers ──
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Signing in..." : "Sign In");
        btnLogin.setAlpha(loading ? 0.7f : 1.0f);
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isAuthInProgress = false;
    }
}