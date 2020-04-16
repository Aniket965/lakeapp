package com.scibots.lakeapp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.app.Service;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;


import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.scibots.lakeapp.App.ALERT_CHANNEL_ID;
import static com.scibots.lakeapp.App.CHANNEL_ID;

public class HotWordTriggeringService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    private boolean USE_KEYWORD_SPOTTING = true;
    private boolean USE_ENV_STRESS_DETECTION = true;
    private boolean USE_STRESS_DETECTION = true;
    private final IBinder mBinder = new MyBinder();
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 500;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
    private static final String MODEL_FILENAME = "file:///android_asset/edgespeechneta.pb";
    private static final String  NEW_MODEL_FILENAME = "file:///android_asset/augkeyword1.pb";
    private static final String ENV_MODEL_FILENAME = "file:///android_asset/stressnew.pb";
    private static final String INPUT_DATA_NAME = "trainable_stft_input";
    private static final String INPUT_DATA_NAME_KEYWORD = "trainable_stft_input";
    private static final String OUTPUT_SCORES_NAME = "dense_2/Softmax";
    private static final String OUTPUT_SCORES_NAME_NEWMODEL = "dense_3/Softmax";

    private static final String ENV_INPUT_DATA_NAME = "trainable_stft_input";
    private static final String ENV_OUTPUT_SCORES_NAME = "dense_1/Softmax";
    private  String phonenumber = "+91-9899023974";
    private AudioRecord record;

    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    boolean shouldContinueRecognition = true;
    private Thread recognitionThread;
    private Thread alertThread;

    private final ReentrantLock recordingBufferLock = new ReentrantLock();
    private TensorFlowInferenceInterface inferenceInterface;
    private TensorFlowInferenceInterface env_inferenceInterface;
    private TensorFlowInferenceInterface newmodel_inferenceInterface;
    private final String TAG = "HotServiceActivity";
    Notification notification;
    NotificationCompat.Builder alertNotification;
    private AcousticEchoCanceler echoCanceler;
    private NoiseSuppressor noiseSuppressor;
    private AutomaticGainControl automaticGainControl;
    private boolean gps_setting = false;
    private boolean ongoingPanicEvent = false;

    // Google location

    private Location location;
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;
    private Location currentlocation;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "On Create called");
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                location =  (locationResult.getLastLocation());
                Log.d(TAG, "location " + location);
                if(location != null) {
                    currentlocation = location;
                }
            }
        };

        createLocationRequest();
        getLastLocation();
    }
    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(2000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void getLastLocation() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                Log.d(TAG, "got location.");
                                currentlocation = task.getResult();
                            } else {
                                Log.w(TAG, "Failed to get location.");
                            }
                        }
                    });
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission." + unlikely);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldContinue = true;
        shouldContinueRecognition = true;
        if (intent != null) {
            Log.d(TAG,"Service on Start");
            String action = intent.getAction();
            if( action != null &&action.equals("CANCEL_ALERT")) {
                // cancel panic event;

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                preferences.edit().putString("cancel_event","true").commit();
            } else  {
                // start our ml models normally

                notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Automatic Protection On")
                        .setContentText("You are safe!")
                        .setSmallIcon(R.drawable.ic_add)
                        .setOnlyAlertOnce(true)
                        .build();

                startForeground(1, notification);

                inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILENAME);
                env_inferenceInterface = new TensorFlowInferenceInterface(getAssets(), ENV_MODEL_FILENAME);
                newmodel_inferenceInterface = new TensorFlowInferenceInterface(getAssets(), NEW_MODEL_FILENAME);


                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                preferences.edit().putString("cancel_event","false").commit();
                String microphoneResult = preferences.getString("microphone", "false");
                String Gps_settingResult = preferences.getString("gps_setting","false");
                Log.d(TAG, "Setting: " + microphoneResult + Gps_settingResult);
                if(Gps_settingResult.equals("true")) gps_setting = true;
                // location client
                googleApiClient = new GoogleApiClient.Builder(this)
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();


                    USE_KEYWORD_SPOTTING = true;
                    USE_STRESS_DETECTION = true;
                    USE_ENV_STRESS_DETECTION = true;

                startRecording();
                startRecognition();


            }
        }

        return START_NOT_STICKY;
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
        recordingThread = null;
    }

    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        short[] audioBuffer = new short[bufferSize / 2];
        Log.d(TAG,"buffer size ->" + bufferSize);
        record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Audio Record can't initialize!");
            return;
        }

        if (!startAEC(record.getAudioSessionId())) {
            Log.i(TAG, "Start aec fail.");
        } else {
            Log.i(TAG, "Start aec success.");
        }
        if (!startNS(record.getAudioSessionId())) {
            Log.i(TAG, "Start ns fail.");
        } else {
            Log.i(TAG, "Start ns success.");
        }
        if (!startAGC(record.getAudioSessionId())) {
            Log.i(TAG, "Start agc fail.");
        } else {
            Log.i(TAG, "Start agc success.");
        }

        record.startRecording();

        Log.v(TAG, "Start recording");


        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
