package com.senseplus.sense;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Button btnUser = findViewById(R.id.btnUser);
        Button btnGuardian = findViewById(R.id.btnGuardian);

        btnUser.setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
        btnGuardian.setOnClickListener(v ->
                startActivity(new Intent(this, GuardianActivity.class)));
    }
}