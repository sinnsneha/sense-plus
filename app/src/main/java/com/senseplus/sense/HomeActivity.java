package com.senseplus.sense;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
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

        Button btnUser = findViewById(R.id.btnUser);
        Button btnGuardian = findViewById(R.id.btnGuardian);
        Button btnComm = findViewById(R.id.btnComm);
        Button btnVisual = findViewById(R.id.btnVisual);

        btnUser.setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));

        btnComm.setOnClickListener(v ->
                startActivity(new Intent(this, CommunicationActivity.class)));

        btnVisual.setOnClickListener(v ->
                startActivity(new Intent(this, VisualAssistActivity.class)));

        btnGuardian.setOnClickListener(v -> authenticateThenOpenGuardian());
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
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        startActivity(new Intent(HomeActivity.this, GuardianActivity.class));
                    }
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        Toast.makeText(HomeActivity.this, "Authentication: " + errString,
                                Toast.LENGTH_SHORT).show();
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