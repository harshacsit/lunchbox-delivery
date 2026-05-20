package com.lunchbox.delivery.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * MpinActivity — FIXED (no XML layout required)
 *
 * WHY IT CRASHED BEFORE:
 *   activity_mpin.xml used:
 *     style="@style/Widget.MaterialComponents.Button.OutlinedButton"
 *   This requires MaterialComponents theme AND the style to exist in your project.
 *   On devices/emulators where the theme was not properly applied, this inflated
 *   to null and caused NullPointerException.
 *
 * FIX: Build the entire MPIN screen in Java code — no XML needed at all.
 *   This is guaranteed to work on every Android device regardless of theme.
 *
 * MODES:
 *   "set"    — first time setup, shows Skip button
 *   "verify" — daily open, no Skip button
 */
public class MpinActivity extends AppCompatActivity {

    private static final String PREFS    = "lunchbox_security";
    private static final String KEY_MPIN = "mpin_hash";
    private static final int    LENGTH   = 4;

    private String mode = "verify";
    private final StringBuilder entered = new StringBuilder();

    // UI references
    private View[] dots = new View[4];
    private TextView tvTitle, tvSubtitle, tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mode = "verify";
        if (getIntent() != null && getIntent().getStringExtra("mode") != null) {
            mode = getIntent().getStringExtra("mode");
        }

