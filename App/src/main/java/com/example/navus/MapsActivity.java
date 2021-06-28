package com.example.navus;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
import java.util.List;
import java.util.Map;



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
    ArrayList<Polyline> polylinearray = new ArrayList<Polyline>();
    int directionstextid = 0;
    EditText searchbar;
    int nooftimestoretry = 3, timeout = 5000;
    JSONObject routes;
    boolean routeselected; //flag to know if the arrows is for routing information or selecting the routes
    int selectedrouteid = 0; //to keep track on which route is being selected
    int backbuttoncode = 0; //0 to close app, 1 to return from select route, 2 to return from route to select route
    boolean backpressed = false; //to know if user pressed the back button

    //to handle search
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        searchbar.clearFocus();
        switch (requestCode) {
            case (0): {
                if (resultCode == Activity.RESULT_OK) {

                    //disable the left and right buttons first
                    leftbutton.setEnabled(false);
                    rightbutton.setEnabled(false);

                    clearui(true); //clear everything including polyline

                    //shrink the map view to show directions text
                    DisplayMetrics displayMetrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                    ViewGroup.LayoutParams params = mapFragment.getView().getLayoutParams();
                    params.height = (int) Math.floor(displayMetrics.heightPixels*0.80);
                    mapFragment.getView().setLayoutParams(params);

                    //get source and destination from search activity
                    String sourcename = data.getStringExtra("Source");
                    String destinationname = data.getStringExtra("Destination");

                    if (sourcename==null){ //user did not enter source
                        //get user's location
                        Location userslocation = getLocation();
                        source = new Venue("Source", String.valueOf(userslocation.getLatitude()), String.valueOf(userslocation.getLongitude()), "false");
                    }else{
                        source = new Venue(sourcename, venuedict.get(sourcename).getLatitude(), venuedict.get(sourcename).getLongitude(), venuedict.get(sourcename).getIsBusStop());
                    }


                    //create Venue object based on user's destination
                    destination = new Venue(destinationname, venuedict.get(destinationname).getLatitude(), venuedict.get(destinationname).getLongitude(), venuedict.get(destinationname).getIsBusStop());
                    Location destinationlocation = new Location(LocationManager.GPS_PROVIDER);


                    //Get shortest path from source to destination
                    String url = "http://kleonang.pythonanywhere.com/getpath/" + source.getLatitude() + "/" + source.getLongitude() + "/" + destination.getLatitude() + "/" + destination.getLongitude();
                    //String url = "https://navus-312709.uc.r.appspot.com/getpath/" + source.getLatitude() + "/" + source.getLongitude() + "/" + destination.getLatitude() + "/" + destination.getLongitude();
                    new getbestpath().execute(url);

                    //inform user route is being calculated
                    Toast.makeText(getApplicationContext(),R.string.calculating_route, Toast.LENGTH_SHORT).show();
                    directionstextview.setText(R.string.calculating_route);

                    //set the pin at the user's destination which may not be the destination bus stop
                    LatLng UsersDestination = new LatLng(Double.parseDouble(destination.getLatitude()), Double.parseDouble(destination.getLongitude()));
                    //add the user's destination description when he taps on the pin
                    UsersDestinationMarker = mMap.addMarker(new MarkerOptions().position(UsersDestination).title(destinationname));
                    //set focus on user's destination
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(UsersDestination, 15));
                    routeselected = false; //set flag to false
                    selectedrouteid = 0; //reset to 0
                    backbuttoncode = 1; //user is viewing routes, set to 1
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

        maptypelabel.setText(getResources().getString(R.string.satellite)); //set default text to satellite
        maptypelabel.setTextColor(getResources().getColor(R.color.white)); //set default text to satellite
        getdata(); //get venue and bus stop data

        searchbar.setOnClickListener(new View.OnClickListener() { //wait for user to tap the searchbar
            @Override
            public void onClick(View v) {
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
                if (routeselected){ //button is to show routing information
                    directionstextview.scrollTo(0,0); //scroll to top
                    if (directionstextid > 0){
                        //decrement ID
                        directionstextid--;

                        //disable left button when it is the first element
                        if (directionstextid == 0){
                            leftbutton.setEnabled(false);
                        }
                        rightbutton.setEnabled(true);

                        //update textview
                        directionstextview.setText(directionstextarray.get(directionstextid));

                        for (Marker a: RouteMarkers){
                            //if routemarker text = text in textview
                            if (a.getTitle().equals(directionstextview.getText().toString())){
                                a.showInfoWindow();
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(a.getPosition(), 15));
                                break;
                            }
                        }
                    }
                }else{ //button is used to select routes
                    rightbutton.setEnabled(true);
                    selectedrouteid--;
                    drawRoute(selectedrouteid);

                    if (selectedrouteid == 0) { //disable the left button
                        leftbutton.setEnabled(false);
                    }
                }
            }
        });

        rightbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                directionstextview.scrollTo(0,0); //scroll to top
                if (routeselected) { //button is to show routing information
                    if (directionstextid < directionstextarray.size()-1){
                        //increment ID
                        directionstextid++;

                        //disable right button when it is the last element
                        if (directionstextid == directionstextarray.size()-1){
                            rightbutton.setEnabled(false);
                        }
                        leftbutton.setEnabled(true);
                        //update textview
                        directionstextview.setText(directionstextarray.get(directionstextid));
                        for (Marker a: RouteMarkers){
                            //if routemarker text = text in textview
                            if (a.getTitle().equals(directionstextview.getText().toString())){
                                a.showInfoWindow();
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(a.getPosition(), 15));
                                break;
                            }
                        }
                    }
                }else { //button is used to select routes
                    leftbutton.setEnabled(true);
                    selectedrouteid++;
                    drawRoute(selectedrouteid);

                    if (selectedrouteid == routes.length()-1) {//disable the right button
                        rightbutton.setEnabled(false);
                    }
                }
            }
        });

        ETAtextview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!routeselected) {
                    backbuttoncode = 2;//update the back button code
                    //disable buttons first
                    leftbutton.setEnabled(false);
                    rightbutton.setEnabled(false);

                    routeselected = true; //set flag to true to restore the left/right button functionally for routing steps
                    directionstextview.setText(directionstextarray.get(0)); //set the first step
                    if (directionstextarray.size() > 1) //there are more steps, enable right button
                        rightbutton.setEnabled(true);

                    //update ETA
                    try {
                        ETAtextview.setText(getResources().getString(R.string.eta) + "\n" +routes.getJSONObject(String.valueOf(selectedrouteid)).getString("ETA"));
                        ETAtextview.setBackgroundColor(getResources().getColor(R.color.text_default));
                        ETAtextview.setTextSize(12);
                        Marker source = RouteMarkers.get(0);
                        source.showInfoWindow();//show the first pin information
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(source.getPosition(), 15));//move to the location

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        maptypebutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMap.getMapType() == GoogleMap.MAP_TYPE_NORMAL){ //if normal map set to hybrid
                    mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    maptypebutton.setImageResource(R.drawable.map);
                    maptypelabel.setText(getResources().getString(R.string.map));
                    maptypelabel.setTextColor(getResources().getColor(R.color.text_default));
                }
                else{ //else return to normal
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    maptypebutton.setImageResource(R.drawable.satellite);
                    maptypelabel.setText(getResources().getString(R.string.satellite));
                    maptypelabel.setTextColor(getResources().getColor(R.color.white));
                }
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        String TAG = MapsActivity.class.getName();
        mMap = googleMap;

        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_CODE_ASK_PERMISSIONS);
                LocationEnabled = false;
                return;
            }
        }
        LocationEnabled = true;
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true); //for the crosshair icon on the top right
        mMap.getUiSettings().setMapToolbarEnabled(false); //remove the google maps icon when pin is tapped


        //check night mode
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES:
                googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.night_mode));
                break;
        }


        //to hide all markers when zoomed out
        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
            @Override
            public void onCameraMove() {
                CameraPosition cameraPosition = mMap.getCameraPosition();
                if(cameraPosition.zoom > 12.0) {
                    for (Marker a : BusStopMarkerAL){
                        a.setVisible(true);
                    }
                    for (Marker b : RouteMarkers){
                        b.setVisible(true);
                    }
                }else{
                    for (Marker a : BusStopMarkerAL){
                        a.setVisible(false);
                    }
                    for (Marker b : RouteMarkers) {
                        b.setVisible(false);
                    }
                }
            }
        });
    }

    //updates local database with bus stops and venue data
    public void getdata() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
        database = MyFireBase.getDatabase();
        SharedPreferences sharedpreferences = getSharedPreferences("MySharedPreference", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedpreferences.edit();
        String LastUpdatedDate = sharedpreferences.getString("UpdatedDate", "1970-01-01 00:00:00.00000");

        DatabaseReference LastUpdatedRef = database.getReference("LastUpdated");
        LastUpdatedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    LastUpdated value = userSnapshot.getValue(LastUpdated.class);
                    try {
                        Date UpdatedDate = sdf.parse(value.getUpdatedDate());
                        if (UpdatedDate.after(sdf.parse(LastUpdatedDate))) {//new updates
                            refreshdata();
                            System.out.println("REFRESHING DATA");
                        } else {
                            getdatafromsql();
                            System.out.println("UP TO DATE");
                        }
                        editor.putString("UpdatedDate", sdf.format(new Date())); //set current datetime
                        editor.commit(); //save data
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
                if (cursor.getString(3).equals("true")){ //if is busstop
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
        LatLng latLng = new LatLng(1,1);
        for (Venue a : BusStopAL) {
            latLng = new LatLng(Double.parseDouble(a.getLatitude()), Double.parseDouble(a.getLongitude()));
            BitmapDrawable bitmapdraw = (BitmapDrawable)getResources().getDrawable(R.drawable.appicon);
            Bitmap b=bitmapdraw.getBitmap();
            Bitmap finalMarker=Bitmap.createScaledBitmap(b, 40, 40, false);
            Marker BusStopMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(a.Name).icon(BitmapDescriptorFactory.fromBitmap(finalMarker)));
            //Marker BusStopMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(a.Name).icon(BitmapDescriptorFactory.fromResource(R.drawable.busstop)));
            BusStopMarkerAL.add(BusStopMarker);
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
    }

    //to request for location permission
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation();
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
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.enable_location, Toast.LENGTH_SHORT).show();
            return null;
        }
        Location myLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (myLocation == null) {
            myLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }
        return myLocation;
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
            if (result==""){//Error accessing server
                Toast.makeText(getApplicationContext(),R.string.route_server_unavailable, Toast.LENGTH_LONG).show();
                directionstextview.setText(R.string.route_server_unavailable);
            }else{
                try {
                    routes = new JSONObject(result);
                    drawRoute(0); //draw the route 0
                } catch (JSONException e) {
                    System.out.println(e);
                }
            }
        }
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

            BitmapDrawable waypointbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.waypoint);
            BitmapDrawable redpinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspinred);
            BitmapDrawable greenpinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspingreen);
            BitmapDrawable bluepinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspinblue);
            Bitmap waypointpin = Bitmap.createScaledBitmap(waypointbitmap.getBitmap(), 50, 50, false);
            Bitmap redpin = Bitmap.createScaledBitmap(redpinbitmap.getBitmap(), 100, 100, false);
            Bitmap greenpin = Bitmap.createScaledBitmap(greenpinbitmap.getBitmap(), 100, 100, false);
            Bitmap bluepin = Bitmap.createScaledBitmap(bluepinbitmap.getBitmap(), 100, 100, false);

            //if not route found
            if (WayPointJSONArray.length()==0){
                directionstextview.setText(R.string.no_route_found);
                ETAtextview.setVisibility(View.INVISIBLE);
                Toast.makeText(getApplicationContext(),R.string.no_route_found, Toast.LENGTH_LONG).show();

            }else if(WayPointJSONArray.length()==1){ //user can walk there
                directionstextarray.add(getResources().getString(R.string.walk_to_destination));
                UsersDestinationMarker.showInfoWindow();
                Marker sourcemarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(source.getLatitude()),Double.parseDouble(source.getLongitude()))).title(getString(R.string.walk_to_destination)).icon(BitmapDescriptorFactory.fromBitmap(greenpin)));
                RouteMarkers.add(sourcemarker);//add to RouteMarkers

                //create route using user's source and destination
                WayPoint sourcewaypoint = new WayPoint(source.getName(), source.getLatitude(), source.getLongitude(), source.getIsBusStop(), "", "0");
                WayPoint destinationwaypoint = new WayPoint(destination.getName(), destination.getLatitude(), destination.getLongitude(), destination.getIsBusStop(), "", "0");
                routeinfo.add(sourcewaypoint);
                routeinfo.add(destinationwaypoint);

                //check if polyline already exist
                if (polylinearray.size() <= routeno){
                    // Getting URL for the Google Directions API
                    String url = getDirectionsUrl(routeinfo, "walking");
                    //draw the blueline
                    new getdirections().execute(url);
                }
                for (int i=0; i<polylinearray.size(); i++){
                    if (i==routeno) {
                        polylinearray.get(i).setVisible(true); //show the current one
                        for (LatLng latlng : polylinearray.get(i).getPoints())
                            builder.include(latlng);
                        LatLngBounds bounds = builder.build();
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 2000, null); //move the map to fit all waypoints
                    }else{
                        polylinearray.get(i).setVisible(false); //hide the rest
                    }
                }

            }else{
                JSONObject JSONObj;
                for (int itemIndex=0, totalcount = WayPointJSONArray.length(); itemIndex < totalcount; itemIndex++) {
                    JSONObj = WayPointJSONArray.getJSONObject(itemIndex);
                    WayPoint waypoint = new WayPoint(JSONObj.getString("Name"), JSONObj.getString("Latitude"), JSONObj.getString("Longitude"), JSONObj.getString("IsBusStop"), JSONObj.getString("Service"), JSONObj.getString("BusArrivalTime"));
                    routeinfo.add(waypoint);//add to route info
                }

                //check if polyline already exist
                if (polylinearray.size() <= routeno){
                    // Getting URL for the Google Directions API
                    String url = getDirectionsUrl(routeinfo, "driving");
                    //draw the blueline
                    new getdirections().execute(url);
                }
                for (int i=0; i<polylinearray.size(); i++){
                    if (i==routeno) {
                        polylinearray.get(i).setVisible(true); //show the current one
                        for (LatLng latlng : polylinearray.get(i).getPoints())
                            builder.include(latlng);
                        LatLngBounds bounds = builder.build();
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100), 2000, null); //move the map to fit all waypoints
                    }else{
                        polylinearray.get(i).setVisible(false); //hide the rest
                    }
                }


                //plot the pins
                String directionstext;
                Boolean markershown = false; //flag to know if marker was shown

                for (int i=0; i<routeinfo.size(); i++){
                    Marker BusStopMarker = null;
                    WayPoint currentbusstop = routeinfo.get(i);
                    //source bus stop
                    if (i == 0) {
                        //add walk instructions if source bus stop is not the user's location
                        if (Double.parseDouble(source.getLatitude()) != Double.parseDouble(currentbusstop.getLatitude()) ||  Double.parseDouble(source.getLongitude()) != Double.parseDouble(currentbusstop.getLongitude())){
                            directionstext = getString(R.string.head_to, currentbusstop.getName());
                            directionstextarray.add(directionstext);
                            BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(directionstext).icon(BitmapDescriptorFactory.fromBitmap(greenpin)));
                            RouteMarkers.add(BusStopMarker);
                            //show first stop information
                            BusStopMarker.showInfoWindow();
                            markershown = true;
                        }

                        directionstext = getString(R.string.board, currentbusstop.getService(), currentbusstop.getBusArrivalTime(), currentbusstop.getName());
                        BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(directionstext).icon(BitmapDescriptorFactory.fromBitmap(greenpin)));
                        if (!markershown){
                            //show first stop information
                            BusStopMarker.showInfoWindow();
                        }

                        //add string to array for the buttons
                        directionstextarray.add(directionstext);

                    }else if(i == routeinfo.size()-1){//reached destination bus stop
                        //remove user's destination if it is a bus stop
                        //if (UsersDestinationMarker!=null && UsersDestinationMarker.getTitle().equals(currentbusstop.getName())){
                        UsersDestinationMarker.remove();
                        //}
                        directionstext = getString(R.string.alight, currentbusstop.getName());
                        BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(directionstext).icon(BitmapDescriptorFactory.fromBitmap(redpin)));
                        //add string to array for the buttons
                        directionstextarray.add(directionstext);

                        //add walk instructions if destination bus stop is not the user's destination
                        if (Double.parseDouble(destination.getLatitude()) != Double.parseDouble(currentbusstop.getLatitude()) ||  Double.parseDouble(destination.getLongitude()) != Double.parseDouble(currentbusstop.getLongitude())){
                            RouteMarkers.add(BusStopMarker);
                            directionstext = getString(R.string.walk_from, currentbusstop.getName(), destination.getName());
                            directionstextarray.add(directionstext);
                            BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(destination.getLatitude()), Double.parseDouble(destination.getLongitude()))).title(directionstext));
                        }

                    }else if (currentbusstop.getName().equals(routeinfo.get(i-1).getName())){//if same busstop name means transfer required
                        //remove old marker first
                        RouteMarkers.get(RouteMarkers.size()-1).remove(); //remove the previous pin
                        RouteMarkers.remove(RouteMarkers.size()-1); //delete from array
                        directionstext = getString(R.string.transfer,routeinfo.get(i-1).getService(), currentbusstop.getService(), currentbusstop.getName(), currentbusstop.getBusArrivalTime());
                        BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(directionstext).icon(BitmapDescriptorFactory.fromBitmap(bluepin))); //set blue pin if need to transfer bus
                        //add string to array for the buttons
                        directionstextarray.add(directionstext);
                        nooftransits++;
                    }else{
                        BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(currentbusstop.getName()).icon(BitmapDescriptorFactory.fromBitmap(waypointpin))); //else white circle
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
                directionstextview.setText(Html.fromHtml(getString(R.string.route, selectedrouteid+1, routes.length(), ETA, nooftransits)));
                ETAtextview.setText(R.string.select);
                ETAtextview.setTextSize(14);
                ETAtextview.setTextColor(getResources().getColor(R.color.white));
                ETAtextview.setBackgroundColor(getResources().getColor(R.color.skyblue));
            }


        } catch (Exception e) {
            System.out.println(e);
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
            if (i==0){//source
                str_origin = "origin=" + routeinfo.get(i).getLatitude() + "," + routeinfo.get(i).getLongitude();
            }else if(i==routeinfo.size()-1){//destination
                str_dest = "destination=" + routeinfo.get(i).getLatitude() + "," + routeinfo.get(i).getLongitude();
            }else if(!previousbusstopname.equals(routeinfo.get(i).getName())){ //remove duplicates in the event of transfer
                waypointsstring += routeinfo.get(i).getLatitude() + "," + routeinfo.get(i).getLongitude() + "|";
            }
            if (previousbusstopname.equals("S 17") && routeinfo.get(i).getName().equals("LT 27")){
                waypointsstring += "1.296827,103.783008|";
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
                    polylinearray.add(blueline);

                    LatLngBounds.Builder builder = new LatLngBounds.Builder();//to know how to zoom the map to fit the polyline
                    for (LatLng latlng: locations)
                        builder.include(latlng);
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
        directionstextid = 0;
        directionstextview.setText("");
        ETAtextview.setText("");
        ETAtextview.setVisibility(View.VISIBLE);
        if (clearpolyline){
            for (Polyline pl: polylinearray)
                pl.remove(); //remove polyline
            polylinearray.clear();

            //expand the map view
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            ViewGroup.LayoutParams params = mapFragment.getView().getLayoutParams();
            params.height = (int) Math.floor(displayMetrics.heightPixels);
            mapFragment.getView().setLayoutParams(params);
        }
    }



    @Override
    public void onBackPressed() {
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
            if (selectedrouteid != 0) { //enable the left button is the selected route is not the first one
                leftbutton.setEnabled(true);
            }else{
                leftbutton.setEnabled(false);
            }
            if (selectedrouteid == routes.length()-1) {//disable the right button is selected route is the last one
                rightbutton.setEnabled(false);
            }else{
                rightbutton.setEnabled(true);
            }
            backbuttoncode = 1;
        }


        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                backpressed=false;
            }
        }, 2000);
    }

}

