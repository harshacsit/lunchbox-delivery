package com.lunchbox.delivery.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.fragments.HomeFragment;
import com.lunchbox.delivery.fragments.ProfileFragment;
import com.lunchbox.delivery.fragments.RouteFragment;
import com.lunchbox.delivery.fragments.SettingsFragment;
import com.lunchbox.delivery.models.Delivery;
import com.lunchbox.delivery.utils.NetworkMonitor;
import com.lunchbox.delivery.utils.OfflineQueueManager;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity — crash-proof version
 *
 * Every single view lookup is null-checked.
 * Animation try/catch means missing anim files never crash.
 * Singleton instance is set before setContentView so fragments can access it.
 */
public class MainActivity extends AppCompatActivity {

    // Shared data — all fragments read from here
    public final List<Delivery> allDeliveries = new ArrayList<>();
    public String currentUserId;

    private TextView tvPendingChip, tvDoneChip, tvDelayedChip;
    private TextView tvNameHeader, tvGreetingHeader, tvHeaderInitials;
    private BottomNavigationView bottomNav;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration deliveryListener;
    private NetworkMonitor networkMonitor;

    // Singleton — fragments use this to access allDeliveries
    private static MainActivity instance;
    public static MainActivity getInstance() { return instance; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set instance BEFORE setContentView so if fragment is restored it can access data
        instance = this;

        // Check auth
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            goTo(LoginActivity.class);
            return;
        }
        currentUserId = mAuth.getCurrentUser().getUid();
        db = FirebaseFirestore.getInstance();

        // Inflate layout
        setContentView(R.layout.activity_main);

        // Bind views — every one is null-safe
        tvGreetingHeader = findViewById(R.id.tvGreetingHeader);
        tvNameHeader     = findViewById(R.id.tvNameHeader);
        tvHeaderInitials = findViewById(R.id.tvHeaderInitials);
        tvPendingChip    = findViewById(R.id.chipPending);
        tvDoneChip       = findViewById(R.id.chipDone);
        tvDelayedChip    = findViewById(R.id.chipDelayed);
        bottomNav        = findViewById(R.id.bottomNav);

        // Greeting
        setGreeting();

        // Avatar tap → Profile tab
        if (tvHeaderInitials != null) {
            tvHeaderInitials.setClickable(true);
            tvHeaderInitials.setFocusable(true);
            tvHeaderInitials.setOnClickListener(v -> {
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                        .start();
                selectTab(R.id.nav_profile);
            });
        }

        // Status chips → filter list
        if (tvPendingChip != null) tvPendingChip.setOnClickListener(v -> filterFromChip("Pending", v));
        if (tvDoneChip    != null) tvDoneChip.setOnClickListener(v    -> filterFromChip("Picked", v));
        if (tvDelayedChip != null) tvDelayedChip.setOnClickListener(v -> filterFromChip("Delayed", v));

