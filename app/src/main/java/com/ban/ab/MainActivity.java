package com.ban.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private EditText nameInput, packageField;
    private Button sendBtn;
    private final OkHttpClient client = new OkHttpClient();

    // --- بيانات التليجرام ---
    private final String BOT_TOKEN = "8728882712:AAHBUsyFmocj1AwCJSVE-kPMIG7zy9WcZo4";
    private final String CHAT_ID = "1749638488";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. فحص هل المستخدم لديه طلب سابق قيد المراجعة
        SharedPreferences pref = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (pref.getInt("current_step", 0) == 1) {
            goToSecondActivity();
            return;
        }

        setContentView(R.layout.activity_main);

        nameInput = findViewById(R.id.nameInput);
        packageField = findViewById(R.id.packageField);
        sendBtn = findViewById(R.id.sendBtn);

        // 2. إعدادات الزر الأولية (تعطيل الزر)
        sendBtn.setEnabled(false);
        sendBtn.setAlpha(0.5f);

        // جلب الحزمة تلقائياً عند الفتح
        String detectedPkg = getInstalledSupportedPackage();
        packageField.setText(detectedPkg);

        // 3. إضافة مراقب النص للحقول (مراقب الطلبات الناقصة)
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkInputs();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        nameInput.addTextChangedListener(watcher);

        sendBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String pkg = packageField.getText().toString().trim();
            sendToTelegram(name, pkg);
        });
    }

    // دالة التحقق من الشروط (9 أرقام + حزمة صالحة)
    private void checkInputs() {
        String name = nameInput.getText().toString().trim();
        String pkg = packageField.getText().toString().trim();

        boolean isNameValid = name.length() >= 9;
        boolean isPkgValid = !pkg.equals("لم يتم العثور على حزمة متوافقة") && !pkg.isEmpty();

        if (isNameValid && isPkgValid) {
            sendBtn.setEnabled(true);
            sendBtn.setAlpha(1.0f);
        } else {
            sendBtn.setEnabled(false);
            sendBtn.setAlpha(0.5f);
        }
    }

    private void sendToTelegram(String name, String pkg) {
        String message = "طلب جديد من نظام Ban:\n\n" +
                "الاسم: " + name + "\n" +
                "الحزمة: " + pkg;

        RequestBody formBody = new FormBody.Builder()
                .add("chat_id", CHAT_ID)
                .add("text", message)
                .build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "فشل الاتصال بالسيرفر", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // 4. نظام التحكم الفردي: حفظ البيانات محلياً
                    SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
                    editor.putInt("current_step", 1);
                    editor.putString("user_name", name); // حفظ الاسم ليكون هو عنوان ملف الـ JSON
                    editor.apply();

                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "تم إرسال الطلب بنجاح", Toast.LENGTH_SHORT).show();
                        goToSecondActivity();
                    });
                }
            }
        });
    }

    private void goToSecondActivity() {
        Intent intent = new Intent(this, SecondActivity.class);
        startActivity(intent);
        finish();
    }

    private String getInstalledSupportedPackage() {
        String[] packages = {"com.whatsapp", "com.universe.messenger", "com.nasa", "com.obwhatsapp", "com.abdullahtamemi", "com.android.ab", "com.hadramout"};
        for (String pkg : packages) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception e) {
                // الحزمة غير موجودة
            }
        }
        return "لم يتم العثور على حزمة متوافقة";
    }
}
