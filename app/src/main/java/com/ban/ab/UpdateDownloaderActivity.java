package com.ban.ab; // قم بتغيير هذا المسار حسب حزمة تطبيقك

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateDownloaderActivity extends AppCompatActivity {

    private TextView tvStatus;
    // استبدل هذا باسم حزمة التطبيق الهدف الخاص بكم
    private static final String TARGET_APP_PACKAGE = "com.target.app2"; 
    // يجب أن يتطابق مع الصلاحية والحدث الذي تمت برمجته في التطبيق الهدف
    private static final String UPDATE_ACTION = "com.abdullah.action.INSTALL_UPDATE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_downloader);

        tvStatus = findViewById(R.id.tvStatus);

        // استقبال رابط ZIP من فايربيس (يتم تمريره عبر Intent عند استدعاء هذا الكلاس)
        String zipUrl = getIntent().getStringExtra("ZIP_URL");

        if (zipUrl != null && !zipUrl.isEmpty()) {
            downloadAndDispatchUpdate(zipUrl);
        } else {
            Toast.makeText(this, "لم يتم العثور على رابط التحديث", Toast.LENGTH_SHORT).show();
            returnToSecondActivity();
        }
    }

    private void downloadAndDispatchUpdate(String fileUrl) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            boolean success = false;
            File updateFile = new File(getCacheDir(), "update.zip");

            try {
                // 1. الاتصال بالسيرفر وتحميل الملف
                URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(updateFile);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    outputStream.close();
                    inputStream.close();
                    success = true;
                }
                connection.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
            }

            // 2. تحديث الواجهة وتنفيذ الإرسال بعد اكتمال التحميل
            final boolean finalSuccess = success;
            handler.post(() -> {
                if (finalSuccess) {
                    tvStatus.setText("تم التحميل.. جاري نقل التحديث للتطبيق الهدف");
                    sendUpdateToTargetApp(updateFile);
                } else {
                    Toast.makeText(UpdateDownloaderActivity.this, "فشل تحميل التحديث", Toast.LENGTH_SHORT).show();
                    returnToSecondActivity();
                }
            });
        });
    }

    private void sendUpdateToTargetApp(File zipFile) {
        try {
            // تجهيز مسار FileProvider الآمن
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    "com.abdullah.assistant.fileprovider", // يجب أن يتطابق مع المانيفست
                    zipFile
            );

            // إرسال الإشعار الصامت للتطبيق الهدف
            Intent intent = new Intent(UPDATE_ACTION);
            intent.setPackage(TARGET_APP_PACKAGE); 
            intent.putExtra("UPDATE_FILE_URI", fileUri);
            
            // منح التطبيق الهدف تصريح قراءة الملف
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // الإرسال
            sendBroadcast(intent);

            Toast.makeText(this, "تم توجيه التحديث بنجاح", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "حدث خطأ أثناء توجيه التحديث", Toast.LENGTH_SHORT).show();
        } finally {
            // في كل الحالات (نجاح أو فشل التوجيه) يتم إنهاء العملية والعودة
            returnToSecondActivity();
        }
    }

    private void returnToSecondActivity() {
        Intent intent = new Intent(UpdateDownloaderActivity.this, SecondActivity.class);
        startActivity(intent);
        finish(); // إغلاق واجهة التحميل نهائياً
    }
}
