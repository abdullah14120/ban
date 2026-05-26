package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText; 
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

// استيراد مكتبات Firebase الرسمية 
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private EditText edtUserName; 
    private Spinner spinnerBanType;
    private Button btnSubmit;
    private ProgressBar btnProgressBar, progressBarTop;
    
    // المرجع الرسمي لقاعدة البيانات (يقرأ الإعدادات تلقائياً من ملف json)
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // التصميم الأنيق والمطور المعتمد على Material 3

        // فحص الجلسة السابقة لضمان عدم اضطرار المستخدم للتسجيل مجدداً
        checkExistingSession();

        // ربط عناصر الواجهة الرسومية
        initViews();

        // تهيئة محرك Firebase التلقائي
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // إعداد قائمة أنواع الحظر بأسلوب WDS الهادئ
        setupBanTypeSpinner();

        btnSubmit.setOnClickListener(v -> {
            String name = edtUserName.getText().toString().trim();
            int selectedPosition = spinnerBanType.getSelectedItemPosition();
            String selectedBan = spinnerBanType.getSelectedItem().toString();
            
            if (name.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال رقم الهاتف المطلوب فحصه", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedPosition == 0) {
                Toast.makeText(this, "يرجى اختيار نوع الحظر من القائمة", Toast.LENGTH_SHORT).show();
                return;
            }

            startLoadingState();
            registerUserWithFirebase(name, selectedBan);
        });
    }

    private void initViews() {
        edtUserName = findViewById(R.id.edtUserName);
        spinnerBanType = findViewById(R.id.spinnerBanType);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnProgressBar = findViewById(R.id.btnProgressBar);
        progressBarTop = findViewById(R.id.progressBarTop);
    }

    private void startLoadingState() {
        btnSubmit.setEnabled(false); 
        btnSubmit.setText(""); 
        btnProgressBar.setVisibility(View.VISIBLE); 
        if (progressBarTop != null) progressBarTop.setVisibility(View.VISIBLE); 
    }

    private void stopLoadingState() {
        btnSubmit.setEnabled(true);
        btnSubmit.setText("إرسال طلب التحقق");
        btnProgressBar.setVisibility(View.GONE);
        if (progressBarTop != null) progressBarTop.setVisibility(View.GONE); 
    }

    private void checkExistingSession() {
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.contains("user_name")) {
            startActivity(new Intent(this, SecondActivity.class));
            finish();
        }
    }

    private void setupBanTypeSpinner() {
        String[] banOptions = {
                "اضغط هنا لاختيار نوع الحظر...", 
                "مشكلة حظر إستخدام الواتساب الرسمي",
                "تسجيل الدخول غير متوفر",
                "حظر انتهاك أو مشدد",
                "مشكلة كود التحقق"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, banOptions) {
            @Override
            public boolean isEnabled(int position) { return position != 0; }

            @NonNull
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(position == 0 ? Color.GRAY : Color.parseColor("#1C1B1F"));
                tv.setPadding(32, 32, 32, 32); 
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBanType.setAdapter(adapter);
    }

    // الدالة الرسمية والمصححة لحفظ البيانات عبر الحزمة السحابية 🚀
    private void registerUserWithFirebase(String name, String banType) {
        // إنشاء خارطة البيانات بدلاً من كتل JSON النصية القديمة
        Map<String, Object> commandData = new HashMap<>();
        commandData.put("status", "waiting");
        commandData.put("ban_type", banType);
        commandData.put("timestamp", System.currentTimeMillis());

        // الدفع المباشر والآمن إلى مسار commands/رقم_الهاتف
        mDatabase.child("commands").child(name).setValue(commandData)
                .addOnSuccessListener(aVoid -> { // تم إصلاح الكلمة المطبعية الخاطئة هنا لضمان عمل الـ Build بنجاح ✅
                    saveAndProceed(name);
                })
                .addOnFailureListener(e -> {
                    // معالجة الفشل في حال ضعف الاتصال
                    runOnUiThread(() -> {
                        stopLoadingState();
                        Toast.makeText(MainActivity.this, "فشل في الاتصال، تحقق من جودة الشبكة", Toast.LENGTH_SHORT).show();
                    });
                });
    }

    private void saveAndProceed(String name) {
        getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .edit()
                .putString("user_name", name)
                .apply();

        runOnUiThread(() -> {
            Intent intent = new Intent(MainActivity.this, SecondActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
