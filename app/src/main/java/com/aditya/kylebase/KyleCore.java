package com.aditya.kylebase;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class KyleCore {

    private SharedPreferences preferences;
    private boolean inCalibrationMode = false;
    private final float radDegConvertor = 57.2957795131f;
    private float errorPitch = 0f;
    private float errorRoll = 0f;
    private float barrier = 1f/57; // Variation has to be less than this for first 75 reading than is increased to fit.

    private int numReadings = 0;
    private float[] mean = new float[3];
    private float[] deviation = new float[3];

    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private OnLevelDetectedListener levelListener;
    private CalibrationListener calibrationListener;
    private Activity activity;

    public interface OnLevelDetectedListener{
        void onError(int errorCode, String message);
        void onLevelDetected(float baseTilt, float sidewaysTilt);
    }

    public interface CalibrationListener{
        void onError(int errorCode, String message);
        void onCalibrationSuccess();
    }

    private KyleCore(Activity activity, OnLevelDetectedListener listener) {
        this.activity = activity;
        this.levelListener = listener;
        inCalibrationMode = false;
        setUpFields();
    }

    private KyleCore(Activity activity, CalibrationListener listener){
        this.activity = activity;
        this.calibrationListener = listener;
        inCalibrationMode =true;
        setUpFields();
    }

    private void setUpFields() {
        preferences = activity.getSharedPreferences("KYLE_SENSOR_ERROR_DB_X84", MODE_PRIVATE);
        errorRoll = preferences.getFloat("errorRoll", 0f);
        errorPitch = preferences.getFloat("errorPitch", 0f);
        sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
    }

    private void recordData(){
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(sensorListener, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(sensorListener, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void stopRecording(){
        sensorManager.unregisterListener(sensorListener);
    }

    public static void detectLevel(Activity activity, OnLevelDetectedListener listener){
        KyleCore core = new KyleCore(activity, listener);
        core.recordData();
    }

    public static void calibrate(Activity activity, CalibrationListener listener){
        KyleCore core = new KyleCore(activity, listener);
        core.recordData();
    }

    private SensorEventListener sensorListener = new SensorEventListener() {
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
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private void updateOrientationAngles() {
        SensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading);
        SensorManager.getOrientation(rotationMatrix, orientationAngles);
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
                if (levelListener != null && !inCalibrationMode){
                    levelListener.onError(1, "The device is not held stable");
                }
                if (calibrationListener != null && inCalibrationMode){
                    calibrationListener.onError(1, "The device is not held stable");
                }
                stopRecording();
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

                if (Math.abs(pitch) <= 0.2f){
                    pitch = 0f;
                }
                if (Math.abs(roll) <= 0.2f){
                    roll = 0f;
                }

                if (levelListener != null && !inCalibrationMode){
                    levelListener.onLevelDetected(pitch, roll);
                }
                if (inCalibrationMode){
                    saveCalibrationErrors();
                }
                stopRecording();
            } else {
                barrier += 0.01f/57f;
            }
        }

    }

    private void saveCalibrationErrors() {
        errorPitch = mean[1]*radDegConvertor;
        errorRoll = mean[2]*radDegConvertor;

        preferences.edit().putFloat("errorPitch", errorPitch)
                .putFloat("errorRoll", errorRoll)
                .commit();

        if (calibrationListener != null){
            calibrationListener.onCalibrationSuccess();
        }
    }

}
