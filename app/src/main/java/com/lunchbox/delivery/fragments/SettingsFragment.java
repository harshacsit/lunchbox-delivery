
package com.lunchbox.delivery.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.lunchbox.delivery.BuildConfig;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.activities.LoginActivity;
import com.lunchbox.delivery.activities.MpinActivity;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        // ── Notification toggles ──
        Switch switchNewDelivery  = view.findViewById(R.id.switchNewDelivery);
        Switch switchDelayReminder = view.findViewById(R.id.switchDelayReminder);

        if (uid != null && switchNewDelivery != null && switchDelayReminder != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists() && isAdded() && getActivity() != null) {
                            Boolean notifNew   = doc.getBoolean("notifNewDelivery");
                            Boolean notifDelay = doc.getBoolean("notifDelay");
                            switchNewDelivery.setChecked(notifNew != null ? notifNew : true);
                            switchDelayReminder.setChecked(notifDelay != null ? notifDelay : false);
                        }
                    });

            final String finalUid = uid;
            switchNewDelivery.setOnCheckedChangeListener((btn, checked) ->
                    FirebaseFirestore.getInstance().collection("users").document(finalUid)
                            .update("notifNewDelivery", checked));

            switchDelayReminder.setOnCheckedChangeListener((btn, checked) ->
                    FirebaseFirestore.getInstance().collection("users").document(finalUid)
                            .update("notifDelay", checked));
        }

        // ── Change password ──
        View itemChangePassword = view.findViewById(R.id.itemChangePassword);
        if (itemChangePassword != null) {
            itemChangePassword.setOnClickListener(v -> {
                String email = FirebaseAuth.getInstance().getCurrentUser() != null
                        ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;
                if (email != null) {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(getContext(),
                                            "Password reset email sent to " + email, Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(),
                                            "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        }

        // ── Update phone number ──
        View itemUpdatePhone = view.findViewById(R.id.itemUpdatePhone);
        if (itemUpdatePhone != null) {
            itemUpdatePhone.setOnClickListener(v ->
                    Toast.makeText(getContext(),
                            "Contact your admin to update your phone number", Toast.LENGTH_SHORT).show());
        }

        // ── Update vehicle ──
        View itemUpdateVehicle = view.findViewById(R.id.itemUpdateVehicle);
        if (itemUpdateVehicle != null && uid != null) {
            itemUpdateVehicle.setOnClickListener(v -> showUpdateVehicleDialog(uid));
        }

        // ── Reset MPIN — NEW ──
        View itemResetMpin = view.findViewById(R.id.itemResetMpin);
        if (itemResetMpin != null) {
            itemResetMpin.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Reset MPIN")
                        .setMessage("This will clear your current PIN. You will be asked to set a new one next time you open the app.")
                        .setPositiveButton("Reset", (dialog, which) -> {
                            MpinActivity.clearMpin(requireContext());
                            Toast.makeText(getContext(),
                                    "MPIN cleared. Set a new one next time you open the app.",
                                    Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        // ── App version ──
        TextView tvVersion = view.findViewById(R.id.tvAppVersion);
        if (tvVersion != null) {
            try {
                tvVersion.setText("Version " + BuildConfig.VERSION_NAME);
            } catch (Exception e) {
                tvVersion.setText("Lunchbox Delivery");
            }
        }

        // ── Sign out ──
        View btnSignOut = view.findViewById(R.id.btnSignOut);
        if (btnSignOut != null) {
            btnSignOut.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Sign Out")
                        .setMessage("Are you sure you want to sign out?")
                        .setPositiveButton("Sign Out", (dialog, which) -> {
                            FirebaseAuth.getInstance().signOut();
                            Intent intent = new Intent(getActivity(), LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        return view;
    }

    private void showUpdateVehicleDialog(String uid) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("e.g. Bike — AP29AB1234");
        input.setPadding(40, 20, 40, 20);
        new AlertDialog.Builder(requireContext())
                .setTitle("Update Vehicle Info")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String vehicle = input.getText().toString().trim();
                    if (!vehicle.isEmpty()) {
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                                .update("vehicle", vehicle)
                                .addOnSuccessListener(v ->
                                        Toast.makeText(getContext(), "Vehicle updated!", Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}