//			Log.v(TAG, "read: " + numberRead);
//			Log.v(TAG, "offset: " + recordingOffset);

            int maxLength = recordingBuffer.length;
            int newRecordingOffset = recordingOffset + numberRead;
            int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
            int firstCopyLength = numberRead - secondCopyLength;
            // We store off all the data for the recognition thread to access. The ML
            // thread will copy out of this buffer into its own, while holding the
            // lock, so this should be thread safe.
            recordingBufferLock.lock();
            try {
                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
                System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
                recordingOffset = newRecordingOffset % maxLength;

            } catch (Exception e) {
                shouldContinue = false;
            } finally {
                recordingBufferLock.unlock();
            }
        }

        try {
            record.stop();
            record.release();
        } catch (Exception e) {
            Log.d(TAG,"exception");
        }


    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    recognize();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
        recognitionThread.start();
    }
    public synchronized void stopRecognition() {
        if (recognitionThread == null) {
            return;
        }
        shouldContinueRecognition = false;
        recognitionThread = null;

    }

    private  int argmax(float[] outputScores ) {
        int maxi = 0;
        float max = Integer.MIN_VALUE;

        for (int i = 0;i<outputScores.length;i++) {
            if(outputScores[i]> max) {
                maxi = i;
                max = outputScores[i];
            }
        }
        return maxi;
    }

    private void recognize() throws IOException {
        Log.v(TAG, "Start recognition");

        short[] inputBuffer = new short[RECORDING_LENGTH];
        float[]floatInputBuffer = new float[RECORDING_LENGTH];
        float[]floatInputBuffer_prev = new float[RECORDING_LENGTH];
        float[]floatInputBuffer_prev2 = new float[RECORDING_LENGTH];
        float[] floatInputBuffer_newmodel = new float[RECORDING_LENGTH * 3];


        // Loop, grabbing recorded data and running the recognition model on it.
        while (shouldContinueRecognition) {
            long startTime = new Date().getTime();
            // The recording thread places data in this round-robin buffer, so lock to
            // make sure there's no writing happening and then copy it to our own
            // local version.
            recordingBufferLock.lock();
            try {
                int maxLength = recordingBuffer.length;
                int firstCopyLength = maxLength - recordingOffset;
                int secondCopyLength = recordingOffset;
                System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
                System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
            } finally {
                recordingBufferLock.unlock();
            }


            for (int i = 0; i < RECORDING_LENGTH; ++i) {
                floatInputBuffer[i] =  inputBuffer[i] / 32767.0f;
            }

            for (int i = 0; i < RECORDING_LENGTH ; ++i) {
                floatInputBuffer_newmodel[i ] =  floatInputBuffer_prev2[i];
            }
            for (int i = 0; i < RECORDING_LENGTH ; ++i) {
                floatInputBuffer_newmodel[i + RECORDING_LENGTH] =  floatInputBuffer_prev[i];
            }
            for (int i = 0; i < RECORDING_LENGTH ; ++i) {
                floatInputBuffer_newmodel[i + RECORDING_LENGTH + RECORDING_LENGTH] =  floatInputBuffer[i];
            }


            // Run the model.
            float[] outputScores = new float[12];
            inferenceInterface.feed(INPUT_DATA_NAME, floatInputBuffer, 1,1,16000);
            String[] outputScoresNames = new String[]{OUTPUT_SCORES_NAME};
            inferenceInterface.run(outputScoresNames);
            inferenceInterface.fetch(OUTPUT_SCORES_NAME, outputScores);

            floatInputBuffer_prev2 = floatInputBuffer_prev;
            floatInputBuffer_prev = floatInputBuffer;

            // run env model
            float[] env_outputScores = new float[5];
            String[] env_outputScoresNames = new String[]{ENV_OUTPUT_SCORES_NAME};
            env_inferenceInterface.feed(ENV_INPUT_DATA_NAME, floatInputBuffer_newmodel, 1,1,16000 * 3);
            env_inferenceInterface.run(env_outputScoresNames);
            env_inferenceInterface.fetch(ENV_OUTPUT_SCORES_NAME, env_outputScores);

            // run new model
            float[] outputScores_newmodel = new float[8];
            newmodel_inferenceInterface.feed(INPUT_DATA_NAME_KEYWORD, floatInputBuffer_newmodel, 1,1,16000 * 3);
            String[] outputScoresNames_newmodel = new String[]{OUTPUT_SCORES_NAME_NEWMODEL};
            newmodel_inferenceInterface.run(outputScoresNames_newmodel);
            newmodel_inferenceInterface.fetch(OUTPUT_SCORES_NAME_NEWMODEL, outputScores_newmodel);


            String result = "";
            int r = argmax(outputScores);
            int r_env = argmax(env_outputScores);
            int r_newmodel = argmax(outputScores_newmodel);


            final HashMap<Integer,String> map = new HashMap<Integer, String>();
            map.put(0,"down");
            map.put(1, "go");
            map.put(2, "left");
            map.put(3, "no");
            map.put(4, "off");
            map.put(5, "on");
            map.put(6, "right");
            map.put(7, "silence");
            map.put(8, "stop");
            map.put(9, "unknown");
            map.put(10, "up");
            map.put(11, "yes");

            final  HashMap<Integer,String> env_map = new HashMap<>();
            env_map.put(0,"angry");
            env_map.put(1,"calm");
            env_map.put(2,"fearful");
            env_map.put(3,"happy");

            final  HashMap<Integer,String> newmodel_map  = new HashMap<>();
            newmodel_map.put(0,"bacho");
            newmodel_map.put(1,"background");
            newmodel_map.put(2,"go");
            newmodel_map.put(3,"help");
            newmodel_map.put(4,"no");
            newmodel_map.put(5,"stop");
            newmodel_map.put(6,"unknown");
            newmodel_map.put(7,"yes");

//            if(outputScores_newmodel[r_newmodel] > 0.96) {
//                notification = new NotificationCompat.Builder(this,CHANNEL_ID)
//                        .setContentTitle("Automatic Protection On")
//                        .setContentText("You just said," +  newmodel_map.get(r_newmodel)+ " new: " + env_map.get(r_env) +" with probabilty " + outputScores_newmodel[r_newmodel] )
//                        .setOnlyAlertOnce(true)
//                        .setOngoing(false)
//                        .setTimeoutAfter(0)
//                        .setSmallIcon(R.drawable.ic_add)
//                        .build();
//                NotificationManager mNotificationManager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
//
//                mNotificationManager.notify(1, notification);
//            } else {
//                notification = new NotificationCompat.Builder(this,CHANNEL_ID)
//                        .setContentTitle("Automatic Protection On")
//                        .setContentText("You just said," + "unknown"+ " new: " + env_map.get(r_env) +" with probabilty " + outputScores_newmodel[r_newmodel] )
//                        .setOnlyAlertOnce(true)
//                        .setOngoing(false)
//                        .setTimeoutAfter(0)
//                        .setSmallIcon(R.drawable.ic_add)
//                        .build();
//                NotificationManager mNotificationManager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
//                mNotificationManager.notify(1, notification);
//            }
//

            // send alert on saying stop
            float score = 0.0f;
            float[] weights = {0.6f,0.5f,1.0f};

            if( (outputScores_newmodel[r_newmodel] > 0.96) && (USE_KEYWORD_SPOTTING == true))
                if ( r_newmodel  == 3)
                    score += weights[0];



            if( (env_outputScores[r_env] > 0.8) && (USE_STRESS_DETECTION == true))
                if ( r_env  == 2)
                    score += weights[1];
                Log.d(TAG,score + "");
            if(score >= 0.9f && ongoingPanicEvent == false) {
                Log.d(TAG,"PANIC EVENT DETECTED," + createMessage());
                turnOnAlertSystem();
            }


        }


        try {
            // We don't need to run too frequently, so snooze for a bit.
            Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS);
        } catch (InterruptedException e) {
            // Ignore
        }

    }
