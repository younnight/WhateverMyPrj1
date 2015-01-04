package co.kr.younnight.whatevermyprj1;

import android.app.Activity;
import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

public class WearMainActivity extends Activity implements SensorEventListener, DataApi.DataListener,
        MessageApi.MessageListener, NodeApi.NodeListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    private TextView mTextView;
    private String TAG = "WearMainActivity";

    private static final int REQUEST_RESOLVE_ERROR = 1000;
    private GoogleApiClient mGoogleApiClient;
    private boolean mResolvingError = false;

    private String dbgSensorDataStr = "/test-sensor-data";

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
	private Sensor mRotaionSensor;

    private final int mAccelGestureThreshold = 10;

    private int MAX_DATA_SIZE = 30;
    private float xAccelValues[] = new float[MAX_DATA_SIZE];
    private float yAccelValues[] = new float[MAX_DATA_SIZE];
    private float zAccelValues[] = new float[MAX_DATA_SIZE];
	private float xRotaionValues[] = new float[MAX_DATA_SIZE];
    private float yRotaionValues[] = new float[MAX_DATA_SIZE];
    private float zRotaionValues[] = new float[MAX_DATA_SIZE];
    private int xyzAccelValuesIdx = 0;
	private int xyzRotaionValuesIdx = 0;
    private long mDataAddedTime = 0;

    private int xPositiveGesture;// 0 : on state, 1 : possible state, 2 : confirmed.
    private int yPositiveGesture;
    private int zPositiveGesture;
    private int xNegativeGesture;
    private int yNegativeGesture;
    private int zNegativeGesture;

    private int mTrashDataCount = 0;

    byte gestureResult[] = new byte[6];

	int fileNameIdx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
	    setContentView(R.layout.activity_wear_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//######################  must be called after setContentView..
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.mainText);
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
	    mRotaionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    private void clearDataInfo(){
        //init data array...
        xyzAccelValuesIdx = 0;
	    xyzRotaionValuesIdx = 0;

        xPositiveGesture = 0;
        xNegativeGesture = 0;
        yPositiveGesture = 0;
        yNegativeGesture = 0;
        zPositiveGesture = 0;
        zNegativeGesture = 0;
        gestureResult = new byte[6];
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float xValue = event.values[0];
        float yValue = event.values[1];
        float zValue = event.values[2];

        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
	        Log.e(TAG, "##AccelSensor: " + xValue + "," + yValue + "," + zValue);

	        if (Math.abs(xValue) > mAccelGestureThreshold || Math.abs(yValue) > mAccelGestureThreshold || Math.abs(zValue) > mAccelGestureThreshold) {
		        Log.e(TAG, "log counted as index[" + xyzAccelValuesIdx + "]");
		        mTrashDataCount = 0;

		        long currentTime = System.currentTimeMillis();
		        if (mDataAddedTime != 0 && (currentTime - mDataAddedTime > 1000)) {
			        clearDataInfo();
		        }

		        mDataAddedTime = currentTime;

		        //add data to array.
		        xAccelValues[xyzAccelValuesIdx] = xValue;
		        yAccelValues[xyzAccelValuesIdx] = yValue;
		        zAccelValues[xyzAccelValuesIdx++] = zValue;
		        if (xyzAccelValuesIdx == MAX_DATA_SIZE) {
			        //not expected.
			        Log.e(TAG, "onSensorChanged: data overflow...............................");
			        xyzAccelValuesIdx--;
		        }

		        //analyze data.
		        if (xValue < -mAccelGestureThreshold && xPositiveGesture == 0 && xNegativeGesture != 2) {
			        xNegativeGesture = 1;
			        Log.e(TAG, "xNegativeGesture = 1");
		        }
		        if (yValue < -mAccelGestureThreshold && yPositiveGesture == 0 && yNegativeGesture != 2) {
			        yNegativeGesture = 1;
			        Log.e(TAG, "yNegativeGesture = 1");
		        }
		        if (zValue < -mAccelGestureThreshold && zPositiveGesture == 0 && zNegativeGesture != 2) {
			        zNegativeGesture = 1;
			        Log.e(TAG, "zNegativeGesture = 1");
		        }
		        if (xValue > mAccelGestureThreshold && xNegativeGesture == 1) {
			        xNegativeGesture = 2;
			        gestureResult[0] = 1;
			        Log.e(TAG, "xNegativeGesture = 2");
		        }
		        if (yValue > mAccelGestureThreshold && yNegativeGesture == 1) {
			        yNegativeGesture = 2;
			        gestureResult[1] = 1;
			        Log.e(TAG, "yNegativeGesture = 2");
		        }
		        if (zValue > mAccelGestureThreshold && zNegativeGesture == 1) {
			        zNegativeGesture = 2;
			        gestureResult[2] = 1;
			        Log.e(TAG, "zNegativeGesture = 2");
		        }

		        if (xValue > mAccelGestureThreshold && xNegativeGesture == 0 && xPositiveGesture != 2) {
			        xPositiveGesture = 1;
			        Log.e(TAG, "xPositiveGesture = 1");
		        }
		        if (yValue > mAccelGestureThreshold && yNegativeGesture == 0 && yPositiveGesture != 2) {
			        yPositiveGesture = 1;
			        Log.e(TAG, "yPositiveGesture = 1");
		        }
		        if (zValue > mAccelGestureThreshold && zNegativeGesture == 0 && zPositiveGesture != 2) {
			        zPositiveGesture = 1;
			        Log.e(TAG, "zPositiveGesture = 1");
		        }
		        if (xValue < -mAccelGestureThreshold && xPositiveGesture == 1) {
			        xPositiveGesture = 2;
			        gestureResult[3] = 1;
			        Log.e(TAG, "xPositiveGesture = 2");
		        }
		        if (yValue < -mAccelGestureThreshold && yPositiveGesture == 1) {
			        yPositiveGesture = 2;
			        gestureResult[4] = 1;
			        Log.e(TAG, "yPositiveGesture = 2");
		        }
		        if (zValue < -mAccelGestureThreshold && zPositiveGesture == 1) {
			        zPositiveGesture = 2;
			        gestureResult[5] = 1;
			        Log.e(TAG, "zPositiveGesture = 2");
                }
            } else {
                mTrashDataCount++;
                if (mTrashDataCount == 10) { //check only 10 trash data counted..
	                Log.e(TAG, "trash data............. init data");
	                if (xPositiveGesture == 2 || yPositiveGesture == 2 || zPositiveGesture == 2 || xNegativeGesture == 2 || yNegativeGesture == 2 || zNegativeGesture == 2) {

		                //write to file.
		                try {
			                String directory = "/sdcard/prj/";
			                directory = Environment.getExternalStorageDirectory().toString() + "/prj/";
			                File file = new File(directory);
			                if(!file.exists()){
				               if(file.mkdir()){
					               Log.e(TAG, "created");
				               }
				               else{
					               Log.e(TAG, "NOT created");
				               }
			                }
			                file = new File(directory + "tmp" + fileNameIdx + ".txt");
			                if(!file.exists()) {
				                file.createNewFile();
			                }
			                FileWriter fw = new FileWriter(file);
			                for(int i = 0; i < xyzRotaionValuesIdx; i++){
				                fw.write(i + "\t" + xAccelValues[i] + "\t" + yAccelValues[i] + "\t" + zAccelValues[i] + "\t" +
						                xRotaionValues[i] + "\t" + yRotaionValues[i] + "\t" + zRotaionValues[i] + "\n");
			                }
			                fw.close();

							mTextView.setText("write file [" + fileNameIdx + "]");
			                fileNameIdx++;
		                } catch (IOException e) {
			                e.printStackTrace();
		                }

                    }
	                clearDataInfo();
                }
            }
        }
	    else if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
	        if(xyzRotaionValuesIdx < xyzAccelValuesIdx){
		        xRotaionValues[xyzRotaionValuesIdx] = xValue;
		        yRotaionValues[xyzRotaionValuesIdx] = yValue;
		        zRotaionValues[xyzRotaionValuesIdx++] = zValue;
		        //Log.e(TAG, "rotaionSensor: " + xValue + "," + yValue + "," + zValue);
	        }
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
	    //TODO make android wear device wake up when it is under suspend state.
        super.onResume();
	    Log.e(TAG, "onResume #############################################");
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
	    mSensorManager.registerListener(this, mRotaionSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onPause() {
        super.onPause();
	    Log.e(TAG, "onPause #############################################");
        mSensorManager.unregisterListener(this);
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
