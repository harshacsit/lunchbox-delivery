package com.lunchbox.delivery.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.activities.MainActivity;
import com.lunchbox.delivery.adapters.DeliveryCardAdapter;
import com.lunchbox.delivery.models.Delivery;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    // Static pending filter — MainActivity sets this before creating the fragment
    private static String sPendingFilter = null;
    public static void setPendingFilter(String f) { sPendingFilter = f; }

    private RecyclerView recyclerView;
    private DeliveryCardAdapter adapter;
    private LinearLayout emptyState;
    private TextView tvEmptyTitle, tvEmptySubtitle, tvFilterCount;
    private ChipGroup chipGroup;

    private final List<Delivery> displayList = new ArrayList<>();
    private String activeFilter = "ALL";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view;
        try {
            view = inflater.inflate(R.layout.fragment_home, container, false);
        } catch (Exception e) {
            android.util.Log.e("HomeFragment", "inflate failed: " + e.getMessage());
            return new View(getContext()); // blank view — never crash
        }

        recyclerView    = view.findViewById(R.id.recyclerView);
        emptyState      = view.findViewById(R.id.emptyState);
        tvEmptyTitle    = view.findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = view.findViewById(R.id.tvEmptySubtitle);
        tvFilterCount   = view.findViewById(R.id.tvFilterCount);
        chipGroup       = view.findViewById(R.id.chipGroup);

        if (recyclerView != null) {
            adapter = new DeliveryCardAdapter(getContext(), displayList);
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setHasFixedSize(false);
            recyclerView.setAdapter(adapter);
        }

        // Apply any pending filter from status chip
        if (sPendingFilter != null) {
            activeFilter = sPendingFilter;
            sPendingFilter = null;
        }

        // Chip click listeners
        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
                if (ids.isEmpty()) return;
                int id = ids.get(0);
                if      (id == R.id.chipAll)       activeFilter = "ALL";
                else if (id == R.id.chipPending)   activeFilter = "Pending";
                else if (id == R.id.chipPicked)    activeFilter = "Picked";
                else if (id == R.id.chipDelayed)   activeFilter = "Delayed";
                else if (id == R.id.chipDelivered) activeFilter = "Delivered";
                refreshDeliveries();
            });
            syncChip();
        }

        refreshDeliveries();
        return view;
    }

    // Called by MainActivity when Firestore data updates
    public void refreshDeliveries() {
        if (!isAdded() || getActivity() == null) return;
        MainActivity main = MainActivity.getInstance();
        if (main == null) return;

        List<Delivery> all = main.allDeliveries;
        displayList.clear();
        for (Delivery d : all) {
            String s = d.getStatus() != null ? d.getStatus() : "";
            if (activeFilter.equals("ALL") || s.equals(activeFilter)) displayList.add(d);
        }

        getActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            try {
                if (adapter != null) adapter.notifyDataSetChanged();

                boolean empty = displayList.isEmpty();
                if (recyclerView != null) recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (emptyState   != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);

                int p  = count(all, "Pending");
                int dl = count(all, "Delayed");

                if (tvFilterCount != null) {
                    if ("ALL".equals(activeFilter))
                        tvFilterCount.setText(all.size() + " deliveries  •  " + p + " pending  •  " + dl + " delayed");
                    else
                        tvFilterCount.setText(displayList.size() + " " + activeFilter.toLowerCase() + " deliveries");
                }

                if (tvEmptyTitle != null && tvEmptySubtitle != null) {
                    switch (activeFilter) {
                        case "Pending":
                            tvEmptyTitle.setText("No pending deliveries");
                            tvEmptySubtitle.setText("All picked up or in progress!"); break;
                        case "Delayed":
                            tvEmptyTitle.setText("No delays today!");
                            tvEmptySubtitle.setText("Everything is on time 🎉"); break;
                        case "Picked":
                            tvEmptyTitle.setText("None picked up yet");
                            tvEmptySubtitle.setText("Picked orders appear here"); break;
                        case "Delivered":
                            tvEmptyTitle.setText("No completed deliveries yet");
                            tvEmptySubtitle.setText("Delivered orders appear here"); break;
                        default:
                            tvEmptyTitle.setText("No deliveries today");
                            tvEmptySubtitle.setText("Admin assigns routes each morning");
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("HomeFragment", "refreshDeliveries UI error: " + e.getMessage());
            }
        });
    }

    // Called by MainActivity when user taps a status chip while on Home tab
    public void applyFilter(String filter) {
        activeFilter = filter;
        syncChip();
        refreshDeliveries();
    }

    private void syncChip() {
        if (chipGroup == null) return;
        try {
            int id;
            switch (activeFilter) {
                case "Pending":   id = R.id.chipPending;   break;
                case "Picked":    id = R.id.chipPicked;    break;
                case "Delayed":   id = R.id.chipDelayed;   break;
                case "Delivered": id = R.id.chipDelivered; break;
                default:          id = R.id.chipAll;
            }
            Chip chip = chipGroup.findViewById(id);
            if (chip != null) chip.setChecked(true);
        } catch (Exception ignored) {}
    }

    private int count(List<Delivery> list, String status) {
        int n = 0;
        for (Delivery d : list) if (status.equals(d.getStatus())) n++;
        return n;
    }
}