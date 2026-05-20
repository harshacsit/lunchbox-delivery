package com.lunchbox.delivery.utils;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * RouteResetManager
 * ─────────────────
 * Handles the daily archive-and-clear operation.
 *
 * What it does:
 *   1. Reads all documents from the "deliveries" collection
 *   2. Copies each one into "history/{YYYY-MM-DD}/deliveries/{docId}"
 *   3. Writes a summary document to "history/{YYYY-MM-DD}/summary"
 *   4. Deletes all documents from the "deliveries" collection
 *
 * This is called from the admin dashboard (JavaScript) for scheduled resets,
 * and can also be triggered manually from the Android app by an admin.
 *
 * IMPORTANT: Only admins should be able to call this. The Firestore security
 * rules restrict delete access to admin role.
 *
 * Usage (from an Activity or Fragment):
 *   RouteResetManager.archiveAndReset(context, new RouteResetManager.ResetCallback() {
 *       public void onProgress(int archived, int total) {
 *           // update a progress bar
 *       }
 *       public void onComplete(String archiveDate, int totalArchived) {
 *           // show success message
 *       }
 *       public void onError(String errorMessage) {
 *           // show error
 *       }
 *   });
 */
public class RouteResetManager {

    private static final String TAG = "RouteReset";

    public interface ResetCallback {
        void onProgress(int archived, int total);
        void onComplete(String archiveDate, int totalArchived);
        void onError(String errorMessage);
    }

    /**
     * Archive all current deliveries to history, then delete from active collection.
     * @param context Android context (for date formatting locale)
     * @param callback progress and completion callbacks
     */
    public static void archiveAndReset(Context context, ResetCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String todayDate = getTodayDate();

        Log.d(TAG, "Starting archive for date: " + todayDate);

        // Step 1: Read all deliveries
        db.collection("deliveries").get()
                .addOnSuccessListener(querySnapshot -> {
                    int total = querySnapshot.size();

                    if (total == 0) {
                        // Nothing to archive
                        callback.onComplete(todayDate, 0);
                        return;
                    }

                    Log.d(TAG, "Archiving " + total + " deliveries...");

                    // Firestore batch can handle max 500 operations.
                    // We use multiple batches for large datasets.
                    int[] archived = {0};
                    int batchSize = 400; // safe below Firestore 500 limit
                    int totalDocs = querySnapshot.getDocuments().size();
                    int batchCount = (int) Math.ceil((double) totalDocs / batchSize);

                    // Count completed and delivered for summary
                    long[] delivered = {0};
                    long[] delayed = {0};
                    long[] pending = {0};

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String status = doc.getString("status");
                        if ("Delivered".equals(status) || "Picked".equals(status)) delivered[0]++;
                        else if ("Delayed".equals(status)) delayed[0]++;
                        else pending[0]++;
                    }

                    // Archive in batches
                    final int[] batchIndex = {0};
                    final WriteBatch[] currentBatch = {db.batch()};
                    final int[] opsInBatch = {0};

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        // Archive path: history/2025-04-03/deliveries/{docId}
                        String archivePath = "history/" + todayDate + "/deliveries/" + doc.getId();

                        // Copy delivery data + add archiveDate field
                        Map<String, Object> data = new HashMap<>(doc.getData());
                        data.put("archiveDate", todayDate);
                        data.put("archivedAt", System.currentTimeMillis());

                        currentBatch[0].set(db.document(archivePath), data);
                        currentBatch[0].delete(doc.getReference()); // delete from active
                        opsInBatch[0] += 2; // set + delete = 2 ops

                        archived[0]++;
                        callback.onProgress(archived[0], total);

                        // Commit when batch is full
                        if (opsInBatch[0] >= batchSize * 2) {
                            commitBatch(currentBatch[0]);
                            currentBatch[0] = db.batch();
                            opsInBatch[0] = 0;
                            batchIndex[0]++;
                        }
                    }

                    // Write summary document for this day's history
                    Map<String, Object> summary = new HashMap<>();
                    summary.put("date", todayDate);
                    summary.put("totalDeliveries", total);
                    summary.put("delivered", delivered[0]);
                    summary.put("delayed", delayed[0]);
                    summary.put("pending", pending[0]);
                    summary.put("completionRate",
                            total > 0 ? Math.round((delivered[0] * 100.0) / total) : 0);
                    summary.put("archivedAt", System.currentTimeMillis());

                    currentBatch[0].set(db.document("history/" + todayDate + "/summary/stats"), summary);

                    // Commit final batch
                    currentBatch[0].commit()
                            .addOnSuccessListener(v -> {
                                Log.d(TAG, "Archive complete: " + total + " deliveries → history/" + todayDate);
                                callback.onComplete(todayDate, total);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Final batch commit failed", e);
                                callback.onError("Archive partially completed. Error: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to read deliveries for archive", e);
                    callback.onError("Could not read deliveries: " + e.getMessage());
                });
    }

    /**
     * Get archive history for a specific date (for the History panel).
     */
    public static void getHistoryForDate(String date, HistoryCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("history").document(date).collection("deliveries").get()
                .addOnSuccessListener(snapshot -> callback.onLoaded(snapshot))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Get all archive dates (to show a list of past days).
     */
    public static void getArchiveDates(ArchiveDatesCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // history collection documents are named by date
        db.collection("history")
                .orderBy("__name__")
                .get()
                .addOnSuccessListener(snapshot -> {
                    java.util.List<String> dates = new java.util.ArrayList<>();
                    snapshot.forEach(doc -> dates.add(doc.getId()));
                    callback.onLoaded(dates);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    private static void commitBatch(WriteBatch batch) {
        batch.commit().addOnFailureListener(e ->
                Log.e(TAG, "Batch commit failed (intermediate)", e));
    }

    public static String getTodayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    public static String getYesterdayDate() {
        long yesterday = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(yesterday));
    }

    public interface HistoryCallback {
        void onLoaded(com.google.firebase.firestore.QuerySnapshot snapshot);
        void onError(String error);
    }

    public interface ArchiveDatesCallback {
        void onLoaded(java.util.List<String> dates);
        void onError(String error);
    }
}