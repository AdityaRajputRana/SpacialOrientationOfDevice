package com.aditya.kylebase;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import org.w3c.dom.Text;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    /*
    Spacial Orientation v:1.0.1, Magnetic and Acc Sensor and Mean Deviation outlier
    and Calibration(Error Removal)
     */

    SharedPreferences preferences;
    float radDegConvertor = 57.2957795131f;
    float errorPitch = 0f;
    float errorRoll = 0f;

    TextView textView;
    Button calibrateBtn;
    TextView readingTxt;
    float barrier = 1f/57;

    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    int numReadings = 0;
    float[] mean = new float[3];
    float[] deviation = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        calibrateBtn = findViewById(R.id.calibrateBtn);
        readingTxt = findViewById(R.id.finalTxt);

        getLevel();
        calibrateBtn.setOnClickListener(view->{
            errorPitch = mean[1]*radDegConvertor;
            errorRoll = mean[2]*radDegConvertor;

            preferences.edit().putFloat("errorPitch", errorPitch)
                    .putFloat("errorRoll", errorRoll)
                    .commit();
        });
    }

    private void getLevel() {
        preferences = this.getSharedPreferences("KYLE_SENSOR_ERROR_DB_X84", MODE_PRIVATE);
        errorRoll = preferences.getFloat("errorRoll", 0f);
        errorPitch = preferences.getFloat("errorPitch", 0f);
        textView.setText("Getting Level");
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    // the device's accelerometer and magnetometer.
    public void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading);

        // "rotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles);

        // "orientationAngles" now has up-to-date information.


        textView.setText("Azimuth Rotation: " + (double) (orientationAngles[0]*radDegConvertor)
        + "\n Pitch Upwards Tilt: " + (double) (orientationAngles[1]*radDegConvertor)
        + "\n Roll Sideways Tilt: " +(double) ( orientationAngles[2]*radDegConvertor));

        saveMeans(orientationAngles);
    }

    private void saveMeans(float[] orientationAngles) {
        for (int i = 0; i < 3; i++){
            float old_mean = mean[i];
            int n = numReadings;
            float old_variance = deviation[i];
            float new_item = orientationAngles[i];
            float new_mean = (old_mean * n + new_item) / (n + 1);
            float new_variance = ((old_variance * n) + (n + 1) * (old_mean - new_mean) * (old_mean - new_mean) + (new_item - new_mean) * (new_item - old_mean)) / (n + 1);

            mean[i] = new_mean;
            deviation[i] = new_variance;
        }

        numReadings++;

        if (numReadings > 75){
            if (numReadings > 750){
                String txt = "Azimuth Rotation: " + (double) (mean[0]*radDegConvertor)
                        + "\n Pitch Upwards Tilt: " + (double) (mean[1]*radDegConvertor-errorPitch)
                        + "\n Roll Sideways Tilt: " +(double) ( mean[2]*radDegConvertor-errorRoll);
                readingTxt.setText("Error:\n"+ txt);
                sensorManager.unregisterListener(this);
            }

            boolean inRange = true;
            for (int i = 1; i < 3; i++){
                if (Math.abs(deviation[i]) > (barrier)){
                    inRange = false;
                }
            }
            if (inRange){
                float pitch = (mean[1]*radDegConvertor-errorPitch);
                float roll = ( mean[2]*radDegConvertor-errorRoll);

                if (Math.abs(pitch) <= 0.1f){
                    pitch = 0f;
                }
                if (Math.abs(roll) <= 0.1f){
                    roll = 0f;
                }


                String txt = "Azimuth Rotation: " + (double) (mean[0]*radDegConvertor)
                        + "(+-"+ (deviation[0]*radDegConvertor) + ")"
                        + "\n\n Pitch Upwards Tilt: " + pitch
                        + "(+-"+ (deviation[1]*radDegConvertor) + ")"
                        + "\n\n Roll Sideways Tilt: " + roll
                        + "(+-"+ (deviation[2]*radDegConvertor) + ")"
                        + "\n\nNum Counts = " + numReadings;

                readingTxt.setText("Final Values:\n"+ txt);
                sensorManager.unregisterListener(this);
            } else {
                barrier += 0.01f/57f;
            }
        }

    }


    @Override
    protected void onResume() {
        super.onResume();

        // Get updates from the accelerometer and magnetometer at a constant rate.
        // To make batch operations more efficient and reduce power consumption,
        // provide support for delaying updates to the application.
        //
        // In this example, the sensor reporting delay is small enough such that
        // the application receives an update before the system checks the sensor
        // readings again.
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Don't receive any more updates from either sensor.
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
        }

        updateOrientationAngles();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}