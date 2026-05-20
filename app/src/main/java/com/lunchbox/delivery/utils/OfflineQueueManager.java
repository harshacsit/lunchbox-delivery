package com.lunchbox.delivery.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * OfflineQueueManager
 * ───────────────────
 * When the device has no internet:
 *   - Saves pending status updates (Picked / Delayed) to SharedPreferences
 *   - Updates the local UI immediately so the agent sees the change
 *
 * When internet comes back:
 *   - Reads all queued actions
 *   - Writes them to Firestore one by one
 *   - Clears the queue on success
 *
 * HOW IT WORKS:
 *   Firestore already has offline persistence (setPersistenceEnabled(true) in SplashActivity).
 *   This class is an extra safety net for cases where the Firestore SDK's own queue
 *   doesn't flush fast enough or the app restarts mid-offline.
 *
 * USAGE:
 *   // In DeliveryAdapter when button is tapped:
 *   if (networkMonitor.isCurrentlyOnline()) {
 *       updateFirestoreDirectly(deliveryId, status);
 *   } else {
 *       OfflineQueueManager.queue(context, deliveryId, status);
 *       updateLocalUIOptimistically(status);  // show change immediately
 *   }
 *
 *   // In DeliveryListActivity when network comes back:
 *   networkMonitor = new NetworkMonitor(this, isOnline -> {
 *       if (isOnline) OfflineQueueManager.flush(context);
 *   });
 */
public class OfflineQueueManager {

    private static final String TAG = "OfflineQueue";
    private static final String PREFS_NAME = "lunchbox_offline_queue";
    private static final String KEY_QUEUE   = "pending_actions";

    // ── Queue a status update for later ──
    public static void queue(Context context, String deliveryId, String newStatus) {
        try {
            List<JSONObject> existing = loadQueue(context);
            JSONObject action = new JSONObject();
            action.put("deliveryId", deliveryId);
            action.put("status", newStatus);
            action.put("timestamp", System.currentTimeMillis());
            existing.add(action);
            saveQueue(context, existing);
            Log.d(TAG, "Queued offline action: " + deliveryId + " → " + newStatus);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to queue action", e);
        }
    }

    // ── Check if there are queued actions ──
    public static boolean hasPending(Context context) {
        return !loadQueue(context).isEmpty();
    }

    // ── Count of pending actions ──
    public static int pendingCount(Context context) {
        return loadQueue(context).size();
    }

    // ── Flush all queued actions to Firestore ──
    public static void flush(Context context) {
        List<JSONObject> queue = loadQueue(context);
        if (queue.isEmpty()) return;

        Log.d(TAG, "Flushing " + queue.size() + " offline actions to Firestore...");
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Copy the list so we can remove items as they succeed
        List<JSONObject> toProcess = new ArrayList<>(queue);

        for (JSONObject action : toProcess) {
            try {
                String deliveryId = action.getString("deliveryId");
                String status = action.getString("status");

                db.collection("deliveries")
                        .document(deliveryId)
                        .update("status", status)
                        .addOnSuccessListener(v -> {
                            Log.d(TAG, "Flushed: " + deliveryId + " → " + status);
                            removeFromQueue(context, deliveryId);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Flush failed for " + deliveryId + ": " + e.getMessage());
                            // Leave in queue — will retry next time network comes back
                        });

            } catch (JSONException e) {
                Log.e(TAG, "Malformed queue item", e);
            }
        }
    }

    // ── Internal: remove a specific deliveryId from the queue ──
    private static void removeFromQueue(Context context, String deliveryId) {
        List<JSONObject> queue = loadQueue(context);
        queue.removeIf(item -> {
            try {
                return deliveryId.equals(item.getString("deliveryId"));
            } catch (JSONException e) {
                return false;
            }
        });
        saveQueue(context, queue);
    }

    // ── Internal: load queue from SharedPreferences ──
    private static List<JSONObject> loadQueue(Context context) {
        List<JSONObject> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_QUEUE, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }
        } catch (JSONException ignored) {}
        return list;
    }

    // ── Internal: persist queue to SharedPreferences ──
    private static void saveQueue(Context context, List<JSONObject> list) {
        JSONArray arr = new JSONArray();
        for (JSONObject item : list) arr.put(item);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_QUEUE, arr.toString())
                .apply();
    }

    // ── Clear entire queue (use only after full successful sync) ──
    public static void clearAll(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_QUEUE)
                .apply();
        Log.d(TAG, "Queue cleared");
    }
}