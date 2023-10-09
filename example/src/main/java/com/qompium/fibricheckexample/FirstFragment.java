package com.qompium.fibricheckexample;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.qompium.fibricheck.camerasdk.FibriChecker;
import com.qompium.fibricheck.camerasdk.listeners.FibriListener;
import com.qompium.fibricheck.camerasdk.measurement.MeasurementData;
import com.qompium.fibricheckexample.databinding.FragmentFirstBinding;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import com.google.gson.Gson;


public class FirstFragment extends Fragment {

    private final String TAG = "FC-Example";
    private FragmentFirstBinding binding;
    private FibriChecker fibriChecker;

    private SimpleDateFormat formatter;


    private TextView eventOverview;
    private TextView eventLog;


    private HashMap<String, Boolean> triggeredEvents;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);


        triggeredEvents = new HashMap<String, Boolean>();

        // Set Events to false
        triggeredEvents.put("onFingerDetected",false);
        triggeredEvents.put("onFingerRemoved",false);
        triggeredEvents.put("onHeartBeat",false);
        triggeredEvents.put("onPulseDetected",false);
        triggeredEvents.put("onCalibrationReady",false);
        triggeredEvents.put("onPulseDetectionTimeExpired",false);
        triggeredEvents.put("onFingerDetectionTimeExpired",false);
        triggeredEvents.put("onMovementDetected",false);
        triggeredEvents.put("onMeasurementStart",false);
        triggeredEvents.put("onMeasurementFinished",false);
        triggeredEvents.put("onMeasurementError",false);
        triggeredEvents.put("onMeasurementProcessed",false);
        triggeredEvents.put("onSampleReady",false);
        triggeredEvents.put("onTimeRemaining",false);


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

    private void updateEventUI(){

        String newText = "";

        Iterator it = triggeredEvents.entrySet().iterator();

        while(it.hasNext()){
            HashMap.Entry el = (HashMap.Entry)it.next();

            if((boolean) el.getValue()){
                newText += "✅ " + (String) el.getKey() + "\n";
            } else {
                newText += "❌ " + (String) el.getKey() + "\n";
            }
        }

        eventOverview.setText(newText);
    }

    private void logEvent(String event, boolean doLog) {
        triggeredEvents.put(event, true);
        updateEventUI();

        if(doLog){
            addLog(event);
        }

    }
    private void addLog(String txt) {
        String currText = (String) eventLog.getText();

        currText += "\n" + formatter.format(new Date())  +txt;
        eventLog.setText(currText);
    }

    private void validateMeasurement(MeasurementData measurementData){

        try {
            Gson gson = new Gson();
            JSONObject jsonObject = new JSONObject(gson.toJson(measurementData));

            if(jsonObject.has("heartrate")
                    && jsonObject.has("attempts")
                    && jsonObject.has("skippedMovementDetection")
                    && jsonObject.has("skippedPulseDetection")
                    && jsonObject.has("skippedFingerDetection")
                    && jsonObject.has("quadrants")
                    && jsonObject.has("technical_details")
                    && jsonObject.has("time")
                    && jsonObject.has("measurement_timestamp")
            ){
                addLog("VALID measurement");
            } else {
                addLog("FAILED to validate measurement");
            }


        } catch(Exception e){

            addLog("FAILED to parse measurement");

        }



    }

    private void initialiseMeasurement(){

        ViewGroup viewGroup = binding.getRoot();

        fibriChecker = new FibriChecker.FibriBuilder(viewGroup.getContext(), viewGroup).build();

        fibriChecker.sampleTime = 20;
        fibriChecker.fingerDetectionExpiryTime = 10000;
        fibriChecker.pulseDetectionExpiryTime = 10000;

        fibriChecker.setFibriListener(new FibriListener() {

            @Override public void onSampleReady(final double ppg, double raw) {
                logEvent("onSampleReady", false);
            }

            @Override public void onFingerDetected() {
                logEvent("onFingerDetected", true);
            }

            @Override public void onFingerRemoved(double y, double v, double stdDevY) {
                logEvent("onFingerRemoved", true);
            }

            @Override public void onCalibrationReady() {
                logEvent("onCalibrationReady", true);
            }

            @Override public void onHeartBeat(int value) {
                logEvent("onHeartBeat", false);
            }

            @Override public void onTimeRemaining(int seconds) {
                logEvent("onTimeRemaining", false);
            }

            @Override public void onMeasurementFinished() {
                logEvent("onMeasurementFinished", true);
            }

            @Override public void onMeasurementStart() {
                logEvent("onMeasurementStart", true);
            }

            @Override public void onFingerDetectionTimeExpired() {
                logEvent("onFingerDetectionTimeExpired", true);
            }

            @Override public void onPulseDetected() {
                logEvent("onPulseDetected", true);
            }

            @Override public void onPulseDetectionTimeExpired() {
                logEvent("onPulseDetectionTimeExpired", true);

            }

            @Override public void onMovementDetected() {
                logEvent("onMovementDetected", true);
            }

            @Override public void onMeasurementProcessed(MeasurementData measurementData) {
                logEvent("onMeasurementProcessed", true);
                validateMeasurement(measurementData);
            }

            @Override public void onMeasurementError(String message) {
                logEvent("onMeasurementError", true);
            }
        });



    }

    private void startMeasurement(){
        addLog("START MEASUREMENT BUTTON-PRESS");
        fibriChecker.start();
        Log.i(TAG, "Start FibriCheck Measurement");
    }

    private void stopMeasurement(){
        addLog("STOP MEASUREMENT BUTTON-PRESS");
        fibriChecker.stop();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initialiseMeasurement();

        formatter = new SimpleDateFormat("mm:ss:SSS");
        eventOverview = (TextView) view.findViewById(R.id.textView_overview2);
        eventLog = (TextView) view.findViewById(R.id.textView_overview);


        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                startMeasurement();
            }
        });

        binding.buttonSecond.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopMeasurement();
            }
        });


        updateEventUI();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}