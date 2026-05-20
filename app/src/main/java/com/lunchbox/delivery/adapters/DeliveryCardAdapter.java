package com.lunchbox.delivery.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.models.Delivery;
import com.lunchbox.delivery.utils.NetworkMonitor;
import com.lunchbox.delivery.utils.OfflineQueueManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeliveryCardAdapter — v5
 *
 * CHANGES from previous version:
 *   - After agent taps "Picked Up": shows "Call Admin" button instead of "Delivered"
 *   - adminPhone comes from Delivery.getAdminPhone() (set during auto-assign)
 *   - "Delivered" button removed — delivery is considered complete after pickup
 *   - All null checks preserved for crash safety
 */
public class DeliveryCardAdapter extends RecyclerView.Adapter<DeliveryCardAdapter.VH> {

    // Admin phone — loaded once by MainActivity and passed in
    private String adminPhone = "";

    private final Context ctx;
    private final List<Delivery> list;
    private final FirebaseFirestore db;
    private int lastPosition = -1;

    public DeliveryCardAdapter(Context ctx, List<Delivery> list) {
        this.ctx  = ctx;
        this.list = list;
        this.db   = FirebaseFirestore.getInstance();
    }

    // Called by MainActivity after loading user profile
    public void setAdminPhone(String phone) {
        this.adminPhone = phone != null ? phone : "";
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_delivery_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        try {
            Delivery d = list.get(position);

            safeText(h.tvOrderNum, String.valueOf(position + 1));
            safeText(h.tvName,     safe(d.getCustomerName()));
            safeText(h.tvPhone,    safe(d.getCustomerPhone()));
            safeText(h.tvPickup,   safe(d.getPickupLocation()));
            safeText(h.tvAddress,  safe(d.getDeliveryAddress()));

            // Notes
            if (h.notesRow != null) {
                if (!TextUtils.isEmpty(d.getNotes())) {
                    safeText(h.tvNotes, d.getNotes());
                    h.notesRow.setVisibility(View.VISIBLE);
                } else {
                    h.notesRow.setVisibility(View.GONE);
                }
            }

            // Item count
            if (h.tvItems != null) {
                if (d.getItemCount() > 0) {
                    h.tvItems.setText(d.getItemCount() + (d.getItemCount() == 1 ? " item" : " items"));
                    h.tvItems.setVisibility(View.VISIBLE);
                } else {
                    h.tvItems.setVisibility(View.GONE);
                }
            }

            // Apply status-based styling and button visibility
            applyStatus(h, d.getStatus());

            // Phone tap → call customer
            if (h.phoneRow != null) {
                h.phoneRow.setOnClickListener(v -> callNumber(d.getCustomerPhone()));
            }

            // ── BUTTONS ──

            // Picked Up — available when Pending or Delayed
            if (h.btnPicked != null) {
                h.btnPicked.setOnClickListener(v -> {
                    press(v);
                    doStatus(d, "Picked", h);
                });
            }

            // Delayed — available when Pending or Delayed
            if (h.btnDelayed != null) {
                h.btnDelayed.setOnClickListener(v -> {
                    press(v);
                    doStatus(d, "Delayed", h);
                });
            }

            // Call Customer — always visible
            if (h.btnCallCustomer != null) {
                h.btnCallCustomer.setOnClickListener(v -> {
                    press(v);
                    callNumber(d.getCustomerPhone());
                });
            }

            // Call Admin — shown after status = Picked
            // Uses adminPhone from delivery document or fallback from adapter field
            if (h.btnCallAdmin != null) {
                String adminNum = !TextUtils.isEmpty(d.getAdminPhone())
                        ? d.getAdminPhone() : adminPhone;
                h.btnCallAdmin.setOnClickListener(v -> {
                    press(v);
                    if (!TextUtils.isEmpty(adminNum)) {
                        callNumber(adminNum);
                    } else {
                        Toast.makeText(ctx,
                                "Admin phone not set. Ask admin to add phone number in dashboard.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            // Entrance animation — staggered slide up
            if (position > lastPosition) {
                h.itemView.setTranslationY(50f);
                h.itemView.setAlpha(0f);
                h.itemView.animate()
                        .translationY(0f).alpha(1f)
                        .setDuration(240)
                        .setStartDelay(Math.min(position * 30L, 210L))
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                        .start();
                lastPosition = position;
            }

        } catch (Exception e) {
            android.util.Log.e("DeliveryCardAdapter", "bind error: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // Status → styling + button visibility
    //
    //  Pending  → show [Picked Up] [Delayed]  [Call Customer]
    //  Delayed  → show [Picked Up] [Delayed]  [Call Customer]
    //  Picked   → show [Call Admin]            [Call Customer]   ← KEY CHANGE
    //  Delivered → dim card, no buttons
    // ────────────────────────────────────────────────
    private void applyStatus(VH h, String status) {
        if (status == null) status = "Pending";

        switch (status) {
            case "Picked":
                strip(h, "#2E7D32");
                pill(h, "Picked", "#2E7D32", "#E8F5E9");
                show(h.rowPendingActions, false);  // hide Picked/Delayed buttons
                show(h.btnCallAdmin,      true);   // show Call Admin
                show(h.btnCallCustomer,   true);   // keep Call Customer
                h.itemView.setAlpha(1f);
                break;

            case "Delayed":
                strip(h, "#C62828");
                pill(h, "Delayed", "#C62828", "#FFEBEE");
                show(h.rowPendingActions, true);
                show(h.btnCallAdmin,      false);
                show(h.btnCallCustomer,   true);
                h.itemView.setAlpha(1f);
                break;

            case "Delivered":
                strip(h, "#1565C0");
                pill(h, "Delivered", "#1565C0", "#E3F2FD");
                show(h.rowPendingActions, false);
                show(h.btnCallAdmin,      false);
                show(h.btnCallCustomer,   false);
                h.itemView.setAlpha(0.5f);
                break;

            default: // Pending
                strip(h, "#E65100");
                pill(h, "Pending", "#E65100", "#FFF3E0");
                show(h.rowPendingActions, true);
                show(h.btnCallAdmin,      false);
                show(h.btnCallCustomer,   true);
                h.itemView.setAlpha(1f);
        }
    }

    private void strip(VH h, String hex) {
        if (h.vStatusStrip != null)
            h.vStatusStrip.setBackgroundColor(Color.parseColor(hex));
    }

    private void pill(VH h, String label, String textHex, String bgHex) {
        if (h.tvStatus != null) {
            h.tvStatus.setText(label);
            h.tvStatus.setTextColor(Color.parseColor(textHex));
            h.tvStatus.setBackgroundColor(Color.parseColor(bgHex));
        }
    }

    private void show(View v, boolean visible) {
        if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ── Write status to Firestore or queue offline ──
    private void doStatus(Delivery d, String newStatus, VH h) {
        d.setStatus(newStatus); // optimistic UI
        applyStatus(h, newStatus);

        if (NetworkMonitor.isOnlineStatic(ctx)) {
            db.collection("deliveries").document(d.getDeliveryId())
                    .update("status", newStatus)
                    .addOnSuccessListener(v ->
                            Toast.makeText(ctx, statusMsg(newStatus), Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> {
                        OfflineQueueManager.queue(ctx, d.getDeliveryId(), newStatus);
                        Toast.makeText(ctx, "Saved offline — will sync", Toast.LENGTH_SHORT).show();
                    });
        } else {
            OfflineQueueManager.queue(ctx, d.getDeliveryId(), newStatus);
            Toast.makeText(ctx, newStatus + " saved offline", Toast.LENGTH_LONG).show();
        }

        if ("Delayed".equals(newStatus)) notifyAdmin(d);
    }

    private void notifyAdmin(Delivery d) {
        try {
            db.collection("users").whereEqualTo("role", "admin").get()
                    .addOnSuccessListener(snap -> snap.getDocuments().forEach(doc -> {
                        Map<String, Object> n = new HashMap<>();
                        n.put("type", "delay_alert");
                        n.put("deliveryId", d.getDeliveryId());
                        n.put("customerName", d.getCustomerName());
                        n.put("message", d.getCustomerName() + " delivery is delayed!");
                        n.put("targetToken", doc.getString("fcmToken"));
                        n.put("timestamp", System.currentTimeMillis());
                        n.put("sent", false);
                        db.collection("notifications").add(n);
                    }));
        } catch (Exception ignored) {}
    }

    private void callNumber(String phone) {
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(ctx, "No phone number available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ctx.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        } catch (Exception e) {
            Toast.makeText(ctx, "Cannot open phone dialer", Toast.LENGTH_SHORT).show();
        }
    }

    private String statusMsg(String s) {
        switch (s) {
            case "Picked":  return "Marked as Picked Up ✓";
            case "Delayed": return "Marked Delayed — admin notified";
            default: return s;
        }
    }

    private void press(View v) {
        v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(70)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start())
                .start();
    }

    private void safeText(TextView tv, String text) {
        if (tv != null) tv.setText(text != null ? text : "—");
    }

    private String safe(String s) { return s != null ? s : "—"; }

    @Override
    public int getItemCount() { return list.size(); }

    // ── ViewHolder ──
    static class VH extends RecyclerView.ViewHolder {
        View          vStatusStrip, phoneRow;
        TextView      tvOrderNum, tvName, tvPhone, tvPickup, tvAddress;
        TextView      tvStatus, tvNotes, tvItems;
        LinearLayout  rowPendingActions, notesRow;  // rowPendingActions = Picked+Delayed row
        MaterialButton btnPicked, btnDelayed;
        MaterialButton btnCallCustomer;   // always visible
        MaterialButton btnCallAdmin;      // shown after Picked

        VH(@NonNull View v) {
            super(v);
            try {
                vStatusStrip     = v.findViewById(R.id.vStatusStrip);
                tvOrderNum       = v.findViewById(R.id.tvOrderNum);
                tvName           = v.findViewById(R.id.tvCustomerName);
                tvPhone          = v.findViewById(R.id.tvPhone);
                tvPickup         = v.findViewById(R.id.tvPickup);
                tvAddress        = v.findViewById(R.id.tvAddress);
                tvStatus         = v.findViewById(R.id.tvStatus);
                tvNotes          = v.findViewById(R.id.tvNotes);
                tvItems          = v.findViewById(R.id.tvItems);
                notesRow         = v.findViewById(R.id.notesRow);
                phoneRow         = v.findViewById(R.id.phoneRow);
                rowPendingActions= v.findViewById(R.id.rowActionMain);   // same XML id
                btnPicked        = v.findViewById(R.id.btnPicked);
                btnDelayed       = v.findViewById(R.id.btnDelayed);
                btnCallCustomer  = v.findViewById(R.id.btnCall);         // existing XML id
                btnCallAdmin     = v.findViewById(R.id.btnCallAdmin);    // new button
            } catch (Exception e) {
                android.util.Log.e("VH", "bind error: " + e.getMessage());
            }
        }
    }
}