package com.senseplus.sense;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        getWindow().setStatusBarColor(Color.parseColor("#0D2B45"));

        findViewById(R.id.cardUser).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.cardComm).setOnClickListener(v ->
                startActivity(new Intent(this, CommunicationActivity.class)));
        findViewById(R.id.cardVisual).setOnClickListener(v ->
                startActivity(new Intent(this, VisualAssistActivity.class)));
        findViewById(R.id.cardSensor).setOnClickListener(v ->
                startActivity(new Intent(this, SensorInfoActivity.class)));
        findViewById(R.id.cardGuardian).setOnClickListener(v -> authenticateThenOpenGuardian());
    }

    private void authenticateThenOpenGuardian() {
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

        BiometricManager bm = BiometricManager.from(this);
        if (bm.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "No lock set up; opening guardian view", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, GuardianActivity.class));
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        startActivity(new Intent(HomeActivity.this, GuardianActivity.class));
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                        Toast.makeText(HomeActivity.this, "Authentication: " + errString, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Guardian access")
                .setSubtitle("Verify it's you to view the dashboard")
                .setAllowedAuthenticators(authenticators)
                .build();
        prompt.authenticate(info);
    }
}