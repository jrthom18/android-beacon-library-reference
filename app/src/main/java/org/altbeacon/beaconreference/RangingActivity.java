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
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
    private BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);
    private Collection<Beacon> uniqueBeacons = new ArrayList<>();
    private Collection<Beacon> newestBeacons = new ArrayList<>();
    private boolean newBeaconsFound = false;
    private Map<String,Object> params = new LinkedHashMap<>();
    private Handler mHandler = null;
    private String searchText = "";
    private ArrayList<DisplayBeacon> displayBeaconArray = new ArrayList<DisplayBeacon>() {};
    private ArrayAdapter<DisplayBeacon> adapter;
    private ProgressDialog progDialog;
    private LinearLayout myGallery;
    private TextView textView2;
    private TextView textView4;
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
        beaconManager.bind(this);
        Toast.makeText(getApplicationContext(), "Loading...", Toast.LENGTH_SHORT).show();
        myGallery = (LinearLayout)findViewById(R.id.mygallery);
        textView2 = (TextView)findViewById(R.id.textView2);
        textView4 = (TextView)findViewById(R.id.textView4);
        myGallery.setVisibility(View.INVISIBLE);
        textView2.setVisibility(View.INVISIBLE);
        textView4.setVisibility(View.INVISIBLE);

        mHandler = new Handler();

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

        // Stage links to boots images
        ImageView img = (ImageView)findViewById(R.id.imageView1);
        img.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("http://www.peterglenn.com/product/north-face-hedgehog-fastpack-mid-gore-tex-boot-mens?utm_source=GooglePLA&utm_medium=PPC&utm_campaign=Shopping+Feeds_THE+NORTH+FACE+INC"));
                startActivity(intent);
            }
        });
        ImageView img2 = (ImageView)findViewById(R.id.imageView2);
        img2.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("http://www.backcountry.com/the-north-face-hedgehog-fastpack-mid-gtx-hiking-boot-mens?CMP_SKU=TNF01VT&MER=0406&skid=TNF01VT-TNBLSHGR-S7&CMP_ID=PLA_GOc001&mv_pc=r101&utm_source=Google&utm_medium=PLA&mr:trackingCode=D957B163-E346-E611-80F4-005056944E17&mr:referralID=NA&mr:device=c&mr:adType=plaonline&gclid=Cj0KEQjwztG8BRCJgseTvZLctr8BEiQAA_kBD7y4GStIJHwT4qv3r6BgIsoG2_Ra74NGFngfYG1uu0kaAupY8P8HAQ&gclsrc=aw.ds"));
                startActivity(intent);
            }
        });
        ImageView img3 = (ImageView)findViewById(R.id.imageView3);
        img3.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("http://www.zappos.com/the-north-face-hedgehog-fastpack-mid-gtx-shroom-brown-brushfire-orange?ef_id=V2HVZwAAAbLEuj0j:20160725043433:s"));
                startActivity(intent);
            }
        });
        ImageView img4 = (ImageView)findViewById(R.id.imageView4);
        img4.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("https://www.thenorthface.com/shop/mens-hedgehog-hike-mid-gore-tex?from=subCat&variationId=APS&cm_mmc=Google-_-ProductListingAds-_-ProductTerms-_-The+North+Face+Men+s+Hedgehog+Hike+Mid+Gore-TEX+Waterproof+Boots+10+&dvid=c&aptid=123738190110&adtid=pla&crtID=54075327750&pdtid=CDF5APS100&pptid=123738190110&dvid=c&aptid=123738190110&adtid=pla&crtID=54075327750&pdtid=CDF5APS100&pptid=123738190110&lsft=dvid:c,aptid:123738190110,adtid:pla,crtID:54075327750,pdtid:CDF5APS100,pptid:123738190110&gclid=Cj0KEQjwztG8BRCJgseTvZLctr8BEiQAA_kBD8EzYDuWrmBiU4pQ_pewaLLvsEiszwO8dACA8lCmSgEaAvIV8P8HAQ"));
                startActivity(intent);
            }
        });
        ImageView img5 = (ImageView)findViewById(R.id.imageView5);
        img5.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("http://www.mountaingear.com/webstore//Footwear/Hedgehog-Hike-Mid-GTX-Boot-Men-s/_/R-243915.htm?voucherCode=979100&id=243915gie__11&gclid=Cj0KEQjwztG8BRCJgseTvZLctr8BEiQAA_kBD0wy_Rr0k2NK2lgzeipEms9vblZ_u4FdpYFIM36BlFYaAiA78P8HAQ&ad=93106472295"));
                startActivity(intent);
            }
        });

        Button refreshButton = (Button)findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // TODO: Rescan for beacons and update display
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

               displayBeaconsFound();
               runOnUiThread(new Runnable() {
                   @Override
                   public void run() {
                       myGallery.setVisibility(View.VISIBLE);
                       textView2.setVisibility(View.VISIBLE);
                       textView4.setVisibility(View.VISIBLE);
                   }
               });
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

        Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

        JSONReader jsonReader = new JSONReader();
        displayBeaconArray.clear();
        displayBeaconArray.addAll(jsonReader.readJsonStream(conn.getInputStream()));


        for (int c; (c = in.read()) >= 0;) {
            System.out.print((char) c);
        }

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
                TextView editText = (TextView)RangingActivity.this.findViewById(R.id.textView);
                editText.setText(line);
            }
        });
    }

    private void clearDisplayText() {
        runOnUiThread(new Runnable() {
            public void run() {
                TextView editText = (TextView)RangingActivity.this.findViewById(R.id.textView);
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
        logToDisplay("speak now...");
        setButtonLabel(R.id.buttonRecord, "Go");
        mState = ConnectionState.CONNECTED;
    }

    public void onError(String error) {
        //logToDisplay(error);
        mState = ConnectionState.IDLE;
    }

    public void onClose(int code, String reason, boolean remote) {
        //logToDisplay("connection closed");
        setButtonLabel(R.id.buttonRecord, "Search");
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
