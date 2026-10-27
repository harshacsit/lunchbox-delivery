package com.lunchbox.delivery.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.models.Delivery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RouteAdapter — v2
 *
 * CHANGES:
 *   - Added "No Box" button next to "Delayed" (matches item_delivery_card.xml v3).
 *   - Removed the old standalone "Call Customer" button reference (btnCall no
 *     longer exists in the layout) — calling the customer is done through the
 *     small phone row, same as the Home tab card.
 *   - Status changes now show an UNDO snackbar and delay the actual Firestore
 *     write for a few seconds. Buttons are disabled (not hidden) meanwhile.
 *   - Marking Delayed also writes wasDelayed:true (never cleared), so the admin
 *     dashboard keeps flagging the customer as "was delayed" even after the
 *     status later moves on.
 */
public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.ViewHolder> {

    public interface OnOrderChanged { void onChanged(); }

    private static final long UNDO_WINDOW_MS = 3500;

    private final Context ctx;
    private final List<Delivery> list;
    private final OnOrderChanged onOrderChanged;
    private final FirebaseFirestore db;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> pendingCommits = new HashMap<>();
    private ItemTouchHelper touchHelper;

    public RouteAdapter(Context ctx, List<Delivery> list, OnOrderChanged callback) {
        this.ctx            = ctx;
        this.list           = list;
        this.onOrderChanged = callback;
        this.db             = FirebaseFirestore.getInstance();
    }

    public void setTouchHelper(ItemTouchHelper th) { this.touchHelper = th; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_delivery_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Delivery d = list.get(position);

        h.tvOrder.setText(String.valueOf(position + 1));
        if (h.tvBoxId != null) h.tvBoxId.setText(!TextUtils.isEmpty(d.getBoxId()) ? d.getBoxId() : "—");
        h.tvName.setText(d.getCustomerName() != null ? d.getCustomerName() : "—");
        h.tvAddress.setText(
                (d.getPickupLocation() != null ? d.getPickupLocation() : "?")
                        + " → "
                        + (d.getDeliveryAddress() != null ? d.getDeliveryAddress() : "?"));
        h.tvStatus.setText(d.getStatus() != null ? d.getStatus() : "Pending");

        // Status colour
        applyStatusStyle(h, d.getStatus());
        setButtonsEnabled(h, true);

        String status   = d.getStatus() != null ? d.getStatus() : "Pending";
        boolean canAct   = "Pending".equals(status) || "Delayed".equals(status);
        boolean picked   = "Picked".equals(status);
        boolean resolved = "Delivered".equals(status) || "NoBox".equals(status);

        if (h.rowActionMain != null) h.rowActionMain.setVisibility(canAct ? View.VISIBLE : View.GONE);
        h.btnPick.setVisibility(canAct ? View.VISIBLE : View.GONE);
        h.btnCallAdmin.setVisibility(picked ? View.VISIBLE : View.GONE);

        h.itemView.setAlpha(resolved ? 0.55f : 1.0f);

        h.btnPick.setOnClickListener(v -> doStatusWithUndo(d, "Picked", h));
        h.btnDelay.setOnClickListener(v -> doStatusWithUndo(d, "Delayed", h));
        if (h.btnNoBox != null) {
            h.btnNoBox.setOnClickListener(v -> doStatusWithUndo(d, "NoBox", h));
        }

        // Call Admin
        h.btnCallAdmin.setOnClickListener(v -> {
            String adminPhone = d.getAdminPhone();
            if (TextUtils.isEmpty(adminPhone)) {
                Toast.makeText(ctx, "Admin phone not set", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                ctx.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + adminPhone)));
            } catch (Exception e) {
                Toast.makeText(ctx, "Cannot open dialer", Toast.LENGTH_SHORT).show();
            }
        });

        // Small call icon in the details (phone row) — replaces the old big Call Customer button
        if (h.phoneRow != null) {
            h.phoneRow.setOnClickListener(v -> {
                String phone = d.getCustomerPhone();
                if (TextUtils.isEmpty(phone)) {
                    Toast.makeText(ctx, "No phone number", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    ctx.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                } catch (Exception e) {
                    Toast.makeText(ctx, "Cannot open dialer", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Drag handle activates drag
        h.dragHandle.setVisibility(View.VISIBLE);
        h.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null) {
                touchHelper.startDrag(h);
            }
            return false;
        });
    }

    private void applyStatusStyle(ViewHolder h, String status) {
        if (status == null) status = "Pending";
        int barColor, statusBg, statusText;
        switch (status) {
            case "Picked":
                barColor = Color.parseColor("#2E7D32");
                statusBg = Color.parseColor("#E8F5E9");
                statusText = Color.parseColor("#2E7D32"); break;
            case "Delayed":
                barColor = Color.parseColor("#C62828");
                statusBg = Color.parseColor("#FFEBEE");
                statusText = Color.parseColor("#C62828"); break;
            case "NoBox":
                barColor = Color.parseColor("#6A1B9A");
                statusBg = Color.parseColor("#F3E5F5");
                statusText = Color.parseColor("#6A1B9A"); break;
            case "Delivered":
                barColor = Color.parseColor("#1565C0");
                statusBg = Color.parseColor("#E3F2FD");
                statusText = Color.parseColor("#1565C0"); break;
            default:
                barColor = Color.parseColor("#E65100");
                statusBg = Color.parseColor("#FFF3E0");
                statusText = Color.parseColor("#E65100");
        }
        h.vStatusStrip.setBackgroundColor(barColor);
        h.tvStatus.setBackgroundColor(statusBg);
        h.tvStatus.setTextColor(statusText);
    }

    private void setButtonsEnabled(ViewHolder h, boolean enabled) {
        if (h.btnPick   != null) h.btnPick.setEnabled(enabled);
        if (h.btnDelay  != null) h.btnDelay.setEnabled(enabled);
        if (h.btnNoBox  != null) h.btnNoBox.setEnabled(enabled);
    }

    // ── Tap a status button: show Undo snackbar, delay the real write ──
    private void doStatusWithUndo(Delivery d, String newStatus, ViewHolder h) {
        final String id = d.getDeliveryId();
        if (id == null) return;

        Runnable existing = pendingCommits.remove(id);
        if (existing != null) handler.removeCallbacks(existing);

        setButtonsEnabled(h, false);

        Runnable commit = () -> {
            pendingCommits.remove(id);
            commitStatus(d, newStatus, h);
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

    private void commitStatus(Delivery d, String status, ViewHolder h) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        if ("Delayed".equals(status)) {
            updates.put("wasDelayed", true);
        }

        db.collection("deliveries").document(d.getDeliveryId())
                .update(updates)
                .addOnSuccessListener(v -> Toast.makeText(ctx, "Marked as " + status, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    setButtonsEnabled(h, true);
                    Toast.makeText(ctx, "Failed — check internet", Toast.LENGTH_SHORT).show();
                });

        if ("Delayed".equals(status)) {
            notifyAdmin(d, "delay_alert", "Delivery for " + d.getCustomerName() + " is delayed!");
        } else if ("NoBox".equals(status)) {
            notifyAdmin(d, "nobox_alert", "No box available for " + d.getCustomerName() + "!");
        }
    }

    private void notifyAdmin(Delivery d, String type, String message) {
        db.collection("users").whereEqualTo("role", "admin").get()
                .addOnSuccessListener(snap -> snap.getDocuments().forEach(doc -> {
                    Map<String, Object> notif = new HashMap<>();
                    notif.put("type", type);
                    notif.put("deliveryId", d.getDeliveryId());
                    notif.put("customerName", d.getCustomerName());
                    notif.put("message", message);
                    notif.put("targetToken", doc.getString("fcmToken"));
                    notif.put("timestamp", System.currentTimeMillis());
                    notif.put("sent", false);
                    db.collection("notifications").add(notif);
                }));
    }

    @Override public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View vStatusStrip, dragHandle, phoneRow;
        LinearLayout rowActionMain;
        TextView tvOrder, tvBoxId, tvName, tvAddress, tvStatus;
        MaterialButton btnPick, btnDelay, btnNoBox, btnCallAdmin;
        ViewHolder(@NonNull View v) {
            super(v);
            vStatusStrip  = v.findViewById(R.id.vStatusStrip);
            dragHandle    = v.findViewById(R.id.dragHandle);
            phoneRow      = v.findViewById(R.id.phoneRow);
            rowActionMain = v.findViewById(R.id.rowActionMain);
            tvOrder       = v.findViewById(R.id.tvOrderNum);
            tvBoxId       = v.findViewById(R.id.tvBoxId);
            tvName        = v.findViewById(R.id.tvCustomerName);
            tvAddress     = v.findViewById(R.id.tvAddress);
            tvStatus      = v.findViewById(R.id.tvStatus);
            btnPick       = v.findViewById(R.id.btnPicked);
            btnDelay      = v.findViewById(R.id.btnDelayed);
            btnNoBox      = v.findViewById(R.id.btnNoBox);
            btnCallAdmin  = v.findViewById(R.id.btnCallAdmin);
        }
    }
}