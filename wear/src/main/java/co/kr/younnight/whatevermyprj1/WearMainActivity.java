package co.kr.younnight.whatevermyprj1;

import android.app.Activity;
import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class WearMainActivity extends Activity implements SensorEventListener, DataApi.DataListener,
        MessageApi.MessageListener, NodeApi.NodeListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    private TextView mTextView;
    private String TAG = "WearMainActivity";

    private static final int REQUEST_RESOLVE_ERROR = 1000;
    private GoogleApiClient mGoogleApiClient;
    private boolean mResolvingError = false;

    private String dbgSensorDataStr = "/test-sensor-data";

    private SensorManager manager;
    private Sensor accel;

    private int MAX_DATA_SIZE = 20;
    private float xValues[] = new float[MAX_DATA_SIZE];
    private float yValues[] = new float[MAX_DATA_SIZE];
    private float zValues[] = new float[MAX_DATA_SIZE];
    private int xyzValuesIdx = 0;
    private long mDataAddedTime = 0;

    private int xPositiveGesture;// 0 : on state, 1 : possible state, 2 : confirmed.
    private int yPositiveGesture;
    private int zPositiveGesture;
    private int xNegativeGesture;
    private int yNegativeGesture;
    private int zNegativeGesture;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_wear_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        manager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accel = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float xValue = event.values[0];
        float yValue = event.values[1];
        float zValue = event.values[2];
        byte gestureResult[] = new byte[6];

        if(Math.abs(xValue) > 10 || Math.abs(yValue) > 10 || Math.abs(zValue) > 10){
            long currentTime = System.currentTimeMillis();
            if(mDataAddedTime != 0 && (currentTime - mDataAddedTime > 1000)) {
                //init data array...
                xyzValuesIdx = 0;

                xPositiveGesture = 0;
                xNegativeGesture = 0;
                yPositiveGesture = 0;
                yNegativeGesture = 0;
                zPositiveGesture = 0;
                zNegativeGesture = 0;
                gestureResult = new byte[6];
            }

            mDataAddedTime = currentTime;

            //add data to array.
            xValues[xyzValuesIdx] = xValue;
            yValues[xyzValuesIdx] = yValue;
            zValues[xyzValuesIdx++] = zValue;
            if(xyzValuesIdx == MAX_DATA_SIZE) {
                //not expected.
                Log.e(TAG, "onSensorChanged: data overflow...............................");
                xyzValuesIdx--;
            }

            //analyze data.
            if(xValue < -10 && xPositiveGesture == 0){
                xNegativeGesture = 1;
            }
            if(yValue < -10 && yPositiveGesture == 0){
                yNegativeGesture = 1;
            }
            if(zValue < -10 && zPositiveGesture == 0){
                zNegativeGesture = 1;
            }
            if(xValue > 10 && xNegativeGesture == 1){
                xNegativeGesture = 2;
                gestureResult[0] = 1;
            }
            if(yValue > 10 && yNegativeGesture == 1){
                yNegativeGesture = 2;
                gestureResult[1] = 1;
            }
            if(zValue > 10 && zNegativeGesture == 1){
                zNegativeGesture = 2;
                gestureResult[2] = 1;
            }

            if(xValue > 10 && xNegativeGesture == 0){
                xPositiveGesture = 1;
            }
            if(yValue > 10 && yNegativeGesture == 0){
                yPositiveGesture = 1;
            }
            if(zValue > 10 && zNegativeGesture == 0){
                zPositiveGesture = 1;
            }
            if(xValue < -10 && xPositiveGesture == 1){
                xPositiveGesture = 2;
                gestureResult[3] = 1;
            }
            if(yValue < -10 && yPositiveGesture == 1){
                yPositiveGesture = 2;
                gestureResult[4] = 1;
            }
            if(zValue < -10 && zPositiveGesture == 1){
                zPositiveGesture = 2;
                gestureResult[5] = 1;
            }

            if(xPositiveGesture==2 || yPositiveGesture==2 || zPositiveGesture==2 || xNegativeGesture==2 || yNegativeGesture==2 || zNegativeGesture==2) {
                new SendSensorDataTask(gestureResult).execute();
            }

            final String str = xValue + " " + yValue + " " + zValue;

            Log.e(TAG, "onSensorChanged:" + str);
        }
    }

    private void sendSensorDataMessage(String node, byte[] data) {
        Wearable.MessageApi.sendMessage(
                mGoogleApiClient, node, dbgSensorDataStr, data).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send message with status code: "
                                    + sendMessageResult.getStatus().getStatusCode());
                        }
                    }
                }
        );
    }

    private class SendSensorDataTask extends AsyncTask<Void, Void, Void> {
        byte[] mData;
        SendSensorDataTask(byte[] data){
            mData = data;
        }

        @Override
        protected Void doInBackground(Void... args) {
            Collection<String> nodes = getNodes();
            for (String node : nodes) {
                sendSensorDataMessage(node, mData);
            }
            return null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        manager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onPause() {
        super.onPause();
        manager.unregisterListener(this);
    }

    @Override
    protected void onStop() {
        if (!mResolvingError) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }
        return results;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.e(TAG, "Google API Client was connected");
        mResolvingError = false;
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.NodeApi.addListener(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "Connection to Google API client was suspended");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

    }

    @Override
    public void onPeerConnected(Node node) {

    }

    @Override
    public void onPeerDisconnected(Node node) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            Log.e(TAG, "Connection to Google API client has failed");
            mResolvingError = false;
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
        }
    }
}
