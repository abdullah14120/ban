package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText; // تم التغيير إلى EditText العادي
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // استخدام EditText العادي لمنع الانهيار (InflateException)
    private EditText edtUserName; 
    private Spinner spinnerBanType;
    private Button btnSubmit;
    private ProgressBar btnProgressBar;
    private final OkHttpClient client = new OkHttpClient();
    
    // رابط قاعدة البيانات الخاص بك
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. فحص الجلسة (إذا كان المستخدم مسجلاً مسبقاً ينتقل فوراً لشاشة الانتظار)
        checkExistingSession();

        // 2. ربط العناصر بالواجهة
        initViews();

        // 3. إعداد القائمة المنسدلة (Spinner)
        setupBanTypeSpinner();

        // 4. منطق زر الإرسال
        btnSubmit.setOnClickListener(v -> {
            String name = edtUserName.getText().toString().trim();
            int selectedPosition = spinnerBanType.getSelectedItemPosition();
            String selectedBan = spinnerBanType.getSelectedItem().toString();
            
            // التحقق من الإدخال
            if (name.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال رقم الهاتف", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedPosition == 0) {
                Toast.makeText(this, "يرجى اختيار نوع الحظر", Toast.LENGTH_SHORT).show();
                return;
            }

            // بدء حالة التحميل وتعطيل الزر لمنع التكرار
            startLoadingState();
            registerUserWithAdmin(name, selectedBan);
        });
    }

    private void initViews() {
        edtUserName = findViewById(R.id.edtUserName);
        spinnerBanType = findViewById(R.id.spinnerBanType);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnProgressBar = findViewById(R.id.btnProgressBar);
    }

    private void startLoadingState() {
        btnSubmit.setEnabled(false); // تعطيل الزر
        btnSubmit.setText(""); // إخفاء النص
        btnProgressBar.setVisibility(View.VISIBLE); // إظهار الدائرة
    }

    private void stopLoadingState() {
        btnSubmit.setEnabled(true);
        btnSubmit.setText("إرسال طلب التحقق");
        btnProgressBar.setVisibility(View.GONE);
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

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, banOptions) {
            @Override
            public boolean isEnabled(int position) { 
                // تعطيل العنصر الأول (العنوان) ليصبح غير قابل للاختيار
                return position != 0; 
            }

            @NonNull
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                // جعل العنوان باللون الرمادي والبقية بالأسود
                tv.setTextColor(position == 0 ? Color.GRAY : Color.BLACK);
                return view;
            }
        };
        spinnerBanType.setAdapter(adapter);
    }

    private void registerUserWithAdmin(String name, String banType) {
        // إنشاء مسار المستخدم في Firebase
        String url = FIREBASE_URL + "commands/" + name + ".json";
        
        try {
            JSONObject body = new JSONObject();
            body.put("status", "waiting"); // الحالة الابتدائية
            body.put("ban_type", banType);
            body.put("timestamp", System.currentTimeMillis());
            
            RequestBody requestBody = RequestBody.create(
                    body.toString(), 
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .put(requestBody) // استخدام PUT لإنشاء أو تحديث البيانات
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                    runOnUiThread(() -> {
                        stopLoadingState();
                        Toast.makeText(MainActivity.this, "تعذر الاتصال بالخادم، افحص الإنترنت", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (response.isSuccessful()) {
                        saveAndProceed(name);
                    } else {
                        runOnUiThread(() -> {
                            stopLoadingState();
                            Toast.makeText(MainActivity.this, "خطأ في السيرفر، حاول لاحقاً", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        } catch (Exception e) {
            stopLoadingState();
        }
    }

    private void saveAndProceed(String name) {
        // حفظ الاسم محلياً للانتقال التلقائي في المرات القادمة
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
