package com.ban.ab;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

public class StartActivity extends AppCompatActivity {

    private String selectedProblem = "";
    private Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_start);

        btnStart = findViewById(R.id.btnStartNext);
        
        // إعداد المشاكل الثلاث
        setupProblemCard(R.id.cardProblem1, "مشكلة حظر واتساب الرسمي", R.drawable.img_prob1);
        setupProblemCard(R.id.cardProblem2, "مشكلة تسجيل الدخول غير متوفر", R.drawable.img_prob2);
        setupProblemCard(R.id.cardProblem3, "مشكلة حظر إنتهاك أو مشدد", R.drawable.img_prob3);

        // زر الفيديو الشرح
        findViewById(R.id.btnWatchTutorial).setOnClickListener(v -> showVideoPopup("https://www.youtube.com/embed/YOUR_VIDEO_ID"));

        btnStart.setOnClickListener(v -> {
            if (selectedProblem.isEmpty()) {
                Toast.makeText(this, "يرجى اختيار نوع المشكلة أولاً", Toast.LENGTH_SHORT).show();
                return;
            }
            // الانتقال لصفحة الهوية مع إرسال نوع المشكلة
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("selected_problem", selectedProblem);
            startActivity(intent);
        });
    }

    private void setupProblemCard(int cardId, String title, int imageRes) {
        MaterialCardView card = findViewById(cardId);
        ImageView img = card.findViewById(R.id.imgThumb);
        TextView txt = card.findViewById(R.id.txtTitle);

        img.setImageResource(imageRes);
        txt.setText(title);

        // عند الضغط على الصورة للتكبير
        img.setOnClickListener(v -> showImagePopup(imageRes));

        // عند الضغط على البطاقة للاختيار
        card.setOnClickListener(v -> {
            selectedProblem = title;
            resetCardsHighlight();
            card.setCardBackgroundColor(Color.parseColor("#E3F2FD")); // لون تمييز عند الاختيار
            card.setStrokeColor(Color.parseColor("#1A73E8"));
            card.setStrokeWidth(5);
        });
    }

    private void resetCardsHighlight() {
        ((MaterialCardView)findViewById(R.id.cardProblem1)).setStrokeWidth(0);
        ((MaterialCardView)findViewById(R.id.cardProblem2)).setStrokeWidth(0);
        ((MaterialCardView)findViewById(R.id.cardProblem3)).setStrokeWidth(0);
    }

    private void showImagePopup(int resId) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.popup_image);
        ImageView fullImg = dialog.findViewById(R.id.imgFull);
        fullImg.setImageResource(resId);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }

    private void showVideoPopup(String videoUrl) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.popup_video);
        WebView webView = dialog.findViewById(R.id.videoWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl(videoUrl);
        
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.show();
    }
}
