
package com.lunchbox.delivery.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.models.Delivery;
import com.lunchbox.delivery.utils.NetworkMonitor;
import com.lunchbox.delivery.utils.OfflineQueueManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeliveryCardAdapter — v6
 *
 * CHANGES from v5:
 *   - Added a "No Box" button, side by side with "Delayed" — the agent taps this
 *     when there's no box ready at pickup to give the customer.
 *   - "Picked Up" is now a big full-width button (previously shared a row with
 *     Delayed).
 *   - Removed the standalone big "Call Customer" button. Calling the customer is
 *     done via the small phone row already inside the card details.
 *   - Every status button now shows an UNDO snackbar and delays the real write
 *     for a few seconds. Buttons are only DISABLED during that window — never
 *     hidden — so an accidental tap can be reversed and nothing jumps around.
 *   - Marking a delivery Delayed also writes wasDelayed:true, which is never
 *     cleared afterward. The admin dashboard (d.html) already reads this field
 *     to keep tagging the customer as "⚠ Was Delayed" even after the status
 *     later moves to Picked/Delivered, so admin can still spot & call them.
 */
public class DeliveryCardAdapter extends RecyclerView.Adapter<DeliveryCardAdapter.VH> {

    private static final long UNDO_WINDOW_MS = 3500;

    // Admin phone — loaded once by MainActivity and passed in
    private String adminPhone = "";

    private final Context ctx;
    private final List<Delivery> list;
    private final FirebaseFirestore db;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> pendingCommits = new HashMap<>();
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
            safeText(h.tvBoxId,    !TextUtils.isEmpty(d.getBoxId()) ? d.getBoxId() : "—");
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
            setButtonsEnabled(h, true);

            // Phone tap → call customer (small icon + number, only call control now)
            if (h.phoneRow != null) {
                h.phoneRow.setOnClickListener(v -> callNumber(d.getCustomerPhone()));
            }

            // ── BUTTONS ──
            if (h.btnPicked != null) {
                h.btnPicked.setOnClickListener(v -> {
                    press(v);
                    doStatusWithUndo(d, "Picked", h);
                });
            }
            if (h.btnDelayed != null) {
                h.btnDelayed.setOnClickListener(v -> {
                    press(v);
                    doStatusWithUndo(d, "Delayed", h);
                });
            }
            if (h.btnNoBox != null) {
                h.btnNoBox.setOnClickListener(v -> {
                    press(v);
                    doStatusWithUndo(d, "NoBox", h);
                });
            }

