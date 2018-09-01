package com.goteam.win7.rail_monitoring;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    ImageButton ib_change_map;

    public static GoogleMap g_map = null;
    CameraPosition cameraPosition = null;
    Marker marker = null;
    Circle circle = null;
    LatLng lat_lng;
    Location mLastLocation = null;
    public static Marker marker_ext = null;
    public static Map<String, Marker> list_marker = new HashMap< String, Marker>();

    boolean flag_init = true;
    boolean flag_monitoring = true;
    int lapse = 5; /* Second */
    int count = 0;
    int type_map = 1;

    public static GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    final static int REQUEST_LOCATION = 199;
    final static int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    final static int UPDATE_INTERVAL = 5 * 1000;
    final static int FASTEST_UPDATE_INTERVAL = UPDATE_INTERVAL / 2;
    final static int SMALLEST_DISPLACEMENT = 5;

    public static boolean FLAG_PERMISSIONS_GPS = false;
    public static boolean FLAG_ENABLE_GPS = false;

    private UsbService usbService;
    private MyHandler mHandler;

    IntentFilter ifilter;
    Intent batteryStatus;

    public static String TAG = "XXX";
    RefreshMonitoring refresh_monitoring = null;

    String ID = "1";
    double latitude = 0;
    double longitude = 0;
    int velocity = 0;
    String time = "00:00:00";
    int navigation = 0;

    DateFormat format_time = new SimpleDateFormat("HH:mm:ss");
    Date date = new Date();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = this.registerReceiver(null, ifilter);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        /* FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // usbService.changeBaudRate(9600);
            }
        }); */

        final Animation animation_a = AnimationUtils.loadAnimation(getBaseContext(), R.anim.alpha);
        ib_change_map = findViewById(R.id.imageButton_change_map);
        ib_change_map.setAnimation(animation_a);
        ib_change_map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.startAnimation(animation_a);
                type_map++;
                switch (type_map) {
                    case 1:
                        g_map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                        break;
                    case 2:
                        g_map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                        break;
                    case 3:
                        g_map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                        break;
                    case 4:
                        g_map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                        type_map = 0;
                        break;
                }
            }
        });

        PermissionsGPS();
        mHandler = new MyHandler(this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.e(TAG, "onStart");
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection, null); // Start UsbService(if it was not started before) and Bind it
    }

    @Override
    public void onRestart() {
        super.onRestart();
        Log.e(TAG, "onRestart");
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.e(TAG, "onStop");
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
        if (mGoogleApiClient != null){
            if (mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
        }
        flag_monitoring = false;
    }

    @Override
    public void onBackPressed() {
        Log.e(TAG, "onBackPressed");
    }

    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.e(TAG, "OnConnected");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        // mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL);
        // mLocationRequest.setSmallestDisplacement(1);

        //LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates state = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
                        FLAG_ENABLE_GPS = true;
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(MainActivity.this, REQUEST_LOCATION);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        break;
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.e(TAG, "onMapReady");
        g_map = googleMap;
        try {
            // g_map.getUiSettings().setZoomControlsEnabled(true);
            g_map.getUiSettings().setCompassEnabled(true);
            g_map.getUiSettings().setRotateGesturesEnabled(true);
            g_map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            }
            g_map.setMyLocationEnabled(true);
            // g_map.setPadding(0, 120, 0, 120);

            refresh_monitoring = new RefreshMonitoring();
            refresh_monitoring.execute();
        } catch (Exception e) {
            Log.e(TAG, e.getCause().getLocalizedMessage());
        }

        /* LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney)); */
    }

    /*********************************************/
    /***************** CONFIG GPS ****************/
    /*********************************************/
    private void PermissionsGPS() {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_PERMISSIONS_REQUEST_CODE);
    }

    protected synchronized void BuildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.e(TAG, "User interaction was cancelled.");
                FLAG_PERMISSIONS_GPS = false;
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission granted, updates requested, starting location updates");
                FLAG_PERMISSIONS_GPS = true;
                BuildGoogleApiClient();
            } else {                try {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                }
                g_map.setMyLocationEnabled(true);
            } catch (Exception e) {

            }
                // Permission denied.
                FLAG_PERMISSIONS_GPS = false;
                ShowSnackbar(R.string.permission_gps,
                        android.R.string.ok, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Request permission
                                PermissionsGPS();
                            }
                        });
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_LOCATION:
                switch (resultCode) {
                    case Activity.RESULT_OK: {
                        // All required changes were successfully made
                        Toast.makeText(MainActivity.this, "Localización habilitada...", Toast.LENGTH_LONG).show();
                        FLAG_ENABLE_GPS = true;
                        break;
                    }
                    case Activity.RESULT_CANCELED: {
                        // The user was asked to change settings, but chose not to
                        Toast.makeText(MainActivity.this, "Localización deshabilitada...", Toast.LENGTH_LONG).show();
                        FLAG_ENABLE_GPS = false;
                        ShowSnackbar(R.string.enable_gps,
                                android.R.string.ok, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        // Request permission
                                        BuildGoogleApiClient();
                                    }
                                });
                        break;
                    }
                    default: {
                        break;
                    }
                }
                break;
        }
    }

    /*********************************************/
    /************* THREAD MONITORING *************/
    /*********************************************/
    public class RefreshMonitoring extends AsyncTask<Void, Integer, Boolean> {
        @Override
        protected void onPreExecute() {
            flag_monitoring = true;
            count = lapse - 1;
        }

        @Override
        protected void onProgressUpdate(Integer... index) {

        }

        @Override
        protected Boolean doInBackground(Void... arg0) {
            while (flag_monitoring) {
                if (count >= lapse) {
                    count = 0;
                    try {
                        if (MainActivity.FLAG_PERMISSIONS_GPS && MainActivity.FLAG_ENABLE_GPS) {
                            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                                    ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            }
                            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(MainActivity.mGoogleApiClient);
                            if(mLastLocation != null){
                                if (flag_init){
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            lat_lng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                                            cameraPosition = new CameraPosition.Builder()
                                                    .target(lat_lng)            // Sets the center of the map to Mountain View
                                                    .zoom(15.5f)                // Sets the zoom
                                                    .bearing(0)                 // Sets the orientation of the camera to east
                                                    .tilt(0)                    // Sets the tilt of the camera to 30 degrees
                                                    .build();
                                            g_map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                                            marker = g_map.addMarker(new MarkerOptions()
                                                    .position(lat_lng)
                                                    .title("ID: " + ID)
                                                    .snippet("00:00:00 - 0 Km/hr")
                                                    //.anchor(0.5f, 0.5f)
                                                    .infoWindowAnchor(0.5f, 0.5f)
                                                    .zIndex(0.0f));

                                            flag_init = false;
                                        }
                                    });
                                }else {
                                    lat_lng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                                    date = new Date(mLastLocation.getTime());

                                    latitude = mLastLocation.getLatitude();
                                    longitude = mLastLocation.getLongitude();
                                    velocity = (int)(mLastLocation.getSpeed() * 3.6);
                                    time = format_time.format(date);
                                    navigation = (int)mLastLocation.getBearing();

                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            marker.setPosition(lat_lng);
                                            marker.setSnippet(time + " - " + velocity + "Km/hr");
                                            g_map.animateCamera(CameraUpdateFactory.newLatLngZoom(lat_lng, g_map.getCameraPosition().zoom));
                                        }
                                    });

                                    String data = ID + ";" + latitude + ";" + longitude + ";" + velocity + ";" + time + ";" + navigation;
                                    if (usbService != null) { // if UsbService was correctly binded, Send data
                                        usbService.write(data.getBytes());
                                    }else {
                                        Toast.makeText(getApplicationContext(), "USB disconnect", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }else {
                                count = lapse / 2;
                                mGoogleApiClient.reconnect();
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        Toast.makeText(getApplicationContext(),"¡Verificando Localización!",Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }else {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    if (!MainActivity.FLAG_PERMISSIONS_GPS) {
                                        Toast.makeText(getApplicationContext(),"!Se requiere permiso de localización!. Click en Aceptar",Toast.LENGTH_SHORT).show();
                                    } else{
                                        if (!MainActivity.FLAG_ENABLE_GPS) {
                                            Toast.makeText(getApplicationContext(),"!Activar GPS!. Click en Aceptar",Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "ERROR " + e.getMessage());
                    }
                }
                SystemClock.sleep(1000);
                count++;
            }
            Log.e("XXX", "END WHILE");
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {

        }

        @Override
        protected void onCancelled() {
            Toast.makeText(getApplicationContext(),"Monitoring Cancel!",Toast.LENGTH_SHORT).show();
        }
    }

    /*********************************************/
    /****************** USB SERIAL ***************/
    /*********************************************/
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    String data = (String) msg.obj;
                    // mActivity.get().display.append(data);
                    Toast.makeText(mActivity.get(), TAG + data, Toast.LENGTH_LONG).show();
                    break;
                case UsbService.CTS_CHANGE:
                    Toast.makeText(mActivity.get(), "CTS_CHANGE",Toast.LENGTH_LONG).show();
                    break;
                case UsbService.DSR_CHANGE:
                    Toast.makeText(mActivity.get(), "DSR_CHANGE",Toast.LENGTH_LONG).show();
                    break;
                case UsbService.SYNC_READ:
                    String buffer = (String) msg.obj;
                    String data_gps [] = buffer.split(";");
                    // Toast.makeText(mActivity.get(), "Length: " + data_gps.length, Toast.LENGTH_LONG).show();
                    if(data_gps.length == 6){
                        if(data_gps[0].length() == 1){
                            int m_voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                            date = new Date(mLastLocation.getTime());
                            time = format_time.format(date);
                            setTitle("Último envio: " + time + " - Voltaje(mV): " + m_voltage);

                            LatLng lat_lng = new LatLng(Double.parseDouble(data_gps[1]), Double.parseDouble(data_gps[2]));
                            // Toast.makeText(mActivity.get(), "Received: " + data_gps[0], Toast.LENGTH_LONG).show();
                            if(list_marker.containsKey(data_gps[0])){
                                marker_ext = list_marker.get(data_gps[0]);
                                marker_ext.setPosition(lat_lng);
                                marker_ext.setSnippet(data_gps[4] + " - " + data_gps[3] + "Km/hr");
                                // Toast.makeText(mActivity.get(), "yes: " + data_gps[0], Toast.LENGTH_LONG).show();
                            } else{
                                marker_ext = g_map.addMarker(new MarkerOptions()
                                        .position(lat_lng)
                                        .title("ID: " + data_gps[0])
                                        .snippet(data_gps[4] + " - " + data_gps[3] + "Km/hr")
                                        //.anchor(0.5f, 0.5f)
                                        .infoWindowAnchor(0.5f, 0.5f)
                                        .zIndex(0.0f)
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                                list_marker.put(data_gps[0], marker_ext);
                                // Toast.makeText(mActivity.get(), "not: " + data_gps[0], Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                    // mActivity.get().display.append(buffer);
                    break;
            }
        }
    }

    /*********************************************/
    /******************** OTHER ******************/
    /*********************************************/
    private void ShowSnackbar(final int mainTextStringId, final int actionStringId,
                              View.OnClickListener listener) {
        Snackbar.make(
                findViewById(android.R.id.content),
                getString(mainTextStringId),
                Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(actionStringId), listener).show();
    }

    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}
