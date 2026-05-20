package com.lunchbox.delivery.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.activities.MainActivity;
import com.lunchbox.delivery.models.Delivery;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ProfileFragment — CRASH FIX
 *
 * WHY IT WAS CRASHING:
 *   The old ProfileFragment called view.findViewById() for IDs that did
 *   NOT exist in the layout XML (tvProfileInitials, tvZone, tvVehicle etc.).
 *   When Android can't find those IDs it returns null.
 *   Then calling null.setText() immediately crashes with NullPointerException.
 *
 * FIX:
 *   1. Created fragment_profile.xml that has ALL required IDs defined.
 *   2. Every TextView.setText() call is wrapped in a null check.
 *   3. Safe helper method setTextSafe() used everywhere.
 */
public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate the layout — make sure fragment_profile.xml exists in res/layout/
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Get current user UID safely
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return view;
        String uid = auth.getCurrentUser().getUid();

        // Load profile data from Firestore
        loadProfileFromFirestore(view, uid, auth);

        // Show today's delivery counts from MainActivity's shared list
        showTodayCounts(view);

        return view;
    }

    // ── Load agent profile from Firestore ──
    private void loadProfileFromFirestore(View view, String uid, FirebaseAuth auth) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    // Always check fragment is still attached before touching UI
                    if (!isAdded() || getActivity() == null) return;
                    if (!doc.exists()) return;

                    // Read all fields with safe defaults
                    String name    = safe(doc.getString("name"),    "Delivery Agent");
                    String email   = safe(doc.getString("email"),   "");
                    String phone   = safe(doc.getString("phone"),   "—");
                    String zone    = safe(doc.getString("zone"),    "—");
                    String vehicle = safe(doc.getString("vehicle"), "—");

                    double rating = 5.0;
                    if (doc.getDouble("rating") != null) rating = doc.getDouble("rating");

                    long total = 0, done = 0;
                    if (doc.getLong("totalDeliveries")     != null) total = doc.getLong("totalDeliveries");
                    if (doc.getLong("completedDeliveries") != null) done  = doc.getLong("completedDeliveries");

                    // Calculate success rate
                    int rate = total > 0 ? (int) Math.round((done * 100.0) / total) : 100;

                    // Member since — from Firebase Auth metadata
                    String memberSince = "—";
                    try {
                        if (auth.getCurrentUser() != null && auth.getCurrentUser().getMetadata() != null) {
                            long creationMs = auth.getCurrentUser().getMetadata().getCreationTimestamp();
                            memberSince = new SimpleDateFormat("MMM yyyy", Locale.getDefault())
                                    .format(new Date(creationMs));
                        }
                    } catch (Exception ignored) {}

                    // Build initials from name
                    String initials = buildInitials(name);

                    // Final copies for lambda
                    final String fInitials = initials, fName = name, fEmail = email, fPhone = phone;
                    final String fZone = zone, fVehicle = vehicle, fMember = memberSince;
                    final long   fTotal = total, fDone = done;
                    final int    fRate = rate;
                    final double fRating = rating;

                    // Update all UI on main thread
                    getActivity().runOnUiThread(() -> {
                        // Guard again — activity might have been destroyed
                        if (!isAdded() || getActivity() == null) return;

                        setTextSafe(view, R.id.tvProfileInitials, fInitials);
                        setTextSafe(view, R.id.tvProfileName,     fName);
                        setTextSafe(view, R.id.tvProfileEmail,    fEmail);
                        setTextSafe(view, R.id.tvProfilePhone,    "📞 " + fPhone);
                        setTextSafe(view, R.id.tvTotalDeliveries, String.valueOf(fTotal));
                        setTextSafe(view, R.id.tvDoneDeliveries,  String.valueOf(fDone));
                        setTextSafe(view, R.id.tvSuccessRate,     fRate + "%");
                        setTextSafe(view, R.id.tvMemberSince,     fMember);
                        setTextSafe(view, R.id.tvZone,            fZone);
                        setTextSafe(view, R.id.tvVehicle,         fVehicle);
                        setTextSafe(view, R.id.tvRating,
                                String.format(Locale.getDefault(), "⭐ %.1f / 5.0", fRating));
                    });
                })
                .addOnFailureListener(e -> {
                    // Silently ignore — profile just won't show. App won't crash.
                    android.util.Log.e("ProfileFragment", "Failed to load profile: " + e.getMessage());
                });
    }

    // ── Show today's live counts ──
    private void showTodayCounts(View view) {
        try {
            MainActivity main = MainActivity.getInstance();
            if (main == null) return;

            List<Delivery> all = main.allDeliveries;
            int done = 0;
            for (Delivery d : all) {
                String s = d.getStatus() != null ? d.getStatus() : "";
                if ("Picked".equals(s) || "Delivered".equals(s)) done++;
            }
            setTextSafe(view, R.id.tvTodayDone,
                    "Today: " + done + " of " + all.size() + " done");
        } catch (Exception e) {
            // Never crash the profile screen over a count issue
            android.util.Log.e("ProfileFragment", "Count error: " + e.getMessage());
        }
    }

    // ── Safe helper: set text only if view is found ──
    private void setTextSafe(View root, int viewId, String text) {
        try {
            TextView tv = root.findViewById(viewId);
            if (tv != null) tv.setText(text != null ? text : "—");
        } catch (Exception e) {
            android.util.Log.w("ProfileFragment", "setTextSafe failed for id " + viewId);
        }
    }

    // ── Build 2-letter initials from name ──
    private String buildInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        StringBuilder sb = new StringBuilder();
        for (String part : name.split(" ")) {
            if (!part.isEmpty()) {
                sb.append(part.charAt(0));
                if (sb.length() >= 2) break;
            }
        }
        return sb.toString().toUpperCase();
    }

    // ── Return value or default if null ──
    private String safe(String value, String defaultValue) {
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}