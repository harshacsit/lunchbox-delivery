package com.lunchbox.delivery.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lunchbox.delivery.R;
import com.lunchbox.delivery.models.Delivery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShareDeliveryAdapter extends RecyclerView.Adapter<ShareDeliveryAdapter.VH> {

    public interface OnSelectionChanged { void onChanged(int count); }

    private final Context ctx;
    private final List<Delivery> list;
    private final Set<String> selected = new HashSet<>();
    private final OnSelectionChanged callback;

    public ShareDeliveryAdapter(Context ctx, List<Delivery> list, OnSelectionChanged cb) {
        this.ctx = ctx;
        this.list = list;
        this.callback = cb;
    }

    public List<Delivery> getSelectedDeliveries() {
        List<Delivery> out = new java.util.ArrayList<>();
        for (Delivery d : list) if (selected.contains(d.getDeliveryId())) out.add(d);
        return out;
    }

    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
        if (callback != null) callback.onChanged(0);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_share_delivery, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Delivery d = list.get(position);
        h.tvBoxId.setText(d.getBoxId() != null ? d.getBoxId() : "—");
        h.tvName.setText(d.getCustomerName() != null ? d.getCustomerName() : "—");
        h.tvStatus.setText(d.getStatus() != null ? d.getStatus() : "Pending");

        // Avoid firing listener from recycled state
        h.cbSelect.setOnCheckedChangeListener(null);
        h.cbSelect.setChecked(selected.contains(d.getDeliveryId()));
        h.itemView.setOnClickListener(v -> h.cbSelect.toggle());
        h.cbSelect.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) selected.add(d.getDeliveryId());
            else selected.remove(d.getDeliveryId());
            if (callback != null) callback.onChanged(selected.size());
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        TextView tvBoxId, tvName, tvStatus;
        VH(@NonNull View v) {
            super(v);
            cbSelect = v.findViewById(R.id.cbSelect);
            tvBoxId  = v.findViewById(R.id.tvBoxId);
            tvName   = v.findViewById(R.id.tvName);
            tvStatus = v.findViewById(R.id.tvStatus);
        }
    }
}