package com.lunchbox.delivery.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.models.Delivery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.ViewHolder> {

    public interface OnOrderChanged { void onChanged(); }

    private final Context ctx;
    private final List<Delivery> list;
    private final OnOrderChanged onOrderChanged;
    private final FirebaseFirestore db;
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
        h.tvName.setText(d.getCustomerName() != null ? d.getCustomerName() : "—");
        h.tvAddress.setText(
                (d.getPickupLocation() != null ? d.getPickupLocation() : "?")
                        + " → "
                        + (d.getDeliveryAddress() != null ? d.getDeliveryAddress() : "?"));
        h.tvStatus.setText(d.getStatus() != null ? d.getStatus() : "Pending");

        // Status colour
        applyStatusStyle(h, d.getStatus());

        // Button visibility
        boolean canAct  = "Pending".equals(d.getStatus()) || "Delayed".equals(d.getStatus());
        boolean picked  = "Picked".equals(d.getStatus());
        boolean isDone  = "Delivered".equals(d.getStatus());

        h.btnPick.setVisibility(canAct  ? View.VISIBLE : View.GONE);
        h.btnDelay.setVisibility(canAct ? View.VISIBLE : View.GONE);
        h.btnDeliver.setVisibility(picked ? View.VISIBLE : View.GONE);

        if (isDone) h.itemView.setAlpha(0.55f);
        else h.itemView.setAlpha(1.0f);

        h.btnPick.setOnClickListener(v -> updateStatus(d, "Picked", h));
        h.btnDelay.setOnClickListener(v -> {
            updateStatus(d, "Delayed", h);
            notifyAdmin(d);
        });
        h.btnDeliver.setOnClickListener(v -> updateStatus(d, "Delivered", h));

        h.btnCall.setOnClickListener(v -> {
            String phone = d.getCustomerPhone();
            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(ctx, "No phone number", Toast.LENGTH_SHORT).show();
                return;
            }
            ctx.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });

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

    private void updateStatus(Delivery d, String status, ViewHolder h) {
        h.btnPick.setEnabled(false); h.btnDelay.setEnabled(false); h.btnDeliver.setEnabled(false);

        db.collection("deliveries").document(d.getDeliveryId())
                .update("status", status)
                .addOnSuccessListener(v -> {
                    Toast.makeText(ctx, "Marked as " + status, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    h.btnPick.setEnabled(true); h.btnDelay.setEnabled(true); h.btnDeliver.setEnabled(true);
                    Toast.makeText(ctx, "Failed — check internet", Toast.LENGTH_SHORT).show();
                });
    }

    private void notifyAdmin(Delivery d) {
        db.collection("users").whereEqualTo("role", "admin").get()
                .addOnSuccessListener(snap -> snap.getDocuments().forEach(doc -> {
                    Map<String, Object> notif = new HashMap<>();
                    notif.put("type", "delay_alert");
                    notif.put("deliveryId", d.getDeliveryId());
                    notif.put("customerName", d.getCustomerName());
                    notif.put("message", "Delivery for " + d.getCustomerName() + " is delayed!");
                    notif.put("targetToken", doc.getString("fcmToken"));
                    notif.put("timestamp", System.currentTimeMillis());
                    notif.put("sent", false);
                    db.collection("notifications").add(notif);
                }));
    }

    @Override public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View vStatusStrip, dragHandle;
        TextView tvOrder, tvName, tvAddress, tvStatus;
        Button btnPick, btnDelay, btnDeliver, btnCall;
        ViewHolder(@NonNull View v) {
            super(v);
            vStatusStrip = v.findViewById(R.id.vStatusStrip);
            dragHandle   = v.findViewById(R.id.dragHandle);
            tvOrder      = v.findViewById(R.id.tvOrderNum);
            tvName       = v.findViewById(R.id.tvCustomerName);
            tvAddress    = v.findViewById(R.id.tvAddress);
            tvStatus     = v.findViewById(R.id.tvStatus);
            btnPick      = v.findViewById(R.id.btnPicked);
            btnDelay     = v.findViewById(R.id.btnDelayed);
            btnDeliver   = v.findViewById(R.id.btnDelivered);
            btnCall      = v.findViewById(R.id.btnCall);
        }
    }
}
