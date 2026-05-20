package com.lunchbox.delivery.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.activities.MainActivity;
import com.lunchbox.delivery.adapters.RouteAdapter;
import com.lunchbox.delivery.models.Delivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RouteFragment extends Fragment {

    private RecyclerView recyclerView;
    private RouteAdapter adapter;
    private List<Delivery> routeList = new ArrayList<>();
    private Button btnSaveOrder;
    private TextView tvHeader;
    private boolean orderChanged = false;

    // Static filter so MainActivity can set it before creating fragment
    private static String pendingFilter = "ALL";
    public static void setInitialFilter(String filter) { pendingFilter = filter; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route, container, false);

        recyclerView = view.findViewById(R.id.routeRecycler);
        btnSaveOrder = view.findViewById(R.id.btnSaveOrder);
        tvHeader     = view.findViewById(R.id.tvRouteHeader);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RouteAdapter(getContext(), routeList, () -> {
            orderChanged = true;
            btnSaveOrder.setVisibility(View.VISIBLE);
        });
        recyclerView.setAdapter(adapter);

        // Attach drag-to-reorder
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getAdapterPosition();
                int toPos   = to.getAdapterPosition();
                Collections.swap(routeList, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                orderChanged = true;
                btnSaveOrder.setVisibility(View.VISIBLE);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        };
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recyclerView);
        adapter.setTouchHelper(touchHelper);

        btnSaveOrder.setOnClickListener(v -> savePickupOrder());
        btnSaveOrder.setVisibility(View.GONE);

        refreshDeliveries();

        // Apply any pending filter from status bar click
        if (!"ALL".equals(pendingFilter)) {
            applyFilter(pendingFilter);
            pendingFilter = "ALL"; // reset after applying
        }

        // Filter tabs
        view.findViewById(R.id.tabAll).setOnClickListener(v     -> applyFilter("ALL"));
        view.findViewById(R.id.tabPending).setOnClickListener(v  -> applyFilter("Pending"));
        view.findViewById(R.id.tabDelayed).setOnClickListener(v  -> applyFilter("Delayed"));
        view.findViewById(R.id.tabDone).setOnClickListener(v     -> applyFilter("Done"));

        return view;
    }

    public void refreshDeliveries() {
        if (getActivity() == null) return;
        MainActivity main = MainActivity.getInstance();
        if (main == null) return;

        routeList.clear();
        routeList.addAll(main.allDeliveries);

        if (adapter != null) {
            getActivity().runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                tvHeader.setText(routeList.size() + " deliveries — drag to adjust order");
            });
        }
    }

    private void applyFilter(String filter) {
        if (getActivity() == null) return;
        MainActivity main = MainActivity.getInstance();
        if (main == null) return;

        routeList.clear();
        for (Delivery d : main.allDeliveries) {
            String s = d.getStatus() != null ? d.getStatus() : "";
            boolean include = filter.equals("ALL")
                    || filter.equals(s)
                    || (filter.equals("Done") && ("Picked".equals(s) || "Delivered".equals(s)));
            if (include) routeList.add(d);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void savePickupOrder() {
        if (getContext() == null) return;

        btnSaveOrder.setEnabled(false);
        btnSaveOrder.setText("Saving...");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        for (int i = 0; i < routeList.size(); i++) {
            Delivery d = routeList.get(i);
            batch.update(db.collection("deliveries").document(d.getDeliveryId()),
                    "pickupOrder", i + 1);
        }

        batch.commit()
                .addOnSuccessListener(v -> {
                    if (getActivity() != null) {
                        btnSaveOrder.setEnabled(true);
                        btnSaveOrder.setText("Order Saved ✓");
                        btnSaveOrder.postDelayed(() -> {
                            if (btnSaveOrder != null) btnSaveOrder.setVisibility(View.GONE);
                        }, 2000);
                        orderChanged = false;
                        Toast.makeText(getContext(), "Pickup order saved!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (getActivity() != null) {
                        btnSaveOrder.setEnabled(true);
                        btnSaveOrder.setText("Save Order");
                        Toast.makeText(getContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}