            // Call Admin — shown after status = Picked. Calling admin is immediate
            // (it's not a status change, so no undo needed here).
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
    //  Pending  → show [Delayed | No Box]  +  big [Picked Up]
    //  Delayed  → same as Pending (agent can still act)
    //  Picked   → show [Call Admin] only
    //  NoBox    → dim card, no buttons (nothing to pick up today)
    //  Delivered→ dim card, no buttons
    // ────────────────────────────────────────────────
    private void applyStatus(VH h, String status) {
        if (status == null) status = "Pending";

        switch (status) {
            case "Picked":
                strip(h, "#2E7D32");
                pill(h, "Picked", "#2E7D32", "#E8F5E9");
                show(h.rowActionMain, false);
                show(h.btnPicked,     false);
                show(h.btnCallAdmin,  true);
                h.itemView.setAlpha(1f);
                break;

            case "Delayed":
                strip(h, "#C62828");
                pill(h, "Delayed", "#C62828", "#FFEBEE");
                show(h.rowActionMain, true);
                show(h.btnPicked,     true);
                show(h.btnCallAdmin,  false);
                h.itemView.setAlpha(1f);
                break;

            case "NoBox":
                strip(h, "#6A1B9A");
                pill(h, "No Box", "#6A1B9A", "#F3E5F5");
                show(h.rowActionMain, false);
                show(h.btnPicked,     false);
                show(h.btnCallAdmin,  false);
                h.itemView.setAlpha(0.6f);
                break;

            case "Delivered":
                strip(h, "#1565C0");
                pill(h, "Delivered", "#1565C0", "#E3F2FD");
                show(h.rowActionMain, false);
                show(h.btnPicked,     false);
                show(h.btnCallAdmin,  false);
                h.itemView.setAlpha(0.5f);
                break;

            default: // Pending
                strip(h, "#E65100");
                pill(h, "Pending", "#E65100", "#FFF3E0");
                show(h.rowActionMain, true);
                show(h.btnPicked,     true);
                show(h.btnCallAdmin,  false);
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

    private void setButtonsEnabled(VH h, boolean enabled) {
        if (h.btnPicked  != null) h.btnPicked.setEnabled(enabled);
        if (h.btnDelayed != null) h.btnDelayed.setEnabled(enabled);
        if (h.btnNoBox   != null) h.btnNoBox.setEnabled(enabled);
    }

    // ── Tap a status button: show an Undo snackbar, delay the real write ──
    // Buttons are disabled (not hidden) during the window, so a second
    // accidental tap can't queue another action, but nothing disappears.
    private void doStatusWithUndo(Delivery d, String newStatus, VH h) {
        final String id = d.getDeliveryId();
        if (id == null) return;

        // Latest tap on this card wins — cancel any earlier pending action
        Runnable existing = pendingCommits.remove(id);
        if (existing != null) handler.removeCallbacks(existing);

        setButtonsEnabled(h, false);

        Runnable commit = () -> {
            pendingCommits.remove(id);
            commitStatus(d, newStatus);
        };
        pendingCommits.put(id, commit);
        handler.postDelayed(commit, UNDO_WINDOW_MS);

        Snackbar sb = Snackbar.make(h.itemView, pendingMsg(newStatus), Snackbar.LENGTH_LONG);
        sb.setAction("UNDO", v -> {
            Runnable r = pendingCommits.remove(id);
            if (r != null) handler.removeCallbacks(r);
            setButtonsEnabled(h, true);
            Toast.makeText(ctx, "Cancelled", Toast.LENGTH_SHORT).show();
        });
        sb.show();
    }

    private String pendingMsg(String status) {
        switch (status) {
            case "Picked":  return "Marking as Picked Up…";
            case "Delayed": return "Marking as Delayed…";
            case "NoBox":   return "Marking as No Box…";
            default:        return "Updating…";
        }
    }

    // ── Actually write the status (runs after the undo window passes) ──
    private void commitStatus(Delivery d, String newStatus) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        if ("Delayed".equals(newStatus)) {
            // Never cleared — lets the admin dashboard keep flagging this
            // customer as "was delayed" even after status later changes.
            updates.put("wasDelayed", true);
        }

        if (NetworkMonitor.isOnlineStatic(ctx)) {
            db.collection("deliveries").document(d.getDeliveryId())
                    .update(updates)
                    .addOnSuccessListener(v -> Toast.makeText(ctx, statusMsg(newStatus), Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> {
                        OfflineQueueManager.queue(ctx, d.getDeliveryId(), newStatus);
                        Toast.makeText(ctx, "Saved offline — will sync", Toast.LENGTH_SHORT).show();
                    });
        } else {
            OfflineQueueManager.queue(ctx, d.getDeliveryId(), newStatus);
            Toast.makeText(ctx, newStatus + " saved offline", Toast.LENGTH_LONG).show();
        }

        if ("Delayed".equals(newStatus)) {
            notifyAdmin(d, "delay_alert", d.getCustomerName() + " delivery is delayed!");
        } else if ("NoBox".equals(newStatus)) {
            notifyAdmin(d, "nobox_alert", "No box available for " + d.getCustomerName() + "!");
        }
    }

    private void notifyAdmin(Delivery d, String type, String message) {
        try {
            db.collection("users").whereEqualTo("role", "admin").get()
                    .addOnSuccessListener(snap -> snap.getDocuments().forEach(doc -> {
                        Map<String, Object> n = new HashMap<>();
                        n.put("type", type);
                        n.put("deliveryId", d.getDeliveryId());
                        n.put("customerName", d.getCustomerName());
                        n.put("message", message);
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
            case "NoBox":   return "Marked No Box — admin notified";
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
        TextView      tvOrderNum, tvBoxId, tvName, tvPhone, tvPickup, tvAddress;
        TextView      tvStatus, tvNotes, tvItems;
        LinearLayout  rowActionMain, notesRow;   // rowActionMain = Delayed + No Box row
        MaterialButton btnPicked, btnDelayed, btnNoBox, btnCallAdmin;

        VH(@NonNull View v) {
            super(v);
            try {
                vStatusStrip  = v.findViewById(R.id.vStatusStrip);
                tvOrderNum    = v.findViewById(R.id.tvOrderNum);
                tvBoxId       = v.findViewById(R.id.tvBoxId);
                tvName        = v.findViewById(R.id.tvCustomerName);
                tvPhone       = v.findViewById(R.id.tvPhone);
                tvPickup      = v.findViewById(R.id.tvPickup);
                tvAddress     = v.findViewById(R.id.tvAddress);
                tvStatus      = v.findViewById(R.id.tvStatus);
                tvNotes       = v.findViewById(R.id.tvNotes);
                tvItems       = v.findViewById(R.id.tvItems);
                notesRow      = v.findViewById(R.id.notesRow);
                phoneRow      = v.findViewById(R.id.phoneRow);
                rowActionMain = v.findViewById(R.id.rowActionMain);
                btnPicked     = v.findViewById(R.id.btnPicked);
                btnDelayed    = v.findViewById(R.id.btnDelayed);
                btnNoBox      = v.findViewById(R.id.btnNoBox);
                btnCallAdmin  = v.findViewById(R.id.btnCallAdmin);
            } catch (Exception e) {
                android.util.Log.e("VH", "bind error: " + e.getMessage());
            }
        }
    }
}