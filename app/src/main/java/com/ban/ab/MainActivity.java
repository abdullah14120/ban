package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText nameInput, packageField;
    private Button sendBtn;
    private final OkHttpClient client = new OkHttpClient();

    // --- إعدادات الربط ---
    private final String BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String CHAT_ID = "1749638488";
    private final String GITHUB_TOKEN = "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";
    private final String GITHUB_REPO_PATH = "abdullah14120/ban"; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // فحص حالة المستخدم (هل سجل مسبقاً؟)
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.getInt("current_step", 0) == 1) {
            goToSecondActivity();
            return;
        }

        setContentView(R.layout.activity_main);
        nameInput = findViewById(R.id.nameInput);
        packageField = findViewById(R.id.packageField);
        sendBtn = findViewById(R.id.sendBtn);

        sendBtn.setEnabled(false);
        sendBtn.setAlpha(0.5f);

        packageField.setText(getInstalledSupportedPackage());

        nameInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkInputs();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        sendBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String pkg = packageField.getText().toString().trim();
            sendToTelegram(name, pkg);
        });
    }

    private void checkInputs() {
        String name = nameInput.getText().toString().trim();
        String pkg = packageField.getText().toString().trim();
        boolean isValid = name.length() >= 9 && !pkg.equals("لم يتم العثور على حزمة متوافقة");
        sendBtn.setEnabled(isValid);
        sendBtn.setAlpha(isValid ? 1.0f : 0.5f);
    }

    private void sendToTelegram(String name, String pkg) {
        String message = "📦 طلب جديد من التطبيق\n\n👤 الاسم: " + name + "\n📱 الحزمة المكتشفة:\n" + pkg;
        RequestBody body = new FormBody.Builder()
                .add("chat_id", CHAT_ID)
                .add("text", message).build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage")
                .post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                 runOnUiThread(() -> Toast.makeText(MainActivity.this, "فشل الاتصال بتليجرام", Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    createJsonOnGithub(name);
                }
            }
        });
    }

    private void createJsonOnGithub(String name) {
    // تأكد من المسار (يجب أن يكون مجلد commands موجوداً مسبقاً في المستودع)
    String url = "https://api.github.com/repos/" + GITHUB_REPO_PATH + "/contents/commands/" + name + ".json";
    
    // المحتوى الافتراضي للملف
    String content = "{\"status\": \"waiting\"}";
    String encodedContent = Base64.encodeToString(content.getBytes(), Base64.NO_WRAP);
    
    // إرسال البيانات مع تحديد الفرع (main)
    String jsonPayload = "{\"message\":\"auto_registration\",\"content\":\"" + encodedContent + "\",\"branch\":\"main\"}";

    RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json; charset=utf-8"));
    
    Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + GITHUB_TOKEN) // تأكد من وجود مسافة بعد Bearer
            .addHeader("Accept", "application/vnd.github.v3+json")
            .addHeader("Content-Type", "application/json")
            .put(body)
            .build();

    client.newCall(request).enqueue(new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
            Log.e("GITHUB_API", "خطأ في الشبكة: " + e.getMessage());
            // سننقل المستخدم للواجهة التالية حتى لو فشل الإنترنت لكي لا يعلق التطبيق
            saveAndProceed(name);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            String responseData = response.body() != null ? response.body().string() : "";
            
            if (response.isSuccessful() || response.code() == 422) {
                // 201 تعني تم الإنشاء، 422 تعني الملف موجود أصلاً
                saveAndProceed(name);
            } else {
                // طباعة تفاصيل الخطأ في Logcat للتحليل
                Log.e("GITHUB_API", "فشل الإنشاء! الكود: " + response.code());
                Log.e("GITHUB_API", "رسالة السيرفر: " + responseData);
                
                // الانتقال أيضاً لضمان استمرارية التطبيق
                saveAndProceed(name);
            }
        }
    });
}

    private void saveAndProceed(String name) {
        SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
        editor.putInt("current_step", 1);
        editor.putString("user_name", name);
        editor.apply();
        
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "تم إرسال الطلب بنجاح", Toast.LENGTH_SHORT).show();
            goToSecondActivity();
        });
    }

    private void goToSecondActivity() {
        startActivity(new Intent(this, SecondActivity.class));
        finish();
    }

    private String getInstalledSupportedPackage() {
        String[] pkgs = {"com.whatsapp", "com.universe.messenger", "com.obwhatsapp", "com.nasa", "com.android.ab"};
        for (String p : pkgs) {
            try { getPackageManager().getPackageInfo(p, 0); return p; } catch (Exception ignored) {}
        }
        return "لم يتم العثور على حزمة متوافقة";
    }
}
