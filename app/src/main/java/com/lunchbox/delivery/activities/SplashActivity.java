package com.lunchbox.delivery.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.lunchbox.delivery.R;

/**
 * SplashActivity — FIXED
 *
 * CRASH FIX: The old version called FirebaseFirestore.getInstance()
 * and setPersistenceEnabled() here. On some devices this causes:
 *   "FirebaseApp is not initialized" or
 *   "Cannot enable persistence after Firestore is already in use"
 * Both crash the app before the user sees anything.
 *
 * FIX: Remove ALL Firestore calls from SplashActivity.
 * Firestore persistence is enabled automatically by the Firebase SDK.
 * Do NOT call it manually.
 *
 * FLOW:
 *   Splash (0.8s) → LoginActivity (if not logged in)
 *                 → MpinActivity  (if logged in)
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Short delay then route — 800ms is enough, 1200ms feels slow
        new Handler(Looper.getMainLooper()).postDelayed(this::route, 800);
    }

    private void route() {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                startActivity(new Intent(this, LoginActivity.class));
            } else {
                String mode = MpinActivity.hasMpin(this) ? "verify" : "set";
                Intent i = new Intent(this, MpinActivity.class);
                i.putExtra("mode", mode);
                startActivity(i);
            }
        } catch (Exception e) {
            // Any error → go to login (safe fallback)
            startActivity(new Intent(this, LoginActivity.class));
        }
        finish();
    }
}