        // Build entire UI in code — no XML, no crash
        setContentView(buildUI());
    }

    // ── Build UI entirely in Java ──
    private ScrollView buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F5F5F5"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(48), dp(32), dp(32));

        // Orange circle icon
        TextView icon = new TextView(this);
        icon.setText("🍱");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(makeCircle("#E65100", dp(72)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconParams.bottomMargin = dp(24);
        icon.setLayoutParams(iconParams);
        root.addView(icon);

        // Title
        tvTitle = new TextView(this);
        tvTitle.setText("set".equals(mode) ? "Set Your MPIN" : "Enter Your MPIN");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvTitle.setTextColor(Color.parseColor("#212121"));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.gravity = Gravity.CENTER_HORIZONTAL;
        titleParams.bottomMargin = dp(8);
        tvTitle.setLayoutParams(titleParams);
        root.addView(tvTitle);

        // Subtitle
        tvSubtitle = new TextView(this);
        tvSubtitle.setText("set".equals(mode)
                ? "Create a 4-digit PIN for quick access"
                : "Enter your 4-digit PIN to continue");
        tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvSubtitle.setTextColor(Color.parseColor("#757575"));
        tvSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.gravity = Gravity.CENTER_HORIZONTAL;
        subParams.bottomMargin = dp(32);
        tvSubtitle.setLayoutParams(subParams);
        root.addView(tvSubtitle);

        // 4 dot indicators
        LinearLayout dotRow = new LinearLayout(this);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotRowParams.gravity = Gravity.CENTER_HORIZONTAL;
        dotRowParams.bottomMargin = dp(32);
        dotRow.setLayoutParams(dotRowParams);

        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dp18 = new LinearLayout.LayoutParams(dp(18), dp(18));
            dp18.setMargins(dp(10), 0, dp(10), 0);
            dot.setLayoutParams(dp18);
            dot.setBackground(makeCircle("#CCCCCC", dp(18)));
            dots[i] = dot;
            dotRow.addView(dot);
        }
        root.addView(dotRow);

        // Error message
        tvError = new TextView(this);
        tvError.setText("Incorrect PIN. Try again.");
        tvError.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvError.setTextColor(Color.parseColor("#C62828"));
        tvError.setGravity(Gravity.CENTER);
        tvError.setVisibility(View.GONE);
        LinearLayout.LayoutParams errParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errParams.gravity = Gravity.CENTER_HORIZONTAL;
        errParams.bottomMargin = dp(12);
        tvError.setLayoutParams(errParams);
        root.addView(tvError);

        // Number pad — 3 rows + bottom row
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(4);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.gravity = Gravity.CENTER_HORIZONTAL;
        gridParams.bottomMargin = dp(20);
        grid.setLayoutParams(gridParams);

        String[] labels = {"1","2","3","4","5","6","7","8","9","⌫","0",""};
        for (String label : labels) {
            if (label.isEmpty()) {
                // Empty spacer
                View spacer = new View(this);
                GridLayout.LayoutParams sp = new GridLayout.LayoutParams();
                sp.width  = dp(80);
                sp.height = dp(80);
                sp.setMargins(dp(6), dp(6), dp(6), dp(6));
                spacer.setLayoutParams(sp);
                grid.addView(spacer);
                continue;
            }
            Button btn = new Button(this);
            btn.setText(label);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.equals("⌫") ? 20 : 24);
            btn.setTextColor(Color.parseColor("#212121"));
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            btn.setBackground(makeCircle("#FFFFFF", dp(80)));
            btn.setElevation(dp(2));
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width  = dp(80);
            gp.height = dp(80);
            gp.setMargins(dp(6), dp(6), dp(6), dp(6));
            btn.setLayoutParams(gp);
            final String lbl = label;
            btn.setOnClickListener(v -> onDigit(lbl));
            grid.addView(btn);
        }
        root.addView(grid);

        // Skip button (set mode only)
        if ("set".equals(mode)) {
            TextView skip = new TextView(this);
            skip.setText("Skip for now");
            skip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            skip.setTextColor(Color.parseColor("#9E9E9E"));
            skip.setGravity(Gravity.CENTER);
            skip.setPadding(dp(16), dp(10), dp(16), dp(10));
            skip.setClickable(true);
            skip.setFocusable(true);
            LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            skipParams.gravity = Gravity.CENTER_HORIZONTAL;
            skip.setLayoutParams(skipParams);
            skip.setOnClickListener(v -> goToMain());
            root.addView(skip);
        }

        scroll.addView(root);
        return scroll;
    }

    private void onDigit(String label) {
        tvError.setVisibility(View.GONE);
        if ("⌫".equals(label)) {
            if (entered.length() > 0) entered.deleteCharAt(entered.length() - 1);
        } else if (entered.length() < LENGTH) {
            entered.append(label);
        }
        updateDots();
        if (entered.length() == LENGTH) {
            new Handler(Looper.getMainLooper()).postDelayed(this::submit, 180);
        }
    }

    private void updateDots() {
        for (int i = 0; i < 4; i++) {
            String hex = i < entered.length() ? "#E65100" : "#CCCCCC";
            dots[i].setBackground(makeCircle(hex, dp(18)));
        }
    }

    private void submit() {
        String pin = entered.toString();
        if ("set".equals(mode)) {
            savePin(pin);
            goToMain();
        } else {
            if (checkPin(pin)) {
                goToMain();
            } else {
                showError();
            }
        }
    }

    private void showError() {
        tvError.setVisibility(View.VISIBLE);
        // Shake dots
        for (View dot : dots) {
            dot.animate().translationX(-8f).setDuration(60)
                    .withEndAction(() -> dot.animate().translationX(8f).setDuration(60)
                            .withEndAction(() -> dot.animate().translationX(0f).setDuration(60).start()).start())
                    .start();
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            entered.setLength(0);
            updateDots();
        }, 600);
    }

    private void goToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void savePin(String pin) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_MPIN, sha256(pin)).apply();
    }

    private boolean checkPin(String pin) {
        String stored = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_MPIN, "");
        return sha256(pin).equals(stored);
    }

    public static boolean hasMpin(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE).contains(KEY_MPIN);
    }

    public static void clearMpin(android.content.Context ctx) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_MPIN).apply();
    }

    private static String sha256(String input) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] bytes = d.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }

    // Make a circular drawable
    private GradientDrawable makeCircle(String hex, int size) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(hex));
        return gd;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}