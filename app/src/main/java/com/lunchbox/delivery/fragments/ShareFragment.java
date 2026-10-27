package com.lunchbox.delivery.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.lunchbox.delivery.R;
import com.lunchbox.delivery.activities.MainActivity;
import com.lunchbox.delivery.adapters.ShareDeliveryAdapter;
import com.lunchbox.delivery.models.Delivery;

import java.util.ArrayList;
import java.util.List;

/**
 * ShareFragment — lets an agent hand off a batch of their own deliveries to
 * another agent (e.g. when they're going to be absent). Completely separate
 * from the normal delivery-status flow — it only ever touches assignedTo /
 * assignedName / agentPhone, never status, so it can't interfere with
 * pickup/delivery progress.
 */
public class ShareFragment extends Fragment {

    private RecyclerView recyclerView;
    private ShareDeliveryAdapter adapter;
    private TextView tvSelectedCount;
    private View btnShareSelected;

    private final List<Delivery> shareable = new ArrayList<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_share, container, false);

        recyclerView     = view.findViewById(R.id.shareRecycler);
        tvSelectedCount  = view.findViewById(R.id.tvSelectedCount);
        btnShareSelected = view.findViewById(R.id.btnShareSelected);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ShareDeliveryAdapter(getContext(), shareable, count -> {
            tvSelectedCount.setText(count + " selected");
            btnShareSelected.setEnabled(count > 0);
        });
        recyclerView.setAdapter(adapter);

        btnShareSelected.setOnClickListener(v -> onShareTapped());

        refreshList();
        return view;
    }

    // Only deliveries that still have work left on them make sense to hand off.
    public void refreshList() {
        MainActivity main = MainActivity.getInstance();
        if (main == null || adapter == null) return;
        shareable.clear();
        for (Delivery d : main.allDeliveries) {
            String s = d.getStatus() != null ? d.getStatus() : "Pending";
            if ("Pending".equals(s) || "Delayed".equals(s)) shareable.add(d);
        }
        adapter.clearSelection();
        adapter.notifyDataSetChanged();
    }

    private void onShareTapped() {
        List<Delivery> selected = adapter.getSelectedDeliveries();
        if (selected.isEmpty()) return;

        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        db.collection("users").whereEqualTo("role", "delivery").get()
                .addOnSuccessListener(snap -> {
                    List<String> names = new ArrayList<>();
                    List<String> uids  = new ArrayList<>();
                    List<String> phones = new ArrayList<>();
                    snap.getDocuments().forEach(doc -> {
                        if (doc.getId().equals(myUid)) return; // exclude self
                        names.add(doc.getString("name") != null ? doc.getString("name") : "Agent");
                        uids.add(doc.getId());
                        phones.add(doc.getString("phone"));
                    });
                    if (names.isEmpty()) {
                        Toast.makeText(getContext(), "No other agents found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showAgentPicker(selected, names, uids, phones);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Could not load agents: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showAgentPicker(List<Delivery> selected, List<String> names, List<String> uids, List<String> phones) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Share to which agent?")
                .setItems(names.toArray(new String[0]), (dialog, which) ->
                        confirmShare(selected, names.get(which), uids.get(which), phones.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmShare(List<Delivery> selected, String agentName, String agentUid, String agentPhone) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Confirm share")
                .setMessage("Share " + selected.size() + " delivery" + (selected.size() == 1 ? "" : "ies")
                        + " to " + agentName + "?\n\nThey will immediately see these in their app.")
                .setPositiveButton("Share", (dialog, which) -> doShare(selected, agentName, agentUid, agentPhone))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doShare(List<Delivery> selected, String agentName, String agentUid, String agentPhone) {
        WriteBatch batch = db.batch();
        for (Delivery d : selected) {
            batch.update(db.collection("deliveries").document(d.getDeliveryId()),
                    "assignedTo", agentUid,
                    "assignedName", agentName,
                    "agentPhone", agentPhone != null ? agentPhone : "");
        }
        batch.commit()
                .addOnSuccessListener(v -> {
                    Toast.makeText(getContext(), "Shared " + selected.size() + " deliveries to " + agentName, Toast.LENGTH_LONG).show();
                    refreshList(); // they'll drop out of MainActivity.allDeliveries once the listener updates
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}