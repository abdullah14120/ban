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

import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText edtUserName; 
    private Spinner spinnerBanType;
    private Button btnSubmit;
    private ProgressBar btnProgressBar, progressBarTop; // إضافة المحرك العلوي الجديد ✅
    private final OkHttpClient client = new OkHttpClient();
    
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification); // التأكد من اسم ملف التصميم الجديد

        checkExistingSession();
        initViews();
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
            registerUserWithAdmin(name, selectedBan);
        });
    }

    private void initViews() {
        edtUserName = findViewById(R.id.edtUserName);
        spinnerBanType = findViewById(R.id.spinnerBanType);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnProgressBar = findViewById(R.id.btnProgressBar);
        progressBarTop = findViewById(R.id.progressBarTop); // ربط شريط المعالجة الهادئ ✅
    }

    private void startLoadingState() {
        btnSubmit.setEnabled(false); 
        btnSubmit.setText(""); 
        btnProgressBar.setVisibility(View.VISIBLE); 
        if (progressBarTop != null) progressBarTop.setVisibility(View.VISIBLE); // تشغيل الخط العلوي
    }

    private void stopLoadingState() {
        btnSubmit.setEnabled(true);
        btnSubmit.setText("إرسال طلب التحقق");
        btnProgressBar.setVisibility(View.GONE);
        if (progressBarTop != null) progressBarTop.setVisibility(View.GONE); // إيقاف الخط العلوي
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

        // تم استبدال الـ Layout الافتراضي بـ simple_spinner_item ليتناسب مع أبعاد خلايا الواجهة الجديدة
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, banOptions) {
            @Override
            public boolean isEnabled(int position) { return position != 0; }

            @NonNull
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                // لون رمادي هادئ للعنصر الدليلي الأول، وأسود أنيق للبقية يتناسق مع Material 3
                tv.setTextColor(position == 0 ? Color.GRAY : Color.parseColor("#1C1B1F"));
                tv.setPadding(32, 32, 32, 32); // مسافات مريحة متناسقة مع الـ WDS
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBanType.setAdapter(adapter);
    }

    private void registerUserWithAdmin(String name, String banType) {
        String url = FIREBASE_URL + "commands/" + name + ".json";
        
        try {
            JSONObject body = new JSONObject();
            body.put("status", "waiting"); 
            body.put("ban_type", banType);
            body.put("timestamp", System.currentTimeMillis());
            
            RequestBody requestBody = RequestBody.create(
                    body.toString(), 
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .put(requestBody) 
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                    runOnUiThread(() -> {
                        stopLoadingState();
                        Toast.makeText(MainActivity.this, "فشل في الاتصال، تأكد من جودة الإنترنت", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (response.isSuccessful()) {
                        saveAndProceed(name);
                    } else {
                        runOnUiThread(() -> stopLoadingState());
                    }
                }
            });
        } catch (Exception e) {
            stopLoadingState();
        }
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
