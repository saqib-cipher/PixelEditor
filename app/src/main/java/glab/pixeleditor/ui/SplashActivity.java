package glab.pixeleditor.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import glab.pixeleditor.R;
import glab.pixeleditor.service.ZipCompressService;

public class SplashActivity extends AppCompatActivity {

    private LinearProgressIndicator splashProgressBar;
    private TextView tvSplashStatus;
    private TextView tvSplashProgress;

    private boolean isReceiverRegistered = false;
    private boolean isNavigating = false;

    private final BroadcastReceiver zipReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int progress = intent.getIntExtra("progress", 0);
            String fileName = intent.getStringExtra("fileName");
            String error = intent.getStringExtra("error");

            if (error != null) {
                if (tvSplashStatus != null) {
                    tvSplashStatus.setText("Ready");
                }
                navigateToHome();
                return;
            }

            if (splashProgressBar != null) {
                splashProgressBar.setIndeterminate(false);
                splashProgressBar.setProgressCompat(progress, true);
            }

            if (tvSplashProgress != null) {
                tvSplashProgress.setVisibility(View.VISIBLE);
                tvSplashProgress.setText(progress + "%");
            }

            if (tvSplashStatus != null && fileName != null && !fileName.isEmpty()) {
                tvSplashStatus.setText(fileName);
            }

            if (progress >= 100) {
                new Handler(Looper.getMainLooper()).postDelayed(SplashActivity.this::navigateToHome, 400);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        splashProgressBar = findViewById(R.id.splashProgressBar);
        tvSplashStatus = findViewById(R.id.tvSplashStatus);
        tvSplashProgress = findViewById(R.id.tvSplashProgress);

        View logo = findViewById(R.id.layoutSplashLogo);
        if (logo != null) {
            logo.setScaleX(0.8f);
            logo.setScaleY(0.8f);
            logo.setAlpha(0f);
            logo.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(700)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }

        boolean alreadyExtracted = ZipCompressService.isAssetsExtracted(this);

        if (alreadyExtracted) {
            if (splashProgressBar != null) {
                splashProgressBar.setIndeterminate(true);
            }
            if (tvSplashStatus != null) {
                tvSplashStatus.setText("Starting Studio...");
            }
            new Handler(Looper.getMainLooper()).postDelayed(this::navigateToHome, 800);
        } else {
            if (splashProgressBar != null) {
                splashProgressBar.setIndeterminate(false);
                splashProgressBar.setProgressCompat(0, false);
            }
            if (tvSplashStatus != null) {
                tvSplashStatus.setText("Extracting typography & vector assets...");
            }
            if (tvSplashProgress != null) {
                tvSplashProgress.setVisibility(View.VISIBLE);
                tvSplashProgress.setText("0%");
            }

            IntentFilter filter = new IntentFilter(ZipCompressService.BROADCAST_ACTION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(zipReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(zipReceiver, filter);
            }
            isReceiverRegistered = true;

            ZipCompressService.startAssetExtraction(this);
        }
    }

    private synchronized void navigateToHome() {
        if (isNavigating) return;
        isNavigating = true;

        if (isReceiverRegistered) {
            try {
                unregisterReceiver(zipReceiver);
                isReceiverRegistered = false;
            } catch (Exception ignored) {}
        }

        startActivity(new Intent(SplashActivity.this, HomeActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(zipReceiver);
                isReceiverRegistered = false;
            } catch (Exception ignored) {}
        }
    }
}