        // Bottom navigation
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                Fragment frag;
                if      (id == R.id.nav_home)     frag = new HomeFragment();
                else if (id == R.id.nav_route)    frag = new RouteFragment();
                else if (id == R.id.nav_profile)  frag = new ProfileFragment();
                else                              frag = new SettingsFragment();
                loadFragment(frag, true);
                return true;
            });
        }

        // Load user name into header
        loadUserName();

        // Network monitor — flush offline queue when back online
        networkMonitor = new NetworkMonitor(this, online -> {
            if (online && OfflineQueueManager.hasPending(this)) {
                OfflineQueueManager.flush(this);
            }
        });

        // Refresh FCM token
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                if (token != null && currentUserId != null)
                    db.collection("users").document(currentUserId).update("fcmToken", token);
            });
        } catch (Exception ignored) {}

        // Load home screen and start listening for deliveries
        loadFragment(new HomeFragment(), false);
        startDeliveryListener();
    }

    // ── Load a fragment with transition animation ──
    private void loadFragment(Fragment fragment, boolean animate) {
        try {
            FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
            if (animate) {
                try {
                    tx.setCustomAnimations(
                            R.anim.slide_in_up, R.anim.fade_out,
                            R.anim.fade_in,     R.anim.slide_out_down);
                } catch (Exception ignored) {
                    // anim files missing — skip animation, no crash
                }
            }
            tx.replace(R.id.fragmentContainer, fragment).commitAllowingStateLoss();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "loadFragment failed: " + e.getMessage());
        }
    }

    // ── Select a bottom nav tab ──
    private void selectTab(int navId) {
        if (bottomNav != null) {
            try { bottomNav.setSelectedItemId(navId); } catch (Exception ignored) {}
        }
    }

    // ── Chip tap: filter the home list ──
    private void filterFromChip(String filter, View v) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                .start();
        Fragment cur = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (cur instanceof HomeFragment) {
            ((HomeFragment) cur).applyFilter(filter);
        } else {
            HomeFragment.setPendingFilter(filter);
            selectTab(R.id.nav_home);
        }
    }

    // ── Firestore real-time listener ──
    public void startDeliveryListener() {
        if (currentUserId == null) return;
        if (deliveryListener != null) deliveryListener.remove();

        deliveryListener = db.collection("deliveries")
                .whereEqualTo("assignedTo", currentUserId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        android.util.Log.e("MainActivity", "Delivery listener error: " + error.getMessage());
                        return;
                    }
                    if (snapshots == null) return;

                    allDeliveries.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        try {
                            Delivery d = doc.toObject(Delivery.class);
                            d.setDeliveryId(doc.getId());
                            allDeliveries.add(d);
                        } catch (Exception e) {
                            android.util.Log.e("MainActivity", "Delivery parse error: " + e.getMessage());
                        }
                    }

                    // Sort by pickup order
                    allDeliveries.sort((a, b) -> {
                        int oa = a.getPickupOrder() > 0 ? a.getPickupOrder() : 9999;
                        int ob = b.getPickupOrder() > 0 ? b.getPickupOrder() : 9999;
                        return Integer.compare(oa, ob);
                    });

                    updateChips();
                    notifyCurrentFragment();
                });
    }

    // ── Update header count chips ──
    private void updateChips() {
        int p = 0, k = 0, dl = 0;
        for (Delivery d : allDeliveries) {
            String s = d.getStatus() != null ? d.getStatus() : "";
            switch (s) {
                case "Pending":   p++;  break;
                case "Picked":
                case "Delivered": k++;  break;
                case "Delayed":   dl++; break;
            }
        }
        final int fp = p, fk = k, fdl = dl;
        runOnUiThread(() -> {
            try {
                if (tvPendingChip != null) tvPendingChip.setText("Pending " + fp);
                if (tvDoneChip    != null) tvDoneChip.setText("Done " + fk);
                if (tvDelayedChip != null) {
                    tvDelayedChip.setVisibility(fdl > 0 ? View.VISIBLE : View.GONE);
                    tvDelayedChip.setText("Late " + fdl);
                    if (fdl > 0) {
                        tvDelayedChip.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120)
                                .withEndAction(() -> tvDelayedChip.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(120).start()).start();
                    }
                }
                if (bottomNav != null) {
                    if (fp + fdl > 0) {
                        bottomNav.getOrCreateBadge(R.id.nav_route).setNumber(fp + fdl);
                        bottomNav.getOrCreateBadge(R.id.nav_route).setVisible(true);
                    } else {
                        bottomNav.removeBadge(R.id.nav_route);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    // ── Tell the current fragment new data arrived ──
    private void notifyCurrentFragment() {
        try {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (f instanceof HomeFragment)  ((HomeFragment)  f).refreshDeliveries();
            if (f instanceof RouteFragment) ((RouteFragment) f).refreshDeliveries();
        } catch (Exception ignored) {}
    }

    // ── Load agent name into header ──
    private void loadUserName() {
        if (currentUserId == null) return;
        db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(doc -> {
                    try {
                        if (doc == null || !doc.exists()) return;
                        String name = doc.getString("name");
                        if (name == null || name.isEmpty()) return;
                        runOnUiThread(() -> {
                            if (tvNameHeader != null) tvNameHeader.setText(name);
                            if (tvHeaderInitials != null) {
                                StringBuilder sb = new StringBuilder();
                                for (String part : name.split(" ")) {
                                    if (!part.isEmpty()) {
                                        sb.append(part.charAt(0));
                                        if (sb.length() >= 2) break;
                                    }
                                }
                                tvHeaderInitials.setText(sb.toString().toUpperCase());
                            }
                        });
                    } catch (Exception ignored) {}
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("MainActivity", "loadUserName failed: " + e.getMessage()));
    }

    private void setGreeting() {
        if (tvGreetingHeader == null) return;
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String g = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        tvGreetingHeader.setText(g + " 👋");
    }

    private void goTo(Class<?> cls) {
        startActivity(new Intent(this, cls));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (networkMonitor != null) networkMonitor.start();
        if (deliveryListener == null) startDeliveryListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (networkMonitor != null) networkMonitor.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (deliveryListener != null) {
            deliveryListener.remove();
            deliveryListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
    }
}