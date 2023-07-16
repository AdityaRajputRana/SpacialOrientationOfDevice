package com.aditya.kylebase;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class TestingActivity extends AppCompatActivity {

    private TextView textView;
    private Button calibrateBtn;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testing);

        textView = findViewById(R.id.textView);
        calibrateBtn = findViewById(R.id.calibrateBtn);

        textView.setOnClickListener(view->{
            getLevel();
        });
        calibrateBtn.setOnClickListener(view->{
            calibrate();
        });
    }

    private void calibrate() {
        textView.setText("Calibrating");
        KyleCore.calibrate(this, new KyleCore.CalibrationListener() {
            @Override
            public void onError(int errorCode, String message) {
                textView.setText(message);
            }

            @Override
            public void onCalibrationSuccess() {
                textView.setText("Calibrated Success");
            }
        });
    }

    private void getLevel() {
        textView.setText("Getting Level");
        KyleCore.detectLevel(this, new KyleCore.OnLevelDetectedListener() {
            @Override
            public void onError(int errorCode, String message) {
                textView.setText(message);
            }

            @Override
            public void onLevelDetected(float baseTilt, float sidewaysTilt) {
                textView.setText("Base:" + baseTilt + "\n\nside" + sidewaysTilt);
            }
        });
    }
}