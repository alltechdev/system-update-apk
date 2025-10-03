package com.dietdroid.updates;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.AsyncTask;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.MessageDigest;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MediaType;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SystemUpdate";
    private static final String VERSION_URL = "https://api.github.com/repos/alltechdev/alltech.dev/contents/system_update.json";
    private static final String DEVICE_PING_URL = "https://httpbin.org/post"; // Test HTTP endpoint for device registration
    
    private TextView statusText;
    private Button checkButton;
    private Button updateButton;
    private ProgressBar progressBar;
    private SharedPreferences prefs;
    
    private String currentVersion;
    private String latestVersion;
    private String scriptUrl;
    private String apkUrl;
    private boolean isForced;
    // Removed automatic update checking variables

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("system_update", MODE_PRIVATE);
        currentVersion = prefs.getString("current_version", "1.0");
        
        initViews();
        setupClickListeners();
        startUpdateService();
        // Removed automatic update checking - all updates now require manual user action
    }
    
    @Override
    public void onBackPressed() {
        // Users can now always exit the app - all updates require manual confirmation
        super.onBackPressed();
    }
    
    // Removed automatic update checking in onResume/onPause
    
    // Removed automatic periodic update checks - updates now require manual user action
    
    private void initViews() {
        statusText = findViewById(R.id.statusText);
        checkButton = findViewById(R.id.checkButton);
        updateButton = findViewById(R.id.updateButton);
        progressBar = findViewById(R.id.progressBar);
        
        statusText.setText("Current version: " + currentVersion + 
                          "\nTap 'Check for Updates' to manually check for updates");
        updateButton.setVisibility(View.GONE);
        
        // Initialize changelog (will be populated when update is found)
        TextView changelogText = findViewById(R.id.changelogText);
        changelogText.setVisibility(View.GONE);
    }
    
    private void setupClickListeners() {
        checkButton.setOnClickListener(v -> checkForUpdate());
        updateButton.setOnClickListener(v -> performUpdate());
    }
    
    private void checkForUpdate() {
        new CheckVersionTask().execute(VERSION_URL);
    }
    
    private void startUpdateService() {
        Intent serviceIntent = new Intent(this, UpdateService.class);
        startService(serviceIntent);
        Log.d(TAG, "UpdateService started from MainActivity");
    }
    
    private void checkRootAccess() {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("su -c echo test");
                int exitCode = process.waitFor();
                
                runOnUiThread(() -> {
                    if (exitCode == 0) {
                        Log.d(TAG, "Root access granted");
                        grantPermissions();
                    } else {
                        Log.d(TAG, "Root access not available, continuing without root privileges");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Root not available, continuing without root privileges");
                });
                Log.e(TAG, "Root check failed", e);
            }
        }).start();
    }
    
    
    private void grantPermissions() {
        new Thread(() -> {
            try {
                String packageName = getPackageName();
                
                // Grant WRITE_EXTERNAL_STORAGE permission
                ProcessBuilder pb1 = new ProcessBuilder("su", "-c", "pm grant " + packageName + " android.permission.WRITE_EXTERNAL_STORAGE");
                Process p1 = pb1.start();
                p1.waitFor();
                
                // Grant INSTALL_PACKAGES permission
                ProcessBuilder pb2 = new ProcessBuilder("su", "-c", "pm grant " + packageName + " android.permission.INSTALL_PACKAGES");
                Process p2 = pb2.start();
                p2.waitFor();
                
                Log.d(TAG, "Permissions granted via root");
            } catch (Exception e) {
                Log.e(TAG, "Failed to grant permissions", e);
            }
        }).start();
    }
    
    private void performUpdate() {
        if (isForced) {
            Toast.makeText(this, "Installing critical security update...", Toast.LENGTH_LONG).show();
        }
        
        if (apkUrl != null && scriptUrl != null) {
            new DownloadAndExecuteTask().execute(scriptUrl, apkUrl);
        } else if (apkUrl != null) {
            new DownloadApkTask().execute(apkUrl);
        } else if (scriptUrl != null) {
            new DownloadAndExecuteTask().execute(scriptUrl);
        }
    }
    
    private class CheckVersionTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            checkButton.setEnabled(false);
            statusText.setText("Checking for updates...");
        }
        
        @Override
        protected String doInBackground(String... urls) {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                    .url(urls[0])
                    .build();
                
                Response response = client.newCall(request).execute();
                return response.body().string();
            } catch (Exception e) {
                Log.e(TAG, "Error checking version", e);
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.GONE);
            checkButton.setEnabled(true);
            
            if (result != null) {
                try {
                    Gson gson = new Gson();
                    JsonObject apiResponse = gson.fromJson(result, JsonObject.class);
                    String content = apiResponse.get("content").getAsString();
                    String decodedContent = new String(android.util.Base64.decode(content, android.util.Base64.DEFAULT));
                    
                    JsonObject jsonObject = gson.fromJson(decodedContent, JsonObject.class);
                    
                    // Support both old and new JSON formats
                    if (jsonObject.has("latest_version")) {
                        // New format
                        latestVersion = jsonObject.get("latest_version").getAsString();
                        JsonObject updates = jsonObject.getAsJsonObject("updates");
                        JsonObject updateInfo = updates.getAsJsonObject(latestVersion);
                        scriptUrl = updateInfo.has("script_url") ? updateInfo.get("script_url").getAsString() : null;
                        apkUrl = updateInfo.has("apk_url") ? updateInfo.get("apk_url").getAsString() : null;
                        isForced = updateInfo.has("forced") ? updateInfo.get("forced").getAsBoolean() : false;
                    } else {
                        // Old format
                        latestVersion = jsonObject.get("version").getAsString();
                        scriptUrl = jsonObject.has("script_url") ? jsonObject.get("script_url").getAsString() : null;
                        apkUrl = jsonObject.has("apk_url") ? jsonObject.get("apk_url").getAsString() : null;
                        isForced = jsonObject.has("forced") ? jsonObject.get("forced").getAsBoolean() : false;
                    }
                    
                    // Find the next sequential version the user should install
                    String nextVersion = getNextSequentialVersion(jsonObject, currentVersion);
                    
                    if (nextVersion != null && !currentVersion.equals(nextVersion)) {
                        // Get the update info for the next version
                        JsonObject currentUpdateInfo = null;
                        if (jsonObject.has("latest_version")) {
                            // New format
                            JsonObject updates = jsonObject.getAsJsonObject("updates");
                            currentUpdateInfo = updates.getAsJsonObject(nextVersion);
                            
                            // Update the current URLs and forced status for the next version
                            if (currentUpdateInfo != null) {
                                scriptUrl = currentUpdateInfo.has("script_url") ? currentUpdateInfo.get("script_url").getAsString() : null;
                                apkUrl = currentUpdateInfo.has("apk_url") ? currentUpdateInfo.get("apk_url").getAsString() : null;
                                isForced = currentUpdateInfo.has("forced") ? currentUpdateInfo.get("forced").getAsBoolean() : false;
                            }
                        } else {
                            // Old format - use the whole jsonObject as update info
                            currentUpdateInfo = jsonObject;
                        }
                        
                        // Display changelog if available
                        displayChangelog(currentUpdateInfo);
                        
                        if (isForced) {
                            statusText.setText("Critical update available: v" + nextVersion + " (Installation required)");
                            updateButton.setVisibility(View.VISIBLE);
                            updateButton.setText("Install Critical Update");
                        } else {
                            statusText.setText("Update available: v" + nextVersion);
                            updateButton.setVisibility(View.VISIBLE);
                            updateButton.setText("Install Update");
                        }
                    } else {
                        statusText.setText("You have the latest version: v" + currentVersion);
                        hideChangelog();
                    }
                } catch (Exception e) {
                    statusText.setText("Error parsing version info");
                    Log.e(TAG, "Error parsing JSON", e);
                }
            } else {
                statusText.setText("Failed to check for updates");
            }
        }
    }
    
    private class DownloadAndExecuteTask extends AsyncTask<String, String, Boolean> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            updateButton.setEnabled(false);
            statusText.setText("Downloading update script...");
        }
        
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                OkHttpClient client = new OkHttpClient();
                
                if (urls.length > 1) {
                    publishProgress("Downloading APK...");
                    
                    // Convert GitHub blob URLs to raw URLs for proper binary download
                    String apkUrl = convertToRawUrl(urls[1]);
                    Log.d(TAG, "APK URL: " + apkUrl);
                    
                    Request apkRequest = new Request.Builder().url(apkUrl).build();
                    Response apkResponse = client.newCall(apkRequest).execute();
                    
                    // First save to app's internal storage
                    File tempApkFile = new File(getFilesDir(), "update.apk");
                    FileOutputStream apkFos = new FileOutputStream(tempApkFile);
                    apkFos.write(apkResponse.body().bytes());
                    apkFos.close();
                    
                    // Copy to /data/local/tmp using su
                    String destPath = "/data/local/tmp/update.apk";
                    ProcessBuilder copyPb = new ProcessBuilder("su", "-c", "cp " + tempApkFile.getAbsolutePath() + " " + destPath);
                    Process copyProcess = copyPb.start();
                    copyProcess.waitFor();
                    
                    // Set permissions using su
                    ProcessBuilder chmodPb = new ProcessBuilder("su", "-c", "chmod 644 " + destPath);
                    Process chmodProcess = chmodPb.start();
                    chmodProcess.waitFor();
                    
                    File apkFile = new File(destPath);
                    
                    publishProgress("Installing APK...");
                    boolean apkInstallSuccess = installApk(apkFile);
                    if (!apkInstallSuccess) {
                        return false;
                    }
                }
                
                if (urls[0] != null) {
                    publishProgress("Downloading script...");
                    
                    Request request = new Request.Builder().url(urls[0]).build();
                    Response response = client.newCall(request).execute();
                    String scriptContent = response.body().string();
                    
                    File scriptFile = new File(getFilesDir(), "update_script.sh");
                    FileOutputStream fos = new FileOutputStream(scriptFile);
                    fos.write(scriptContent.getBytes());
                    fos.close();
                    
                    scriptFile.setExecutable(true);
                    
                    publishProgress("Executing update script...");
                    
                    ProcessBuilder pb = new ProcessBuilder("su", "-c", "sh " + scriptFile.getAbsolutePath());
                    Process process = pb.start();
                    
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.d(TAG, "Script output: " + line);
                        publishProgress("Script: " + line);
                    }
                    
                    int exitCode = process.waitFor();
                    return exitCode == 0;
                }
                
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Error executing update", e);
                return false;
            }
        }
        
        @Override
        protected void onProgressUpdate(String... progress) {
            statusText.setText(progress[0]);
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(View.GONE);
            updateButton.setEnabled(true);
            
            if (success) {
                statusText.setText("Update completed successfully!");
                Toast.makeText(MainActivity.this, "System updated to v" + latestVersion, Toast.LENGTH_LONG).show();
                currentVersion = latestVersion;
                prefs.edit().putString("current_version", latestVersion).apply();
                updateButton.setVisibility(View.GONE);
            } else {
                statusText.setText("Update failed. Check logs for details.");
                Toast.makeText(MainActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private class DownloadApkTask extends AsyncTask<String, String, Boolean> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            updateButton.setEnabled(false);
            statusText.setText("Downloading APK...");
        }
        
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                publishProgress("Downloading APK...");
                
                // Convert GitHub blob URLs to raw URLs for proper binary download
                String apkUrl = convertToRawUrl(urls[0]);
                Log.d(TAG, "APK URL: " + apkUrl);
                
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(apkUrl).build();
                Response response = client.newCall(request).execute();
                
                // First save to app's internal storage
                File tempApkFile = new File(getFilesDir(), "update.apk");
                FileOutputStream fos = new FileOutputStream(tempApkFile);
                fos.write(response.body().bytes());
                fos.close();
                
                // Copy to /data/local/tmp using su
                String destPath = "/data/local/tmp/update.apk";
                ProcessBuilder copyPb = new ProcessBuilder("su", "-c", "cp " + tempApkFile.getAbsolutePath() + " " + destPath);
                Process copyProcess = copyPb.start();
                copyProcess.waitFor();
                
                // Set permissions using su
                ProcessBuilder chmodPb = new ProcessBuilder("su", "-c", "chmod 644 " + destPath);
                Process chmodProcess = chmodPb.start();
                chmodProcess.waitFor();
                
                File apkFile = new File(destPath);
                
                publishProgress("Installing APK...");
                boolean installSuccess = installApk(apkFile);
                
                return installSuccess;
                
            } catch (Exception e) {
                Log.e(TAG, "Error downloading APK", e);
                return false;
            }
        }
        
        @Override
        protected void onProgressUpdate(String... progress) {
            statusText.setText(progress[0]);
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(View.GONE);
            updateButton.setEnabled(true);
            
            if (success) {
                statusText.setText("APK installed successfully!");
                Toast.makeText(MainActivity.this, "APK updated to v" + latestVersion, Toast.LENGTH_LONG).show();
                currentVersion = latestVersion;
                prefs.edit().putString("current_version", latestVersion).apply();
                updateButton.setVisibility(View.GONE);
            } else {
                statusText.setText("APK installation failed.");
                Toast.makeText(MainActivity.this, "APK installation failed", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private boolean installApk(File apkFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String command = "pm install -r " + apkFile.getAbsolutePath();
                ProcessBuilder pb = new ProcessBuilder("su", "-c", command);
                Process process = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    Log.d(TAG, "PM install output: " + line);
                }
                
                StringBuilder errorOutput = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    Log.e(TAG, "PM install error: " + line);
                }
                
                int exitCode = process.waitFor();
                Log.d(TAG, "PM install exit code: " + exitCode);
                
                if (exitCode == 0 && output.toString().contains("Success")) {
                    return true;
                } else {
                    Log.e(TAG, "APK installation failed. Exit code: " + exitCode);
                    Log.e(TAG, "Error output: " + errorOutput.toString());
                    return false;
                }
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri apkUri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    apkUri = Uri.fromFile(apkFile);
                }
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true; // Can't verify success for manual installation
            }
        } catch (Exception e) {
            Log.e(TAG, "Error installing APK", e);
            return false;
        }
    }
    
    private String convertToRawUrl(String url) {
        if (url != null && url.contains("github.com") && url.contains("/blob/")) {
            // Convert GitHub blob URL to raw URL
            String rawUrl = url.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/");
            Log.d(TAG, "Converted blob URL to raw URL: " + url + " -> " + rawUrl);
            return rawUrl;
        }
        return url;
    }
    
    private void displayChangelog(JsonObject updateInfo) {
        TextView changelogText = findViewById(R.id.changelogText);
        ImageView iconView = findViewById(R.id.iconView);
        TextView titleText = findViewById(R.id.titleText);
        
        try {
            if (updateInfo != null && updateInfo.has("changelog")) {
                com.google.gson.JsonArray changelog = updateInfo.getAsJsonArray("changelog");
                if (changelog.size() > 0) {
                    StringBuilder changelogStr = new StringBuilder();
                    for (int i = 0; i < Math.min(3, changelog.size()); i++) { // Show max 3 items
                        if (i > 0) changelogStr.append("\n");
                        changelogStr.append("• ").append(changelog.get(i).getAsString());
                    }
                    changelogText.setText(changelogStr.toString());
                    changelogText.setVisibility(View.VISIBLE);
                    
                    // Hide icon and change title to "Changelog"
                    iconView.setVisibility(View.GONE);
                    titleText.setText("Changelog");
                    
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying changelog", e);
        }
        
        // Hide changelog if not available or error
        changelogText.setVisibility(View.GONE);
        restoreHeaderElements();
    }
    
    private void hideChangelog() {
        TextView changelogText = findViewById(R.id.changelogText);
        changelogText.setVisibility(View.GONE);
        restoreHeaderElements();
    }
    
    private void restoreHeaderElements() {
        ImageView iconView = findViewById(R.id.iconView);
        TextView titleText = findViewById(R.id.titleText);
        
        // Restore icon and original title
        iconView.setVisibility(View.VISIBLE);
        titleText.setText("System Update");
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