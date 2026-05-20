package com.lunchbox.delivery.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * NetworkMonitor — updated with static isOnlineStatic() helper
 * so DeliveryCardAdapter can check network without needing an instance.
 *
 * ADD this method to your existing NetworkMonitor.java:
 *
 *   public static boolean isOnlineStatic(Context context) { ... }
 *
 * Or replace your existing NetworkMonitor.java with this complete version.
 */
public class NetworkMonitor {

    public interface Listener {
        void onNetworkChanged(boolean isOnline);
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager connectivityManager;

    public NetworkMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    mainHandler.post(() -> listener.onNetworkChanged(true));
                }
                @Override public void onLost(Network network) {
                    mainHandler.post(() -> listener.onNetworkChanged(false));
                }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    boolean ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    mainHandler.post(() -> listener.onNetworkChanged(ok));
                }
            };
            NetworkRequest req = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            connectivityManager.registerNetworkCallback(req, networkCallback);
        }
        mainHandler.post(() -> listener.onNetworkChanged(isCurrentlyOnline()));
    }

    public void stop() {
        if (networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); }
            catch (Exception ignored) {}
            networkCallback = null;
        }
    }

    public boolean isCurrentlyOnline() {
        return isOnlineStatic(context);
    }

    // ── Static version — usable from adapters without an instance ──
    public static boolean isOnlineStatic(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } else {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }
}