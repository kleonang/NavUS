package com.example.navus;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import tourguide.tourguide.Pointer;
import tourguide.tourguide.ToolTip;
import tourguide.tourguide.TourGuide;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private static final String TAG = "MyActivity";
    private GoogleMap mMap;
    ArrayList<Venue> BusStopAL = new ArrayList<Venue>();
    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;
    Marker UsersDestinationMarker;
    FirebaseDatabase database;
    Venue source;
    Venue destination;
    ArrayList<Marker> BusStopMarkerAL = new ArrayList<Marker>();
    ArrayList<Marker> RouteMarkers = new ArrayList<Marker>();
    SupportMapFragment mapFragment;
    TextView directionstextview, ETAtextview, maptypelabel;
    Boolean LocationEnabled = false;
    Map<String, Venue> venuedict = new HashMap<String, Venue>();
    Button leftbutton, rightbutton;
    ImageButton maptypebutton;
    ArrayList<String> directionstextarray = new ArrayList<String>();
    ArrayList<String> directionstextarraybackground = new ArrayList<String>(); //to store instructions for notifications
    ArrayList<ArrayList<Polyline>> polylinearray = new ArrayList<ArrayList<Polyline>>();
    int directionstextid = 0;
    EditText searchbar;
    int nooftimestoretry = 3, timeout = 10000;
    JSONObject routes;
    boolean routeselected; //flag to know if the arrows is for routing information or selecting the routes
    int selectedrouteid = 0; //to keep track on which route is being selected
    int backbuttoncode = 0; //0 to close app, 1 to return from select route, 2 to return from route to select route
    boolean backpressed = false; //to know if user pressed the back button
    LocationManager locationManager;
    boolean autopan = true; //to know if user disabled autopan
    boolean runservice = false; //flag to know if to run the background service
    Location userslocation;
    ProgressBar progressbar;
    int directionstextidpan = 0; //id for autopan to keep track what it showed
    TourGuide mTourGuideHandler; //to store the tooltips
    int guideid = 0; //to know what guide to show user
    SharedPreferences.Editor editor;
    Marker closestbusstop; //to store the closest bus stop
    Map<String, Date> lastrefreshed = new HashMap<String, Date>();
    String serverurl = "http://127.0.0.1:5000";

    //to handle search
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        searchbar.clearFocus();
        switch (requestCode) {
            case (0): {
                if (resultCode == Activity.RESULT_OK) {
                    if (mTourGuideHandler!=null) //in case user taps on search bar again during tutorial
                        mTourGuideHandler.cleanUp();

                    //disable the left and right buttons first
                    leftbutton.setEnabled(false);
                    rightbutton.setEnabled(false);

                    clearui(true); //clear everything including polyline

                    //shrink the map view to show directions text
                    DisplayMetrics displayMetrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                    ViewGroup.LayoutParams params = mapFragment.getView().getLayoutParams();
                    params.height = (int) Math.floor(displayMetrics.heightPixels * 0.80);
                    mapFragment.getView().setLayoutParams(params);

                    //get source and destination from search activity
                    String sourcename = data.getStringExtra("Source");
                    String destinationname = data.getStringExtra("Destination");
                    guideid = data.getIntExtra("GuideID",3);

                    if (sourcename == null) { //user did not enter source
                        //get user's location
                        userslocation = getLocation();

                        source = new Venue("Source", String.valueOf(userslocation.getLatitude()), String.valueOf(userslocation.getLongitude()), "false");
                    } else {
                        source = new Venue(sourcename, venuedict.get(sourcename).getLatitude(), venuedict.get(sourcename).getLongitude(), venuedict.get(sourcename).getIsBusStop());
                    }


                    //create Venue object based on user's destination
                    destination = new Venue(destinationname, venuedict.get(destinationname).getLatitude(), venuedict.get(destinationname).getLongitude(), venuedict.get(destinationname).getIsBusStop());
                    Location destinationlocation = new Location(LocationManager.GPS_PROVIDER);

                    //Get shortest path from source to destination
                    String url = serverurl + "/getpath/" + source.getLatitude() + "/" + source.getLongitude() + "/" + destination.getLatitude() + "/" + destination.getLongitude();
                    new getbestpath().execute(url);

                    //inform user route is being calculated
                    Toast.makeText(getApplicationContext(), R.string.calculating_route, Toast.LENGTH_SHORT).show();
                    directionstextview.setText(R.string.calculating_route);

                    //set the pin at the user's destination which may not be the destination bus stop
                    LatLng UsersDestination = new LatLng(Double.parseDouble(destination.getLatitude()), Double.parseDouble(destination.getLongitude()));
                    //add the user's destination description when he taps on the pin
                    UsersDestinationMarker = mMap.addMarker(new MarkerOptions().position(UsersDestination).title(destinationname));
                    //set focus on user's destination
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(UsersDestination, 17));
                    routeselected = false; //set flag to false
                    selectedrouteid = 0; //reset to 0
                    backbuttoncode = 1; //user is viewing routes, set to 1
                    progressbar.setVisibility(View.VISIBLE); //set invisible at first

                }else if (routes!=null){ //user tapped on search bar during navigation by mistake
                    runservice = true;
                }
                break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        searchbar = findViewById(R.id.searchbutton);
        directionstextview = findViewById(R.id.directionstext);
        leftbutton = findViewById(R.id.leftbutton);
        rightbutton = findViewById(R.id.rightbutton);
        maptypebutton = findViewById(R.id.maptypebutton);
        ETAtextview = findViewById(R.id.eta);
        directionstextview.setMovementMethod(ScrollingMovementMethod.getInstance());
        maptypelabel = findViewById(R.id.maptypelabel);
        progressbar = findViewById(R.id.progressbar);
        progressbar.setVisibility(View.INVISIBLE); //set invisible at first

        maptypelabel.setText(getResources().getString(R.string.satellite)); //set default text to satellite
        maptypelabel.setTextColor(getResources().getColor(R.color.white)); //set default text to satellite
        getdata(); //get venue and bus stop data

        if (guideid==0)
            guideuser(guideid); //start the tour

        searchbar.setOnClickListener(new View.OnClickListener() { //wait for user to tap the searchbar
            @Override
            public void onClick(View v) {
                if (guideid==0){
                    mTourGuideHandler.cleanUp(); //clear tip box
                    guideid++;
                }
                //hide closest bus stop arrival information
                if (closestbusstop!=null){
                    closestbusstop.hideInfoWindow();
                }
                runservice = false;//to prevent service from running
                Intent mainIntent = new Intent(MapsActivity.this, Search.class);
                mainIntent.putExtra("locationenabled", LocationEnabled);
                startActivityForResult(mainIntent, 0);
                if (UsersDestinationMarker != null) { //clear pin on map if exists
                    UsersDestinationMarker.remove();
                }
            }
        });


        leftbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (routeselected) { //button is to show routing information
                    directionstextview.scrollTo(0, 0); //scroll to top

                    if (directionstextid > 0) {
                        //decrement ID
                        directionstextid--;
                        //ensure text is not blank
                        while(directionstextarray.get(directionstextid).equals(""))
                            directionstextid--;
                        disableenablebuttons(directionstextid, directionstextarray.size());

                        //update textview
                        directionstextview.setText(directionstextarray.get(directionstextid));
                        Marker currentmarker = RouteMarkers.get(directionstextid);
                        currentmarker.showInfoWindow();
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentmarker.getPosition(), 17));
                        setautopan(false);
                    }
                } else { //button is used to select routes
                    selectedrouteid--;
                    drawRoute(selectedrouteid);
                    disableenablebuttons(selectedrouteid, routes.length());
                }
            }
        });

        rightbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                directionstextview.scrollTo(0, 0); //scroll to top
                if (guideid==3 || guideid==5) { //clear tip box for route selection and route instructions
                    mTourGuideHandler.cleanUp();
                    guideid++;
                    guideuser(guideid);
                }

                if (routeselected) { //button is to show routing information
                    if (directionstextid < directionstextarray.size() - 1) {
                        //increment ID
                        directionstextid++;
                        //ensure text is not blank
                        while(directionstextarray.get(directionstextid).equals(""))
                            directionstextid++;

                        disableenablebuttons(directionstextid, directionstextarray.size());
                        //update textview
                        directionstextview.setText(directionstextarray.get(directionstextid));
                        Marker currentmarker = RouteMarkers.get(directionstextid);
                        currentmarker.showInfoWindow();
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentmarker.getPosition(), 17));
                        setautopan(false);
                    }
                } else { //button is used to select routes
                    selectedrouteid++;
                    drawRoute(selectedrouteid);
                    disableenablebuttons(selectedrouteid, routes.length());
                }
            }
        });

        ETAtextview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mTourGuideHandler!=null) {
                    mTourGuideHandler.cleanUp();
                    guideid = 5; //set to route instructions
                    guideuser(guideid);
                }


                if (!routeselected) {
                    backbuttoncode = 2;//update the back button code
                    //disable buttons first
                    leftbutton.setEnabled(false);
                    rightbutton.setEnabled(false);
                    runservice = true; //set flag to enable service
                    routeselected = true; //set flag to true to restore the left/right button functionally for routing steps
                    directionstextview.setText(directionstextarray.get(directionstextid)); //set the first step
                    disableenablebuttons(directionstextid, directionstextarray.size());

                    //update ETA
                    try {
                        ETAtextview.setText(getResources().getString(R.string.eta, routes.getJSONObject(String.valueOf(selectedrouteid)).getString("ETA")));
                        ETAtextview.setBackgroundColor(getResources().getColor(R.color.text_default));
                        ETAtextview.setTextSize(12);
                        Marker source = RouteMarkers.get(0);
                        source.showInfoWindow();//show the first pin information
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(source.getPosition(), 17));//move to the location
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if (!autopan) {
                    setautopan(true);
                }
            }
        });

        maptypebutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(guideid==6){ //clear tip box for satellite view
                    mTourGuideHandler.cleanUp();
                    guideid++;
                    guideuser(guideid);
                }
                if (mMap.getMapType() == GoogleMap.MAP_TYPE_NORMAL) { //if normal map set to hybrid
                    mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    maptypebutton.setImageResource(R.drawable.map);
                    maptypelabel.setText(getResources().getString(R.string.map));
                    maptypelabel.setTextColor(getResources().getColor(R.color.text_default));
                } else { //else return to normal
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    maptypebutton.setImageResource(R.drawable.satellite);
                    maptypelabel.setText(getResources().getString(R.string.satellite));
                    maptypelabel.setTextColor(getResources().getColor(R.color.white));
                }
            }
        });

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void onPause(){
        super.onPause();
        //ensure route is selected
        if (runservice){
            ArrayList<LatLng> routelatlngarray = new ArrayList<LatLng>();
            for (int i = 0; i<RouteMarkers.size(); i++){
                Marker marker = RouteMarkers.get(i);

                if (i!=RouteMarkers.size()-1 || venuedict.get(marker.getTitle()).getIsBusStop().equals("true")) { //ensure last point is a bus stop then add
                    routelatlngarray.add(marker.getPosition());
                }
            }


            Intent intent = new Intent(this, LocationUpdateService.class);
            intent.putExtra("directionstextarray", directionstextarraybackground);
            intent.putExtra("routelatlngarray", routelatlngarray);
            intent.putExtra("directionstextidpan", directionstextidpan);
            startService(intent);
        }
    }

    protected void onResume() {
        super.onResume();
        //stop service
        stopService(new Intent(getApplicationContext(), LocationUpdateService.class));
    }


    protected void onNewIntent (Intent intent) {
        super.onNewIntent(intent);
        //check if user tap on the stop button in the notification
        if (intent.getExtras()!=null && intent.getExtras().getBoolean("stopapp", false)==true){
            //stop service
            stopService(new Intent(getApplicationContext(), LocationUpdateService.class));
            //close the app
            this.finishAffinity();
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        String TAG = MapsActivity.class.getName();
        mMap = googleMap;
        mMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    //user moves map during navigation, disable the autopan
                    if (routeselected && LocationEnabled)
                        setautopan(false);
                }
            }
        });

        //to move the map to marker that the user tapped on
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                Boolean refresh = true;
                if (routeselected)
                    setautopan(false);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 17));
                marker.showInfoWindow();
                //if it is a busstop then query for timings
                if (BusStopMarkerAL.contains(marker)){
                    if (lastrefreshed.containsKey(marker.getTitle())){
                        //only allow the user to refresh every 30 seconds
                        Date thirtysecondslater = new Date(lastrefreshed.get(marker.getTitle()).getTime() + 30000);
                        if (thirtysecondslater.compareTo(new Date()) > 0){
                            refresh = false;
                        }
                    }
                    if (refresh){
                        marker.setSnippet(getString(R.string.getting_arrival_info));
                        marker.showInfoWindow();
                        new getarrivaltimings().execute(marker.getTitle());
                    }
                }
                return true;
            }
        });


        //check night mode
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES:
                googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.night_mode));
                break;
        }


        mMap.getUiSettings().setMapToolbarEnabled(false); //remove the google maps icon when pin is tapped

        //to hide all markers when zoomed out
        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
            @Override
            public void onCameraMove() {
                CameraPosition cameraPosition = mMap.getCameraPosition();
                if (cameraPosition.zoom > 12.0) {
                    for (Marker a : BusStopMarkerAL) {
                        a.setVisible(true);
                    }
                    for (Marker b : RouteMarkers) {
                        b.setVisible(true);
                    }
                } else {
                    for (Marker a : BusStopMarkerAL) {
                        a.setVisible(false);
                    }
                    for (Marker b : RouteMarkers) {
                        b.setVisible(false);
                    }
                }
            }
        });


        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {

                LinearLayout info = new LinearLayout(getApplicationContext());
                info.setOrientation(LinearLayout.VERTICAL);

                //create bus stop name as title
                TextView title = new TextView(getApplicationContext());
                title.setTextColor(Color.BLACK);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(null, Typeface.BOLD);
                title.setText(marker.getTitle());
                info.addView(title);
                title.measure(0,0);//so that we can measure the width later
                int maxwidth = 0;

                try {
                    TableLayout tbl=new TableLayout(getApplicationContext());

                    tbl.setStretchAllColumns(true);

                    if (marker.getSnippet()!=null) { //ensure there's snippet
                        JSONObject json = new JSONObject(marker.getSnippet());
                        Iterator<String> keys = json.keys();
                        while (keys.hasNext()) {
                            TableRow row = new TableRow(getApplicationContext());
                            String key = keys.next();
                            //add service name
                            TextView tv = new TextView(getApplicationContext());
                            tv.setText(key);
                            tv.setTypeface(null, Typeface.BOLD); //bold service name
                            row.addView(tv);
                            //loop through both arrival times
                            Iterator<String> arrivalkeys = json.getJSONObject(key).keys();
                            while (arrivalkeys.hasNext()) {
                                String arrivalkey = arrivalkeys.next();
                                tv = new TextView(getApplicationContext());
                                String arrivaltiming = json.getJSONObject(key).getString(arrivalkey);
                                //if service is operating, check for 0 as it means arriving
                                if (!arrivaltiming.equals("-")) {
                                    if (Integer.parseInt(arrivaltiming) == 0)
                                        arrivaltiming = getString(R.string.arriving);
                                    else
                                        arrivaltiming = getString(R.string.mins, arrivaltiming);
                                }
                                tv.setText(arrivaltiming);
                                tv.setGravity(Gravity.CENTER);
                                row.addView(tv);
                            }

                            tbl.addView(row);
                            row.measure(0,0);//so that we can measure the width later
                            maxwidth = Math.max(maxwidth, row.getMeasuredWidth());
                        }
                        tbl.setLayoutParams(new LinearLayout.LayoutParams((int) Math.max(Math.floor(maxwidth*1.2),title.getMeasuredWidth()), LinearLayout.LayoutParams.WRAP_CONTENT)); //set width to min of 500 or larger
                        info.addView(tbl);
                    }

                } catch (JSONException e) {
                    //not a JSON object, should be string
                    TextView snippet = new TextView(getApplicationContext());
                    snippet.setGravity(Gravity.CENTER);
                    snippet.setTextColor(getResources().getColor(R.color.text_default));
                    snippet.setText(marker.getSnippet());
                    //if there is not snippet don't show it
                    if (marker.getSnippet()!=null){
                        info.addView(snippet);
                    }
                }
                return info;
            }
        });

        //ask for location permission
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_CODE_ASK_PERMISSIONS);
                LocationEnabled = false;
                return;
            }
            LocationEnabled = true;
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true); //for the crosshair icon on the top right
            userslocation = getLocation();
            if (userslocation!=null)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(userslocation.getLatitude(), userslocation.getLongitude()), 15));//zoom to user's location on launch
        }
    }

    public void setautopan(Boolean enabled){
        if (LocationEnabled){
            if (enabled){
                autopan = true;
                Toast.makeText(getApplicationContext(), R.string.autopan_enabled, Toast.LENGTH_SHORT).show();
                //update ETA
                try {
                    ETAtextview.setText(getResources().getString(R.string.eta, routes.getJSONObject(String.valueOf(selectedrouteid)).getString("ETA")));
                    ETAtextview.setBackgroundColor(getResources().getColor(R.color.text_default));
                    ETAtextview.setTextSize(12);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }else{
                autopan = false;
                ETAtextview.setText(R.string.autopan);
                ETAtextview.setBackgroundColor(getResources().getColor(R.color.skyblue));
            }
        }
    }

    //updates local database with bus stops and venue data
    public void getdata() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
        database = MyFireBase.getDatabase();
        SharedPreferences sharedpreferences = getSharedPreferences("MySharedPreference", MODE_PRIVATE);
        editor = sharedpreferences.edit();
        String LastUpdatedDate = sharedpreferences.getString("UpdatedDate", "1970-01-01 00:00:00.00000");
        guideid = sharedpreferences.getInt("GuideID", 0);

        DatabaseReference LastUpdatedRef = database.getReference("LastUpdated");
        LastUpdatedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    LastUpdated value = userSnapshot.getValue(LastUpdated.class);
                    try {
                        Date UpdatedDate = sdf.parse(value.getUpdatedDate());
                        if (UpdatedDate.after(sdf.parse(LastUpdatedDate))) {//new updates
                            System.out.println("REFRESHING DATA");
                            refreshdata();
                        } else {
                            System.out.println("UP TO DATE");
                            getdatafromsql();
                        }
                        editor.putString("UpdatedDate", sdf.format(new Date())); //set current datetime
                        editor.apply(); //save data
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }

    public void getdatafromsql() {
        DBHelper myhelper = new DBHelper(this);
        SQLiteDatabase mydatabase = myhelper.getWritableDatabase();
        Cursor cursor = mydatabase.rawQuery("SELECT * FROM Venues", null);
        if (cursor.moveToFirst()) {
            do {
                if (cursor.getString(3).equals("true")) { //if is busstop
                    BusStopAL.add(new Venue(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)));
                }
                String name = cursor.getString(0);
                Venue NewVenue = new Venue(name, cursor.getString(1), cursor.getString(2), cursor.getString(3));
                venuedict.put(name, NewVenue);
            } while (cursor.moveToNext());
        }
        cursor.close();
        mydatabase.close();
        plotbusstops();
    }

    public void refreshdata() {
        DBHelper myhelper = new DBHelper(this);
        SQLiteDatabase mydatabase = myhelper.getWritableDatabase();
        mydatabase.execSQL("DROP TABLE IF EXISTS Venues");

        //create new table
        mydatabase.execSQL("CREATE TABLE Venues(Name String, Latitude Text, Longitude Text, IsBusStop String)"); //using text so it does not truncate the latitude and longitude

        //get venue info
        DatabaseReference VenueRef = database.getReference("Venues");
        VenueRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    Venue value = userSnapshot.getValue(Venue.class);
                    mydatabase.execSQL("INSERT INTO Venues VALUES ('" + value.getName().replaceAll("'", "''") + "','" + value.getLatitude() + "','" + value.getLongitude() + "','" + value.getIsBusStop() + "')");//need to replace all in case of ' like Prince George's Park
                }
                getdatafromsql(); //for the first launch
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }


    //plot pins for busstops
    public void plotbusstops() {
        LatLng latLng = new LatLng(1, 1);
        for (Venue a : BusStopAL) {
            latLng = new LatLng(Double.parseDouble(a.getLatitude()), Double.parseDouble(a.getLongitude()));
            BitmapDrawable bitmapdraw = (BitmapDrawable) getResources().getDrawable(R.drawable.appicon);
            Bitmap b = bitmapdraw.getBitmap();
            Bitmap finalMarker = Bitmap.createScaledBitmap(b, 40, 40, false);
            Marker BusStopMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(a.Name).snippet(getString(R.string.getting_arrival_info)).icon(BitmapDescriptorFactory.fromBitmap(finalMarker)));
            //Marker BusStopMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(a.Name).icon(BitmapDescriptorFactory.fromResource(R.drawable.busstop)));
            BusStopMarkerAL.add(BusStopMarker);
        }
        if (!LocationEnabled) //if location not enabled then zoom to the bus stops
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
        else{ //show closest bus stop arrival info
            float bestdistance = Float.MAX_VALUE; //initialise to max first
            float[] distance = new float[1];
            if (userslocation!=null){
                for (Marker busstop: BusStopMarkerAL){
                    Location.distanceBetween(userslocation.getLatitude(), userslocation.getLongitude(), busstop.getPosition().latitude, busstop.getPosition().longitude, distance);
                    if (distance[0] < bestdistance){
                        bestdistance = distance[0];
                        closestbusstop = busstop;
                    }
                }
                new getarrivaltimings().execute(closestbusstop.getTitle());
            }
        }
    }

    //to request for location permission
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    userslocation = getLocation();
                    LocationEnabled = true;
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    mMap.setMyLocationEnabled(true);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true); //for the crosshair icon on the top right
                    if (userslocation!=null)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(userslocation.getLatitude(), userslocation.getLongitude()), 15));//zoom to user's location on launch
                } else {
                    // Permission Denied
                    Toast.makeText(this, R.string.enable_location, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    //Get location
    public Location getLocation() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.enable_location, Toast.LENGTH_SHORT).show();
            return null;
        }
        Location myLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (myLocation == null) {
            myLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 10, mLocationListener);
        return myLocation;
    }

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            userslocation = location; //update user's location
            if (routeselected && autopan){ //ensure user has selected route and autopan is enabled
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 16)); //zoom to user's location
                Boolean directionsupdated = false;
                for (int j=0; j<RouteMarkers.size(); j++){
                    Marker waypoint = RouteMarkers.get(j);
                    Location waypointlocation = new Location("waypoint");
                    waypointlocation.setLatitude(waypoint.getPosition().latitude);
                    waypointlocation.setLongitude(waypoint.getPosition().longitude);

                    if ((location.distanceTo(waypointlocation) < 150 && directionstextidpan+1 == j) || directionstextidpan==0){//less than 200m and did not show before
                        int previd = directionstextidpan;
                        directionstextidpan = j;
                        waypoint.showInfoWindow(); //show instructions
                        String text = directionstextarray.get(j);
                        if (!text.equals("")){
                            directionstextview.setText(text); //update directions text view
                            disableenablebuttons(directionstextidpan, directionstextarray.size());
                            directionstextid = directionstextidpan;
                            directionsupdated = true;
                        }

                        if (!directionsupdated){ //show next instruction when it does not match any waypoints
                            if (directionstextidpan < directionstextarray.size()-1){
                                int id = directionstextidpan+1;
                                //ensure text is not blank
                                while(directionstextarray.get(id).equals(""))
                                    id++;
                                directionstextview.setText(directionstextarray.get(id)); //show next step
                                disableenablebuttons(id, directionstextarray.size());
                            }
                        }
                        if (previd!=0)
                            break;
                    }
                }
            }
        }
    };

    public void disableenablebuttons(int id, int length){
        if (id == length-1)
            rightbutton.setEnabled(false);
        else
            rightbutton.setEnabled(true);

        if (id == 0)
            leftbutton.setEnabled(false);
        else
            leftbutton.setEnabled(true);
    }

    //Getting shortest path info from server
    private class getbestpath extends AsyncTask<String, Integer, String> {
        protected String doInBackground(String... urls) {
            URL url = null;
            String content = "";
            for (int i=0; i<nooftimestoretry; i++){
                HttpURLConnection connection = null;
                StringBuilder sb = null;

                try {
                    System.out.println(urls[0]);
                    url = new URL(urls[0]);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(timeout);
                    connection.setReadTimeout(timeout);
                    connection.connect();

                    InputStream in = connection.getInputStream();
                    InputStreamReader isw = new InputStreamReader(in);
                    sb = new StringBuilder();

                    int data = isw.read();
                    while (data != -1) {
                        char current = (char) data;
                        sb.append(current);
                        data = isw.read();
                    }
                    isw.close(); //close inputstreamreader
                    in.close(); //close inputstream
                    break;
                } catch (IOException e) {
                    System.out.println(e);
                    publishProgress(i);
                } finally {
                    connection.disconnect();
                    if (sb !=null)
                        content = sb.toString();
                }
            }
            return content;
        }

        protected void onProgressUpdate(Integer... progress) {
            if(progress[0]==0)
                directionstextview.setText(R.string.reattempt);
            else if (progress[0]==1)
                directionstextview.setText(R.string.reattempt2);
        }

        protected void onPostExecute(String result) {
            // this is executed on the main thread after the process is over
            // update your UI here
            progressbar.setVisibility(View.INVISIBLE); //hide progress bar

            if (result==""){//Error accessing server
                Toast.makeText(getApplicationContext(),R.string.route_server_unavailable, Toast.LENGTH_LONG).show();
                directionstextview.setText(R.string.route_server_unavailable);
                if (guideid==3){
                    showguideerror();
                }
            }else{
                try {
                    routes = new JSONObject(result);
                    if (routes.length()!=0){
                        drawRoute(0); //draw the route 0
                        if (guideid==3)
                            guideuser(3);
                    }else{
                        directionstextview.setText(R.string.no_route_found);
                        Toast.makeText(getApplicationContext(),R.string.no_route_found, Toast.LENGTH_LONG).show();
                        if (guideid==3)
                            showguideerror();
                    }

                } catch (JSONException e) {
                    System.out.println(e);
                    Toast.makeText(getApplicationContext(),R.string.route_server_unavailable, Toast.LENGTH_LONG).show();
                    directionstextview.setText(R.string.route_server_unavailable);
                    if (guideid==3){
                        showguideerror();
                    }
                }
            }
        }
    }

    private void showguideerror(){
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.opps))
                .setMessage(getString(R.string.guide_again))
                .setCancelable(false)
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).show();
    }

    //plots the bus stop along the route
    private void drawRoute(int routeno){
        try {
            //reset all UI
            clearui(false);//clear everything excluding polyline

            if(routes.length() > 1) //more than 1 option for the user, enable the right button
                rightbutton.setEnabled(true);

            //Process the bus stops for the path from the server
            ArrayList<WayPoint> routeinfo = new ArrayList<WayPoint>();
            LatLngBounds.Builder builder = new LatLngBounds.Builder();//to know how to zoom the map to fit the polyline
            int nooftransits=0;

            JSONArray WayPointJSONArray = routes.getJSONObject(String.valueOf(routeno)).getJSONArray("Route");
            String ETA = routes.getJSONObject(String.valueOf(selectedrouteid)).getString("ETA");
            String traveltime = routes.getJSONObject(String.valueOf(selectedrouteid)).getString("TravelTime");

            BitmapDrawable waypointbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.waypoint);
            BitmapDrawable redpinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspinred);
            BitmapDrawable greenpinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspingreen);
            BitmapDrawable bluepinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspinblue);
            Bitmap waypointpin = Bitmap.createScaledBitmap(waypointbitmap.getBitmap(), 50, 50, false);
            Bitmap redpin = Bitmap.createScaledBitmap(redpinbitmap.getBitmap(), 100, 100, false);
            Bitmap greenpin = Bitmap.createScaledBitmap(greenpinbitmap.getBitmap(), 100, 100, false);
            Bitmap bluepin = Bitmap.createScaledBitmap(bluepinbitmap.getBitmap(), 100, 100, false);

            //if no route found
            if (WayPointJSONArray.length()==0){
                directionstextview.setText(R.string.no_route_found);
                Toast.makeText(getApplicationContext(),R.string.no_route_found, Toast.LENGTH_LONG).show();

            }else if(WayPointJSONArray.length()==1){ //user can walk there
                ETAtextview.setVisibility(View.VISIBLE);
                directionstextarray.add(getResources().getString(R.string.walk_to_destination));
                directionstextarraybackground.add(getResources().getString(R.string.walk_to_destination));
                UsersDestinationMarker.showInfoWindow();
                Marker sourcemarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(source.getLatitude()),Double.parseDouble(source.getLongitude()))).title(getString(R.string.walk_to_destination)).icon(BitmapDescriptorFactory.fromBitmap(greenpin)));
                RouteMarkers.add(sourcemarker);//add to RouteMarkers

                //create route using user's source and destination
                WayPoint sourcewaypoint = new WayPoint(source.getName(), source.getLatitude(), source.getLongitude(), source.getIsBusStop(), "-", "-", "-");
                WayPoint destinationwaypoint = new WayPoint(destination.getName(), destination.getLatitude(), destination.getLongitude(), destination.getIsBusStop(), "-", "-", "-");
                routeinfo.add(sourcewaypoint);
                routeinfo.add(destinationwaypoint);

                //check if polyline already exist
                if (polylinearray.size() <= routeno){
                    polylinearray.add(new ArrayList<Polyline>()); //Initialise as empty arraylist
                    // Getting URL for the Google Directions API
                    String url = getDirectionsUrl(routeinfo, "walking");
                    //draw the blueline
                    new getdirections().execute(url);
                }


            }else{
                ETAtextview.setVisibility(View.VISIBLE);
                JSONObject JSONObj;
                for (int itemIndex=0, totalcount = WayPointJSONArray.length(); itemIndex < totalcount; itemIndex++) {
                    JSONObj = WayPointJSONArray.getJSONObject(itemIndex);
                    WayPoint waypoint = new WayPoint(JSONObj.getString("Name"), JSONObj.getString("Latitude"), JSONObj.getString("Longitude"), JSONObj.getString("IsBusStop"), JSONObj.getString("Service"), JSONObj.getString("BusArrivalTime"), JSONObj.getString("BusArrivalTimeMins"));
                    routeinfo.add(waypoint);//add to route info
                }

                //check if polyline already exist
                if (polylinearray.size() <= routeno){
                    polylinearray.add(new ArrayList<Polyline>()); //Initialise as empty arraylist
                    // Getting URL for the Google Directions API
                    String url = getDirectionsUrl(routeinfo, "driving");
                    //draw the blueline
                    new getdirections().execute(url);
                }

                //plot the pins
                String directionstext;

                for (int i=0; i<routeinfo.size(); i++){
                    Marker BusStopMarker = null;
                    WayPoint currentbusstop = routeinfo.get(i);
                    //source bus stop
                    if (i == 0) {
                        //add walk instructions if source bus stop is not the user's location
                        if (Double.parseDouble(source.getLatitude()) != Double.parseDouble(currentbusstop.getLatitude()) ||  Double.parseDouble(source.getLongitude()) != Double.parseDouble(currentbusstop.getLongitude())){
                            String title = getString(R.string.head_to, currentbusstop.getName());
                            directionstext = getString(R.string.board, currentbusstop.getService(), currentbusstop.getBusArrivalTimeMins(), currentbusstop.getBusArrivalTime());

                            //Draw walking path in red
                            PolylineOptions walkpath = new PolylineOptions().width(20).color(Color.RED).geodesic(true);
                            walkpath.add(new LatLng(Double.parseDouble(source.getLatitude()), Double.parseDouble(source.getLongitude())));
                            walkpath.add(new LatLng(Double.parseDouble(currentbusstop.getLatitude()), Double.parseDouble(currentbusstop.getLongitude())));
                            polylinearray.get(routeno).add(mMap.addPolyline(walkpath));
                            BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(source.getLatitude()),Double.parseDouble(source.getLongitude()))).title(getString(R.string.your_location)).snippet(title).icon(BitmapDescriptorFactory.fromBitmap(greenpin)));
                            RouteMarkers.add(BusStopMarker);
                            directionstextarray.add(title);
                            directionstextarraybackground.add(title);

                            //add string to array for the buttons
                            directionstextarray.add(directionstext);
                            directionstextarraybackground.add(directionstext);
                            BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(currentbusstop.getName()).snippet(directionstext).icon(BitmapDescriptorFactory.fromBitmap(bluepin)));

                        }else{
                            directionstext = getString(R.string.board, currentbusstop.getService(), currentbusstop.getBusArrivalTimeMins(), currentbusstop.getBusArrivalTime());
                            //add string to array for the buttons
                            directionstextarray.add(directionstext);
                            directionstextarraybackground.add(directionstext);
                            BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(currentbusstop.getName()).snippet(directionstext).icon(BitmapDescriptorFactory.fromBitmap(greenpin)));
                        }

                        //show first stop information
                        BusStopMarker.showInfoWindow();

                    }else if(i == routeinfo.size()-1){//reached destination bus stop
                        //remove user's destination if it is a bus stop
                        //if (UsersDestinationMarker!=null && UsersDestinationMarker.getTitle().equals(currentbusstop.getName())){
                        UsersDestinationMarker.remove();
                        //}
                        directionstext = getString(R.string.alight_at, currentbusstop.getName());
                        BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(currentbusstop.getName()).snippet(getString(R.string.alight_here)).icon(BitmapDescriptorFactory.fromBitmap(redpin)));
                        //add string to array for the buttons
                        directionstextarray.add(directionstext);
                        directionstextarraybackground.add(directionstext + getString(R.string.next_stop));

                        //add walk instructions if destination bus stop is not the user's destination
                        if (Double.parseDouble(destination.getLatitude()) != Double.parseDouble(currentbusstop.getLatitude()) ||  Double.parseDouble(destination.getLongitude()) != Double.parseDouble(currentbusstop.getLongitude())){
                            RouteMarkers.add(BusStopMarker);
                            directionstext = getString(R.string.walk_from, currentbusstop.getName(), destination.getName());
                            directionstextarray.add(directionstext);

                            //to combine the last two steps in one for the notifications
                            String prevtext = directionstextarraybackground.get(directionstextarraybackground.size()-1);
                            directionstextarraybackground.remove(directionstextarraybackground.size()-1);
                            directionstextarraybackground.add(prevtext + "\n" + directionstext);

                            BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(destination.getLatitude()), Double.parseDouble(destination.getLongitude()))).title(destination.getName()).snippet(getString(R.string.walk_from_here, currentbusstop.getName())));

                            //Draw walking path in red
                            PolylineOptions walkpath = new PolylineOptions().width(20).color(Color.RED).geodesic(true);
                            walkpath.add(new LatLng(Double.parseDouble(currentbusstop.getLatitude()), Double.parseDouble(currentbusstop.getLongitude())));
                            walkpath.add(new LatLng(Double.parseDouble(destination.getLatitude()), Double.parseDouble(destination.getLongitude())));
                            polylinearray.get(routeno).add(mMap.addPolyline(walkpath));
                        }

                    }else if (currentbusstop.getName().equals(routeinfo.get(i-1).getName())){//if same busstop name means transfer required
                        //remove old marker first
                        RouteMarkers.get(RouteMarkers.size()-1).remove(); //remove the previous pin
                        RouteMarkers.remove(RouteMarkers.size()-1); //delete from array
                        directionstextarray.remove(directionstextarray.size()-1);
                        directionstextarraybackground.remove(directionstextarray.size()-1);
                        directionstext = getString(R.string.transfer,routeinfo.get(i-1).getService(), currentbusstop.getService(), currentbusstop.getName(), currentbusstop.getBusArrivalTimeMins(), currentbusstop.getBusArrivalTime());
                        BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(currentbusstop.getName()).snippet(getString(R.string.transfer_here, routeinfo.get(i-1).getService(), currentbusstop.getService(), currentbusstop.getBusArrivalTimeMins(), currentbusstop.getBusArrivalTime())).icon(BitmapDescriptorFactory.fromBitmap(bluepin))); //set blue pin if need to transfer bus
                        //add string to array for the buttons
                        directionstextarray.add(directionstext);
                        directionstextarraybackground.add(directionstext + getString(R.string.next_stop));
                        nooftransits++;
                    }else{
                        BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(currentbusstop.getName()).icon(BitmapDescriptorFactory.fromBitmap(waypointpin))); //else white circle
                        directionstextarray.add("");//add empty string as placeholder
                        directionstextarraybackground.add("");
                    }
                    if (BusStopMarker!=null){
                        RouteMarkers.add(BusStopMarker);//add to RouteMarkers
                    }
                }
            }

            //update UI
            if (ETA.equals("-")) {//no ETA
                ETA = getResources().getString(R.string.na);
                Toast.makeText(getApplicationContext(), R.string.no_eta_available, Toast.LENGTH_SHORT).show();
            }

            if (WayPointJSONArray.length()!=0){ //if there are routes
                directionstextview.setText(Html.fromHtml(getString(R.string.route, selectedrouteid+1, routes.length(), traveltime, ETA, nooftransits)));
                ETAtextview.setText(R.string.select);
                ETAtextview.setTextSize(14);
                ETAtextview.setTextColor(getResources().getColor(R.color.white));
                ETAtextview.setBackgroundColor(getResources().getColor(R.color.skyblue));

                for (int i=0; i<polylinearray.size(); i++){
                    ArrayList<Polyline> currentpolyline = polylinearray.get(i);
                    if (i==routeno) {
                        for (Polyline a : currentpolyline) { //show all polylines in current route
                            a.setVisible(true);
                            for (LatLng latlng : a.getPoints()){
                                builder.include(latlng);
                            }
                        }
                        for (Marker marker : RouteMarkers)
                            builder.include(marker.getPosition());

                        LatLngBounds bounds = builder.build();
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 2000, null); //move the map to fit all waypoints

                    }else {
                        for (Polyline a : currentpolyline) { //show all polylines in current route
                            a.setVisible(false); //hide the rest
                        }
                    }
                }

            }


        } catch (Exception e) {
            System.out.println(e);
            Toast.makeText(this, e.toString(),Toast.LENGTH_LONG).show();
        }

    }

    private String getDirectionsUrl(ArrayList<WayPoint> routeinfo, String mode){
        String str_origin = "";
        String str_dest = "";
        // Key
        String key = "key=" + getString(R.string.google_maps_key);

        // Waypoints
        String waypointsstring = "";
        String previousbusstopname="";
        String url="";

        for (int i=0; i<routeinfo.size(); i++){
            if (i==0){//source is first point in route only when user did not enable location services
                str_origin = "origin=" + routeinfo.get(i).getLatitude() + "," + routeinfo.get(i).getLongitude();
            }else if(i==routeinfo.size()-1){//destination
                str_dest = "destination=" + routeinfo.get(i).getLatitude() + "," + routeinfo.get(i).getLongitude();
            }else if(!previousbusstopname.equals(routeinfo.get(i).getName())){ //remove duplicates in the event of transfer
                waypointsstring += routeinfo.get(i).getLatitude() + "," + routeinfo.get(i).getLongitude() + "|";
            }

            previousbusstopname = routeinfo.get(i).getName();
        }

        // Building the parameters to the web service
        String parameters = str_origin+"&"+str_dest+"&"+key+"&mode=" + mode + "&waypoints="+waypointsstring;

        // Building the url to the web service
        url = "https://maps.googleapis.com/maps/api/directions/json?"+parameters;
        System.out.println(url);

        return url;
    }

    private class getdirections extends AsyncTask<String, Integer, String> {
        protected String doInBackground(String... urls) {
            URL url = null;
            String content = "", line;
            try {
                url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
                connection.connect();

                InputStream in = connection.getInputStream();
                InputStreamReader isw = new InputStreamReader(in);
                StringBuilder sb = new StringBuilder();

                int data = isw.read();
                while (data != -1) {
                    char current = (char) data;
                    sb.append(current);
                    data = isw.read();
                }

                isw.close(); //close inputstreamreader
                in.close(); //close inputstream
                connection.disconnect();
                content = sb.toString();

            } catch (IOException e) {
                System.out.println(e);
            }
            return content;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(String result) {
            // this is executed on the main thread after the process is over
            // update your UI here
            if (result==""){//Error accessing server
                Toast.makeText(getApplicationContext(),R.string.google_server_unavailable, Toast.LENGTH_LONG).show();
                directionstextview.setText(R.string.google_server_unavailable);
            }else{
                try {
                    JSONObject json = new JSONObject(result);
                    //Routes array is always of length 1 so index 0
                    String overview_polyline = json.getJSONArray("routes").optJSONObject(0).optJSONObject("overview_polyline").getString("points");

                    //draw the blue line
                    List<LatLng> locations = PolyUtil.decode(overview_polyline);
                    Polyline blueline = mMap.addPolyline(new PolylineOptions().add(locations.toArray(new LatLng[locations.size()])).width(20).color(Color.BLUE));
                    polylinearray.get(selectedrouteid).add(blueline);

                    LatLngBounds.Builder builder = new LatLngBounds.Builder();//to know how to zoom the map to fit the polyline

                    for (Polyline a : polylinearray.get(selectedrouteid)) { //show all polylines in current route
                        for (LatLng latlng : a.getPoints()){
                            builder.include(latlng);
                        }
                    }
                    for (Marker marker : RouteMarkers) //add positions of markers
                        builder.include(marker.getPosition());

                    LatLngBounds bounds = builder.build();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 2000, null); //move the map to fit all waypoints

                } catch (JSONException e) {
                    Toast.makeText(getApplicationContext(),R.string.google_server_unavailable, Toast.LENGTH_LONG).show();
                    System.out.println(e);
                }
            }
        }
    }

    public void clearui(Boolean clearpolyline){
        //clear RouteMarkers
        for (Marker a: RouteMarkers){
            a.remove();
        }
        RouteMarkers.clear(); //clear the arraylist
        directionstextarray.clear();
        directionstextarraybackground.clear();
        directionstextid = 0;
        directionstextidpan = 0;
        directionstextview.setText("");
        ETAtextview.setText("");
        ETAtextview.setVisibility(View.INVISIBLE);


        if (clearpolyline){
            backbuttoncode = 0;
            for (ArrayList<Polyline> pl: polylinearray)
                for (Polyline a: pl)
                    a.remove(); //remove polyline
            polylinearray.clear();

            //expand the map view
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            ViewGroup.LayoutParams params = mapFragment.getView().getLayoutParams();
            params.height = (int) Math.floor(displayMetrics.heightPixels);
            mapFragment.getView().setLayoutParams(params);
        }
        autopan = true; //set back to true
        progressbar.setVisibility(View.INVISIBLE);
        runservice = false;
    }



    @Override
    public void onBackPressed() {
        //in case user presses back button during tutorial and he didn't end tutorial
        if (mTourGuideHandler!=null && guideid!=8) {
            mTourGuideHandler.cleanUp();
            guideid = 4;
            guideuser(guideid);
        }

        if (backbuttoncode==0) {
            Toast.makeText(this, R.string.back_again, Toast.LENGTH_SHORT).show();
            if (backpressed){
                super.onBackPressed();
                finishAndRemoveTask();
            }
            backpressed = true;

        }else if (backbuttoncode==1){
            clearui(true); //clear everything including polyline
            backbuttoncode = 0;
        }else{ //backbuttoncode = 2
            routeselected = false;
            drawRoute(selectedrouteid);
            disableenablebuttons(selectedrouteid,routes.length());
            backbuttoncode = 1;
            //stop service
            stopService(new Intent(getApplicationContext(), LocationUpdateService.class));
        }


        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                backpressed=false;
            }
        }, 2000);
    }

    //Getting arrival timings info from server
    private class getarrivaltimings extends AsyncTask<String, Integer, ArrayList<String>> {
        protected ArrayList<String> doInBackground(String... urls) {
            URL url = null;
            String content = "";
            for (int i=0; i<nooftimestoretry; i++){
                HttpURLConnection connection = null;
                StringBuilder sb = null;

                try {
                    System.out.println(serverurl + "/getarrivaltimings/" + urls[0]);
                    url = new URL(serverurl + "/getarrivaltimings/" + urls[0]);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(timeout);
                    connection.setReadTimeout(timeout);
                    connection.connect();

                    InputStream in = connection.getInputStream();
                    InputStreamReader isw = new InputStreamReader(in);
                    sb = new StringBuilder();

                    int data = isw.read();
                    while (data != -1) {
                        char current = (char) data;
                        sb.append(current);
                        data = isw.read();
                    }
                    isw.close(); //close inputstreamreader
                    in.close(); //close inputstream
                    break;
                } catch (IOException e) {
                    System.out.println(e);
                } finally {
                    connection.disconnect();
                    if (sb !=null)
                        content = sb.toString();
                }
            }
            ArrayList<String> myarray = new ArrayList<String>();
            myarray.add(content);
            myarray.add(urls[0]); //add the queried bus stop name
            return myarray;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(ArrayList<String> result) {
            // this is executed on the main thread after the process is over
            // update your UI here

            String arrivaltimings = "";
            //if nothing is return, error
            if(result.get(0).equals("")){
                arrivaltimings = getString(R.string.route_server_unavailable);
            }else{
                arrivaltimings = result.get(0);
            }
            for (Marker marker: BusStopMarkerAL){
                //if marker name matches
                if (marker.getTitle().equals(result.get(1))){
                    marker.setSnippet(arrivaltimings);
                    //set refreshed time
                    lastrefreshed.put(marker.getTitle(), new Date());

                    //ensure map is loaded before calling showInfoWindow
                    mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                        @Override
                        public void onMapLoaded() {
                            marker.showInfoWindow();
                        }
                    });

                }
            }

        }
    }

    public void guideuser(int id){
        Animation animation = new TranslateAnimation(0f, 0f, 200f, 0f);
        animation.setDuration(1000);
        animation.setFillAfter(true);
        animation.setInterpolator(new BounceInterpolator());

        ToolTip toolTip = new ToolTip()
                .setTextColor(getResources().getColor(R.color.text_default))
                .setBackgroundColor(getResources().getColor(R.color.white))
                .setShadow(true)
                .setEnterAnimation(animation);

        mTourGuideHandler = TourGuide.init(this).with(TourGuide.Technique.CLICK);
        //mTourGuideHandler.setOverlay(new Overlay().disableClick(false));
        Pointer pointer = new Pointer();
        pointer.setColor(getResources().getColor(R.color.red));
        mTourGuideHandler.setPointer(pointer);

        if (id==0){ //for search bar
            mTourGuideHandler.setToolTip(toolTip.setTitle(getString(R.string.welcome)).setGravity(Gravity.BOTTOM).setDescription(getString(R.string.search_bar_tap)));
            mTourGuideHandler.playOn(searchbar);

        }else if(id==3){ //for right buttons
            //When there's only one route during tutorial, skip route selection

            if (routes.length()!=0){
                mTourGuideHandler.setToolTip(toolTip.setTitle(getString(R.string.route_selection)).setGravity(Gravity.TOP).setDescription(getString(R.string.route_selection_tap)));
                mTourGuideHandler.playOn(rightbutton);
            }else{
                guideid++;
                guideuser(guideid);
            }

        }else if(id==4){ //for select button

            LinearLayout info = new LinearLayout(getApplicationContext());
            info.setOrientation(LinearLayout.VERTICAL);

            int[] text = new int[]{R.string.green_icon, R.string.blue_icon, R.string.red_icon, R.string.red_icon_default, R.string.white_icon, R.string.bus_stop_icon};
            int[] image = new int[]{R.drawable.buspingreen, R.drawable.buspinblue, R.drawable.buspinred, R.drawable.googlemarker, R.drawable.waypoint, R.drawable.appicon};

            TextView tv = new TextView(getApplicationContext());
            tv.setBackgroundColor(getResources().getColor(R.color.white));
            tv.setGravity(Gravity.CENTER);
            tv.setText(R.string.select_route);
            tv.setTextSize(20);
            tv.setPadding(0,20,0,20);
            info.addView(tv);

            for (int i=0; i<text.length; i++) {
                tv = new TextView(getApplicationContext());
                tv.setBackgroundColor(getResources().getColor(R.color.white));
                tv.setGravity(Gravity.CENTER);
                Drawable img = getResources().getDrawable(image[i]);
                img.setBounds(0, 0, 100, 100);
                tv.setCompoundDrawables(img, null, null, null);
                tv.setText(text[i]);
                tv.setTextSize(14);
                info.addView(tv);
            }

            mTourGuideHandler.setToolTip(toolTip.setCustomView(info).setGravity(Gravity.TOP));
            mTourGuideHandler.playOn(ETAtextview);


        }else if(id==5){ //for route instructions
            try {
                //When there's only one step in the route during tutorial, skip route steps
                if (routes.getJSONObject(String.valueOf(selectedrouteid)).getJSONArray("Route").length() == 1) {
                    guideid++;
                    guideuser(guideid);
                }else{
                    mTourGuideHandler.setToolTip(toolTip.setTitle(getString(R.string.route_instructions)).setGravity(Gravity.TOP).setDescription(getString(R.string.route_instructions_tap)));
                    //to handle route within walking distance so user can dismiss the box by tapping anywhere
                   /* mTourGuideHandler.getOverlay().setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mTourGuideHandler.cleanUp();
                            guideid++;
                            guideuser(guideid);
                        }
                    });*/
                    mTourGuideHandler.playOn(rightbutton);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }else if(id==6){ //for satellite view
            mTourGuideHandler.setToolTip(toolTip.setTitle(getString(R.string.satellite)).setGravity(Gravity.TOP).setDescription(getString(R.string.satellite_tap)));
            mTourGuideHandler.playOn(maptypebutton);

        }else if (id==7){
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.enjoy))
                    .setMessage(getString(R.string.bonus_tip))
                    .setCancelable(false)
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            guideid = 8;
                            editor.putInt("GuideID", guideid); //update flag
                            editor.apply(); //save data
                            clearui(true);
                            maptypebutton.performClick();
                            mTourGuideHandler = null;
                        }
            }).show();
        }
    }

}

