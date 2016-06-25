package org.altbeacon.beaconreference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.app.LoaderManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Spinner;

import com.ibm.watson.developer_cloud.android.speech_to_text.v1.ISpeechDelegate;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.dto.SpeechConfiguration;
import com.ibm.watson.developer_cloud.android.text_to_speech.v1.TextToSpeech;

import org.altbeacon.beacon.AltBeacon;
import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

public class RangingActivity extends ListActivity implements BeaconConsumer, ISpeechDelegate,
        LoaderManager.LoaderCallbacks<Cursor> {

    protected static final String TAG = "RangingActivity";
    private static String url_create_product = "http://159.203.254.18/android/create_product.php";
    private static String url_voice_search = "http://159.203.254.18/android/voice_search.php";
    private BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);
    private Collection<Beacon> uniqueBeacons = new ArrayList<>();
    private Collection<Beacon> newestBeacons = new ArrayList<>();
    private boolean newBeaconsFound = false;
    private Map<String,Object> params = new LinkedHashMap<>();
    private Handler mHandler = null;
    private String searchText = "";
    private ProgressDialog pDialog;
    private ArrayList<DisplayBeacon> displayBeaconArray = new ArrayList<DisplayBeacon>() {};
    private ArrayAdapter<DisplayBeacon> adapter;

    private int inc = 1;

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    private enum ConnectionState {
        IDLE, CONNECTING, CONNECTED
    }

    ConnectionState mState = ConnectionState.IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranging);
        mHandler = new Handler();
        beaconManager.bind(this);

        adapter = new ArrayAdapter<DisplayBeacon>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, displayBeaconArray);
        ListView listView = (ListView) findViewById(android.R.id.list);
        listView.setAdapter(adapter);

        try {
            if (initSTT() == false) {
                //logToDisplay("Error: no authentication credentials/token available, please enter your authentication information");
            }
            else{
                //logToDisplay("Valid auth");
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Button buttonRecord = (Button)findViewById(R.id.buttonRecord);
        buttonRecord.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (mState == ConnectionState.IDLE) {
                    mState = ConnectionState.CONNECTING;
                    // start recognition
                    new AsyncTask<Void, Void, Void>(){
                        @Override
                        protected Void doInBackground(Void... none) {
                            SpeechToText.sharedInstance().recognize();
                            return null;
                        }
                    }.execute();
                    setButtonLabel(R.id.buttonRecord, "Connecting...");
                }
                else if (mState == ConnectionState.CONNECTED) {
                    mState = ConnectionState.IDLE;
                    SpeechToText.sharedInstance().stopRecognition();
                    // TODO: Send text to cloud to initiate search
                    new AsyncTask<Void, Void, Void>(){
                        @Override
                        protected Void doInBackground(Void... none) {
                            try {
                                makeVoiceSearch(searchText);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                    }.execute();
                }
            }
        });
    }

    private void makeVoiceSearch(String searchText) throws IOException {
        ArrayList<DisplayBeacon> matchingBeacons = new ArrayList<>();

        for(DisplayBeacon beacon : displayBeaconArray){
            if (beacon.getTitle().toLowerCase().contains(searchText.toLowerCase()) ||
                    beacon.getDescription().toLowerCase().contains(searchText.toLowerCase())){
                matchingBeacons.add(beacon);
            }
        }
        displayBeaconArray.clear();
        displayBeaconArray.addAll(matchingBeacons);
        displayBeaconsFound();
    }

    @Override 
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override 
    protected void onPause() {
        super.onPause();
        if (beaconManager.isBound(this)) beaconManager.setBackgroundMode(true);
    }

    @Override 
    protected void onResume() {
        super.onResume();
        if (beaconManager.isBound(this)) beaconManager.setBackgroundMode(false);
    }

    @Override
    public void onBeaconServiceConnect() {

        beaconManager.setRangeNotifier(new RangeNotifier() {
           @Override
           public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
               for (Beacon beacon: beacons) {
                   // Assure this is an Eddystone-URL frame
                   if (beacon.getServiceUuid() == 0xfeaa && beacon.getBeaconTypeCode() == 0x10) {
                       if (!uniqueBeacons.contains(beacon)){
                           uniqueBeacons.add(beacon);
                           newestBeacons.add(beacon);
                           newBeaconsFound = true;
                       }
                   }
               }
               /*
                    This code will only send beacon data to database one time.
                    If you walk in range of a beacon, out of range of that beacon, and then
                    back in range of that same beacon later on, the database will only be updated
                    for the initial contact with the beacon (assuming the app was not forced to quit
                    and then restarted in that time period).
                    We probably want to update the database every time the beacon is seen in the
                    background?
                */
               if (newBeaconsFound){
                   Location loc = locateUser();
                   for (Beacon beacon: newestBeacons) {

                       String url = UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray());
                       //sendNotification("Beacon nearby: " + url);
                       // Send beacon data to cloud
                       // TODO: Pass Facebook ID into first data parameter

                       List<Object> data = new ArrayList<>();
                       data.add(1);
                       data.add(url);
                       data.add(loc.getLatitude());
                       data.add(loc.getLongitude());
                       data.add(System.currentTimeMillis()/1000); // Current timestamp in seconds
                       data.add(beacon.getDistance());
                       params.put("Beacon"+inc, data);
                       inc++;
                   }
                   try {
                       updateDatabase();
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
                   newBeaconsFound = false;
                   newestBeacons.clear();
               }
               // This will display all beacons found in the initial range scan, does not update
               // distance estimates in real time on the UI
               displayBeaconsFound();
           }
        });
        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null,
                    null, null));
        } catch (RemoteException e) {   }
    }

    private void displayBeaconsFound() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void updateDatabase() throws IOException {
        URL postUrl = new URL(url_create_product);

        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String,Object> param : params.entrySet()) {
            if (postData.length() != 0) postData.append('&');
            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            postData.append('=');
            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
        }
        byte[] postDataBytes = postData.toString().getBytes("UTF-8");

        HttpURLConnection conn = (HttpURLConnection)postUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
        conn.setDoOutput(true);
        conn.getOutputStream().write(postDataBytes);

        //Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

        // TODO: Read in JSON
        JSONReader jsonReader = new JSONReader();
        displayBeaconArray.clear();
        displayBeaconArray.addAll(jsonReader.readJsonStream(conn.getInputStream()));
        //displayBeaconArray = jsonReader.readJsonStream(conn.getInputStream());

        /*for (int c; (c = in.read()) >= 0;) {
            System.out.print((char) c);
            char ch = ((char) c);
        }*/

        params.clear();
    }


    private void sendNotification(String contentText) {
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setContentTitle("Beacon Reference Application")
                        .setContentText(contentText)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setVibrate(new long[] {0, 1000, 1000, 1000, 1000})
                        .setSound(alarmSound);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntent(new Intent(this, MonitoringActivity.class));
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        builder.setContentIntent(resultPendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
    }

    private Location locateUser() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new MyLocationListener();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return null;
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                0, 0, locationListener);

        Location currentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        locationManager.removeUpdates(locationListener);
        return currentLocation;
    }

    private void logToDisplay(final String line) {
        runOnUiThread(new Runnable() {
            public void run() {
                EditText editText = (EditText)RangingActivity.this.findViewById(R.id.rangingText);
                editText.append(line+"\n");
            }
        });
    }

    private void clearDisplayText() {
        runOnUiThread(new Runnable() {
            public void run() {
                EditText editText = (EditText)RangingActivity.this.findViewById(R.id.rangingText);
                editText.setText("");
            }
        });
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        DisplayBeacon item = displayBeaconArray.get(position);
        String url = item.getUrl();
        Uri uri = Uri.parse(url);
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    public void onOpen() {
        logToDisplay("Speak now...");
        setButtonLabel(R.id.buttonRecord, "GO");
        mState = ConnectionState.CONNECTED;
    }

    public void onError(String error) {
        //logToDisplay(error);
        mState = ConnectionState.IDLE;
    }

    public void onClose(int code, String reason, boolean remote) {
        //logToDisplay("connection closed");
        setButtonLabel(R.id.buttonRecord, "VOICE SEARCH");
        mState = ConnectionState.IDLE;
    }

    public void onMessage(String message) {

        Log.d(TAG, "onMessage, message: " + message);
        try {
            JSONObject jObj = new JSONObject(message);
            // state message
            if(jObj.has("state")) {
                Log.d(TAG, "Status message: " + jObj.getString("state"));
            }
            // results message
            else if (jObj.has("results")) {
                //if has result
                Log.d(TAG, "Results message: ");
                JSONArray jArr = jObj.getJSONArray("results");
                for (int i=0; i < jArr.length(); i++) {
                    JSONObject obj = jArr.getJSONObject(i);
                    JSONArray jArr1 = obj.getJSONArray("alternatives");
                    String str = jArr1.getJSONObject(0).getString("transcript");
                    clearDisplayText();
                    logToDisplay(str);
                    searchText = str;
                    break;
                }
            } else {
                logToDisplay("unexpected data coming from stt server: \n" + message);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON");
            e.printStackTrace();
        }
    }

    @Override
    public void onAmplitude(double amplitude, double volume) {

    }

    // initialize the connection to the Watson STT service
    private boolean initSTT() throws URISyntaxException {

        // DISCLAIMER: please enter your credentials or token factory in the lines below
        String username = "23e41665-a035-41fd-85c9-806beab12796";
        String password = "8FTmGDEMNRfo";

        String tokenFactoryURL = getString(R.string.STTdefaultTokenFactory);
        String serviceURL = "wss://stream.watsonplatform.net/speech-to-text/api";

        SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_OGGOPUS);
        //SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_DEFAULT);

        SpeechToText.sharedInstance().initWithContext(new URI("wss://stream.watsonplatform.net/speech-to-text/api"),
                this.getApplicationContext(), sConfig);


        SpeechToText.sharedInstance().setCredentials(username, password);
        SpeechToText.sharedInstance().setModel("en-US_BroadbandModel");
        SpeechToText.sharedInstance().setDelegate(this);

        return true;
    }

    /**
     * Change the button's label
     */
    public void setButtonLabel(final int buttonId, final String label) {
        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                Button button = (Button)findViewById(buttonId);
                button.setText(label);
            }
        };
        new Thread(){
            public void run(){
                mHandler.post(runnableUi);
            }
        }.start();
    }


}
