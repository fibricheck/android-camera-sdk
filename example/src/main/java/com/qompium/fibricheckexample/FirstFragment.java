package com.qompium.fibricheckexample;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.qompium.fibricheck_camera_sdk.FibriChecker;
import com.qompium.fibricheck_camera_sdk.listeners.FibriListener;
import com.qompium.fibricheck_camera_sdk.measurement.MeasurementData;
import com.qompium.fibricheckexample.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private final String TAG = "FC-Example";
    private FragmentFirstBinding binding;
    private FibriChecker fibriChecker;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);

        // Request Camera Permissions
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if(isGranted){
                //Permission Granted
                Log.i(TAG,"Camera Permission granted");
            } else {
                // Permission Denied
                Log.i(TAG,"Camera Permission Denied");
            }
        }).launch(Manifest.permission.CAMERA);


        return binding.getRoot();

    }

    private void initialiseMeasurement(){

        ViewGroup viewGroup = binding.getRoot();

        fibriChecker = new FibriChecker.FibriBuilder(viewGroup.getContext(), viewGroup).build();

        fibriChecker.setFibriListener(new FibriListener() {

            @Override public void onSampleReady(final double ppg, double raw) {
                Log.i(TAG, "Callback: onSampleReady");

            }

            @Override public void onFingerDetected() {
                Log.i(TAG, "Callback: onFingerDetected");
            }

            @Override public void onFingerRemoved(double y, double v, double stdDevY) {
                Log.i(TAG, "Callback: onFingerRemoved");

            }

            @Override public void onCalibrationReady() {
                Log.i(TAG, "Callback: onCalibrationReady");

            }

            @Override public void onHeartBeat(int value) {
                Log.i(TAG, "Callback: onHeartBeat: " + value);
            }

            @Override public void timeRemaining(int seconds) {
                Log.i(TAG, "Callback: timeRemaining");

            }

            @Override public void onMeasurementFinished() {
                Log.i(TAG, "Callback: onMeasurementFinished");

            }

            @Override public void onMeasurementStart() {
                Log.i(TAG, "Callback: onMeasurementStart");

            }

            @Override public void onFingerDetectionTimeExpired() {
                Log.i(TAG, "Callback: onFingerDetectionTimeExpired");

            }

            @Override public void onPulseDetected() {
                Log.i(TAG, "Callback: onPulseDetected");
            }

            @Override public void onPulseDetectionTimeExpired() {
                Log.i(TAG, "Callback: onPulseDetectionTimeExpired");

            }

            @Override public void onMovementDetected() {
                Log.i(TAG, "Callback: onMovementDetected");
            }

            @Override public void onMeasurementProcessed(MeasurementData measurementData) {
                Log.i(TAG, "Callback: onMeasurementProcessed");
            }

            @Override public void onMeasurementError(String message) {
                Log.i("FC", "onMeasurementError");
            }
        });



    }

    private void startMeasurement(){
        fibriChecker.start();
        Log.i(TAG, "Start FibriCheck Measurement");
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initialiseMeasurement();

        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                startMeasurement();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}