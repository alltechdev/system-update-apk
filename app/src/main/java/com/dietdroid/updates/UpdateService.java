package com.dietdroid.updates;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.content.SharedPreferences;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MediaType;

public class UpdateService extends Service {
    private static final String TAG = "UpdateService";
    private static final String CHANNEL_ID = "update_service_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int UPDATE_CHECK_INTERVAL = 25000; // 25 seconds
    private static final String VERSION_URL = "https://api.github.com/repos/alltechdev/alltech.dev/contents/system_update.json";
    
    private Handler updateHandler;
    private Runnable updateRunnable;
    private SharedPreferences prefs;
    
    private String currentVersion;
    private String latestVersion;
    private String scriptUrl;
    private String apkUrl;
    private boolean isForced;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "UpdateService created");
        
        prefs = getSharedPreferences("system_update", MODE_PRIVATE);
        currentVersion = prefs.getString("current_version", "1.0");
        
        // Removed automatic background update checking
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "UpdateService started");
        return START_STICKY; // Restart if killed
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "UpdateService destroyed");
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "System Update Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background service for system updates");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Update Service")
                .setContentText("Monitoring for system updates...")
                .setSmallIcon(R.drawable.ic_system_update)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    // Removed automatic background update checking functionality

    // Removed automatic update checking functionality

    // Removed automatic version response parsing that could trigger updates

    private void updateNotification(String text) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Update Service")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_system_update)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, notification);
    }

    // Removed automatic background update installation functionality

    // Removed automatic script download and execution functionality

    // Removed automatic APK download and installation functionality

    // Removed automatic file download functionality

    private String convertGitHubUrl(String url) {
        if (url.contains("github.com") && url.contains("/blob/")) {
            String rawUrl = url.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/");
            Log.d(TAG, "Converted blob URL to raw URL: " + url + " -> " + rawUrl);
            return rawUrl;
        }
        return url;
    }
    
    private String getNextSequentialVersion(JsonObject jsonObject, String currentVersion) {
        try {
            if (jsonObject.has("latest_version") && jsonObject.has("updates")) {
                JsonObject updates = jsonObject.getAsJsonObject("updates");
                
                // Get all available versions and sort them
                java.util.List<String> versions = new java.util.ArrayList<>();
                for (String key : updates.keySet()) {
                    versions.add(key);
                }
                
                // Sort versions numerically
                versions.sort((a, b) -> {
                    try {
                        double versionA = Double.parseDouble(a);
                        double versionB = Double.parseDouble(b);
                        return Double.compare(versionA, versionB);
                    } catch (NumberFormatException e) {
                        return a.compareTo(b); // Fallback to string comparison
                    }
                });
                
                // Find the next version after current version
                double currentVersionNum = Double.parseDouble(currentVersion);
                for (String version : versions) {
                    try {
                        double versionNum = Double.parseDouble(version);
                        if (versionNum > currentVersionNum) {
                            return version; // Return the first version higher than current
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid version numbers
                    }
                }
            } else {
                // Old format - just return the version field
                return jsonObject.get("version").getAsString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding next sequential version", e);
        }
        
        return null; // No updates available
    }
    
}