//    }

    public void turnOnAlertSystem() {
        ongoingPanicEvent = true;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putString("cancel_event","false").commit();

        alertNotification = new NotificationCompat.Builder(this,ALERT_CHANNEL_ID)
                .setContentTitle("APED Detected Panic Event!!")
                .setContentText("If you are safe, you can stop this alert")
                .setSmallIcon(R.drawable.ic_remove);

        //cancel event intent;
        Intent cancelintent = new Intent("CANCEL_ALERT");
        cancelintent.setClass(this, CancelEventReciever.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(HotWordTriggeringService.this,
                12345, cancelintent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        alertNotification.addAction(R.drawable.ic_remove,"Cancel",pendingIntent);
        alertNotification.setOngoing(true);
        alertNotification.setOnlyAlertOnce(false);
        final NotificationManager mNotificationManager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        mNotificationManager.notify(2, alertNotification.build());

        startForeground(2,alertNotification.build());
        new Thread(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(2000);
                String cancelEvent = null;
                for(int progress = 1; progress <= 30 && (!Thread.currentThread().isInterrupted()); progress+= 1)  {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(HotWordTriggeringService.this);
                    cancelEvent = preferences.getString("cancel_event","false");

                    if(cancelEvent.equals("true")) {
                        Thread.currentThread().interrupt();
                    } else {

                        alertNotification.setOnlyAlertOnce(true);
                        alertNotification.setContentText( (30 - progress) + " seconds remains");
                        alertNotification.setProgress(30,progress,false);
                        mNotificationManager.notify(2, alertNotification.build());
                        SystemClock.sleep(1000);
                    }
                }

                Log.d(TAG,"notification....");

                if( cancelEvent != null && cancelEvent.equals("true")) {
                    NotificationCompat.Builder cancelNotificaion;
                    cancelNotificaion =  new NotificationCompat.Builder(HotWordTriggeringService.this,ALERT_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_add);

                    cancelNotificaion.setContentTitle("lake Alert Cancelled");
                    cancelNotificaion.setOnlyAlertOnce(false);
                    cancelNotificaion.setOngoing(false);
                    cancelNotificaion.setContentText("lake is snoozed for next 1 hour");
                    cancelNotificaion.setProgress(0,0,false);
                    mNotificationManager.notify(2,cancelNotificaion.build());

                } else {
                    Log.d(TAG,"navi notification....");

                    NotificationCompat.Builder sentNotification;
                    sentNotification =  new NotificationCompat.Builder(HotWordTriggeringService.this,ALERT_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_remove);

                    sentNotification.setContentTitle("lake Sent Alert");
                    sentNotification.setOnlyAlertOnce(false);
                    sentNotification.setOngoing(false);
                    sentNotification.setContentText("Your Well Wishers Notified, lake is snoozed for next 1 hour");
                    sentNotification.setProgress(0,0,false);
                    mNotificationManager.notify(2,sentNotification.build());
                }


            }
        }).start();

        // do not send alert for next 1 hour;
        new Thread(new Runnable() {
            @Override
            public void run() {
                SystemClock.sleep(60 * 60 * 1000);
                ongoingPanicEvent = false;
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    public class MyBinder extends Binder {
        public HotWordTriggeringService getService() {
            return HotWordTriggeringService.this;
        }
    }

    @Override
    public void onDestroy() {

        super.onDestroy();

        final NotificationManager mNotificationManager = (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
        mNotificationManager.cancel(1);

        stopRecording();
        stopRecognition();
        try {
            record.stop();
            record.release();
        } catch (Exception e) {
            Log.d(TAG," failed to release...");
        }


        stopAEC();
        stopAGC();
        stopNS();
        stopForeground(false);
    }



    private void stopRecording() {
        if( recordingThread == null) return;
        shouldContinue = false;
    }
    private boolean startAEC(int audioSessionId) {
        if (!AcousticEchoCanceler.isAvailable()) {
            Log.w(TAG, "This device does not support AEC.");
            return false;
        }

        echoCanceler = AcousticEchoCanceler.create(audioSessionId);
        if (echoCanceler == null) {
            Log.w(TAG, "This device does not implement AEC.");
            return false;
        }
        echoCanceler.setEnabled(true);

        return echoCanceler.getEnabled();
    }

    private void stopAEC() {
        if (echoCanceler != null) {
            echoCanceler.release();
            echoCanceler = null;
        }
    }

    private boolean startNS(int audioSessionId) {
        if (!NoiseSuppressor.isAvailable()) {
            Log.w(TAG, "This device does support NS.");
            return false;
        }

        noiseSuppressor = NoiseSuppressor.create(audioSessionId);
        if (noiseSuppressor == null) {
            Log.w(TAG, "This device does not implement NS.");
            return false;
        }
        noiseSuppressor.setEnabled(true);

        return noiseSuppressor.getEnabled();
    }

    private void stopNS() {
        if (noiseSuppressor != null) {
            noiseSuppressor.release();
            noiseSuppressor = null;
        }
    }

    private boolean startAGC(int audioSessionId) {
        if (!automaticGainControl.isAvailable()) {
            Log.w(TAG, "This device does not support AGC.");
            return false;
        }

        automaticGainControl = AutomaticGainControl.create(audioSessionId);
        if (automaticGainControl == null) {
            Log.w(TAG, "This device does not implement AGC.");
            return false;
        }
        automaticGainControl.setEnabled(true);

        return automaticGainControl.getEnabled();
    }

    private void stopAGC() {
        if (automaticGainControl != null) {
            automaticGainControl.release();
            automaticGainControl = null;
        }
    }


    @Override
    public void onLocationChanged(Location location) {
        if (location != null)
            currentlocation = location;
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
    public String getCurrentTimeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    }
    public String createMessage() {

        String message = "I need your help, I might be in danger my location: ";
        if (currentlocation != null && gps_setting) message +=  "https://www.google.com/maps/search/?api=1&query="+ currentlocation.getLatitude() + "," + currentlocation.getLongitude() + " ";
        else message += "not available";

        if (phonenumber != null) message += ", First you can try calling me at : " + phonenumber;
        else message +="";
        message += " time :" + getCurrentTimeStamp();
        return message;
    }

    public String createlink() {
        String link = "";
        if (currentlocation != null && gps_setting != false) link +=  "https://www.google.com/maps/search/?api=1&query="+ currentlocation.getLatitude() + "," + currentlocation.getLongitude() + " ";
        return link;
    }


    private void startLocationUpdates() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(5000);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                &&  ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "You need to enable permissions to display location !", Toast.LENGTH_SHORT).show();
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, HotWordTriggeringService.this);
    }

}