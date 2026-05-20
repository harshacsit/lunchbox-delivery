package com.lunchbox.delivery;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    private static final String PROJECT_ID = "lunchboxdelivery-12345";
    private static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/" + PROJECT_ID + "/messages:send";

    public static void sendNotification(Context context, String targetToken, String title, String body) {
        new Thread(() -> {
            try {
                // Simplified for now to avoid Google Auth library issues during build
                sendFcmMessage("MOCK_TOKEN", targetToken, title, body);
            } catch (Exception e) {
                Log.e(TAG, "sendNotification error: " + e.getMessage());
            }
        }).start();
    }

    private static void sendFcmMessage(String accessToken, String targetToken, String title, String body) {
        try {
            JSONObject notification = new JSONObject();
            notification.put("title", title);
            notification.put("body",  body);

            JSONObject message = new JSONObject();
            message.put("token",        targetToken);
            message.put("notification", notification);

            JSONObject payload = new JSONObject();
            payload.put("message", message);

            URL url = new URL(FCM_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type",  "application/json; UTF-8");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "FCM response: " + responseCode);
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "sendFcmMessage error: " + e.getMessage());
        }
    }
}