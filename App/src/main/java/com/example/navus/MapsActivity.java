package com.example.navus;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.internal.Constants;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
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
import java.io.BufferedReader;
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
    private Polyline mPolyline;
    FirebaseDatabase database;
    LatLng OriginBusStop;
    LatLng DestinationBusStop;
    ArrayList<Marker> BusStopMarkerAL = new ArrayList<Marker>();
    ArrayList<Marker> RouteMarkers = new ArrayList<Marker>();
    Polyline blueline;
    SupportMapFragment mapFragment;
    TextView directionstext;
    Boolean LocationEnabled = false;
    Map<String, Venue> venuedict = new HashMap<String, Venue>();

    //to handle search
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case (0): {
                if (resultCode == Activity.RESULT_OK) {
                    //Close the SearchView after search
                    SearchView searchView = findViewById(R.id.searchbutton);
                    searchView.setQuery("", false);
                    searchView.setIconified(true);

                    //clear RouteMarkers
                    for (Marker a: RouteMarkers){
                        a.remove();
                    }
                    RouteMarkers.clear(); //clear the arraylist
                    if (blueline!=null)
                        blueline.remove(); //remove polyline

                    //shrink the map view to show directions text
                    DisplayMetrics displayMetrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                    ViewGroup.LayoutParams params = mapFragment.getView().getLayoutParams();
                    params.height = (int) Math.floor(displayMetrics.heightPixels*0.85);
                    mapFragment.getView().setLayoutParams(params);

                    //get source and destination from search activity
                    String sourcename = data.getStringExtra("Source");
                    String destinationname = data.getStringExtra("Destination");
                    Venue OriginClosestBusStop;

                    if (sourcename==null){ //user did not enter source
                        //get closest bus stop based on user's location
                        Location userlocation = getLocation();
                        OriginClosestBusStop = getclosestbusstop(userlocation);
                    }else{
                        //create Location object based on user's source
                        Location sourcelocation = new Location(LocationManager.GPS_PROVIDER);
                        //set latitude and longitude on location object
                        sourcelocation.setLatitude(Double.parseDouble(venuedict.get(sourcename).getLatitude()));
                        sourcelocation.setLongitude(Double.parseDouble(venuedict.get(sourcename).getLongitude()));
                        OriginClosestBusStop = getclosestbusstop(sourcelocation);
                    }

                    //create LatLng object based on closest origin bus stop
                    OriginBusStop = new LatLng(Double.parseDouble(OriginClosestBusStop.getLatitude()), Double.parseDouble(OriginClosestBusStop.getLongitude()));
                    //create Location object based on user's destination
                    Location destinationlocation = new Location(LocationManager.GPS_PROVIDER);
                    //set latitude and longitude on location object
                    destinationlocation.setLatitude(Double.parseDouble(venuedict.get(destinationname).getLatitude()));
                    destinationlocation.setLongitude(Double.parseDouble(venuedict.get(destinationname).getLongitude()));

                    //get closest bus stop based on user's destination
                    Venue DestinationClosestBusStop = getclosestbusstop(destinationlocation);
                    //create LatLng object based on closest destination bus stop
                    DestinationBusStop = new LatLng(Double.parseDouble(DestinationClosestBusStop.getLatitude()), Double.parseDouble(DestinationClosestBusStop.getLongitude()));
                    //Get shortest path from source to destination
                    String url = "http://192.168.1.126:5000/getpath/" + OriginClosestBusStop.getName().replaceAll("\\s", "%20") + "/" + DestinationClosestBusStop.getName().replaceAll("\\s", "%20");
                    new getbestpath().execute(url);

                    //set the pin at the user's destination which may not be the destination bus stop
                    LatLng UsersDestination = new LatLng(Double.parseDouble(venuedict.get(destinationname).getLatitude()), Double.parseDouble(venuedict.get(destinationname).getLongitude()));
                    //add the user's destination description when he taps on the pin
                    UsersDestinationMarker = mMap.addMarker(new MarkerOptions().position(UsersDestination).title(destinationname));
                    //set focus on user's destination
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(UsersDestination, 15));
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

        SearchView searchView = findViewById(R.id.searchbutton);
        directionstext = (TextView)findViewById(R.id.directionstext);

        searchView.setOnClickListener(new View.OnClickListener() { //wait for user to tap the searchbar
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

        searchView.setOnSearchClickListener(new View.OnClickListener() { //wait for user to tap the magnifying glass
            @Override
            public void onClick(View v) {
                searchView.performClick();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        String TAG = MapsActivity.class.getName();
        mMap = googleMap;
        getdata(); //get venue and bus stop data

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
        database = FirebaseDatabase.getInstance(APIKeys.FirebaseURL);//connect to firebase
        database.setPersistenceEnabled(true);
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

    //returns the closest bus stop based on provided location
    public Venue getclosestbusstop(Location location) {
        Venue closestbusstop = new Venue();
        if (location != null) {
            double bestsum = 100;
            for (Venue a : BusStopAL) {
                double currentsum = Math.abs(location.getLatitude() - Double.parseDouble(a.getLatitude())) + Math.abs(location.getLongitude() - Double.parseDouble(a.getLongitude()));
                if (currentsum < bestsum) {
                    closestbusstop = a;
                    bestsum = currentsum;
                }
            }
        }
        return closestbusstop;
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
                directionstext.setText(R.string.enable_location);
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
            String content = "", line;
            try {
                System.out.println(urls[0]);
                url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            while ((line = rd.readLine()) != null) {
                content += line + "\n";
            }
            rd.close();
            } catch (IOException e) {
                System.out.println(R.string.server_unavailable);
            }
            return content;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(String result) {
            // this is executed on the main thread after the process is over
            // update your UI here
            if (result==""){//Error accessing server
                Toast.makeText(getApplicationContext(),R.string.server_unavailable, Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(getApplicationContext(),R.string.calculating_route, Toast.LENGTH_LONG).show();
                drawRoute(result);
            }
        }
    }

    //plots the bus stop along the route
    private void drawRoute(String resultfromserver){
        try {
            //Process the bus stops for the shortest path from the server
            ArrayList<WayPoint> routeinfo = new ArrayList<WayPoint>();
            JSONObject json = new JSONObject(resultfromserver);
            JSONArray WayPointJSONArray = json.getJSONArray("Waypoints");
            JSONObject JSONObj;
            for (int itemIndex=0, totalcount = WayPointJSONArray.length(); itemIndex < totalcount; itemIndex++) {
                JSONObj = WayPointJSONArray.getJSONObject(itemIndex);
                WayPoint waypoint = new WayPoint(JSONObj.getString("Name"), JSONObj.getString("Latitude"), JSONObj.getString("Longitude"), JSONObj.getString("IsBusStop"), JSONObj.getString("Service"));
                routeinfo.add(waypoint);//add to route info
            }

            // Getting URL for the Google Directions API
            String url = getDirectionsUrl(routeinfo);
            new getdirections().execute(url);

            //plot the pins
            BitmapDrawable blackpinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspinblack);
            BitmapDrawable redpinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspinred);
            BitmapDrawable greenpinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspingreen);
            BitmapDrawable bluepinbitmap = (BitmapDrawable)getResources().getDrawable(R.drawable.buspinblue);
            Bitmap blackpin = Bitmap.createScaledBitmap(blackpinbitmap.getBitmap(), 100, 100, false);
            Bitmap redpin = Bitmap.createScaledBitmap(redpinbitmap.getBitmap(), 100, 100, false);
            Bitmap greenpin = Bitmap.createScaledBitmap(greenpinbitmap.getBitmap(), 100, 100, false);
            Bitmap bluepin = Bitmap.createScaledBitmap(bluepinbitmap.getBitmap(), 100, 100, false);

            for (int i=0; i<routeinfo.size(); i++){
                Marker BusStopMarker = null;
                WayPoint currentbusstop = routeinfo.get(i);
                //source bus stop
                if (i == 0) {
                    BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title("Board " + currentbusstop.getService() + " at " + currentbusstop.getName()).icon(BitmapDescriptorFactory.fromBitmap(greenpin)));
                    directionstext.setText("Board " + currentbusstop.getService() + " at " + currentbusstop.getName());
                    //show first stop information
                    BusStopMarker.showInfoWindow();
                }else if(i == routeinfo.size()-1){//reached destination bus stop
                    //do not plot pin if user's destination is a bus stop
                    if (UsersDestinationMarker!=null && !UsersDestinationMarker.getTitle().equals(currentbusstop.getName())){
                        BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title("Alight at " + currentbusstop.getName()).icon(BitmapDescriptorFactory.fromBitmap(redpin)));
                    }
                }else if (currentbusstop.getName().equals(routeinfo.get(i-1).getName())){//if same busstop name means transfer required
                    //remove old marker first
                    RouteMarkers.get(RouteMarkers.size()-1).remove(); //remove the previous pin
                    RouteMarkers.remove(RouteMarkers.size()-1); //delete from array
                    BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title("Transfer from " + routeinfo.get(i-1).getService() + " to " + currentbusstop.getService() + " at " + currentbusstop.getName()+".").icon(BitmapDescriptorFactory.fromBitmap(bluepin))); //set blue pin if need to transfer bus
                }else{
                    BusStopMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(currentbusstop.getLatitude()),Double.parseDouble(currentbusstop.getLongitude()))).title(currentbusstop.getName()).icon(BitmapDescriptorFactory.fromBitmap(blackpin))); //else default black pin
                }
                if (BusStopMarker!=null){
                    RouteMarkers.add(BusStopMarker);//add to RouteMarkers
                }
            }

        } catch (JSONException e) {
            System.out.println(e);
        }

    }

    private String getDirectionsUrl(ArrayList<WayPoint> routeinfo){
        String str_origin = "";
        String str_dest = "";
        // Key
        String key = "key=" + getString(R.string.google_maps_key);

        // Waypoints
        String waypointsstring = "";
        String previousbusstopname="";

        for (int i=0; i<routeinfo.size(); i++){
            if (i==0){//source
                str_origin = "origin=" + routeinfo.get(i).getLatitude() + "," + routeinfo.get(i).getLongitude();
            }else if(i==routeinfo.size()-1){//destination
                str_dest = "destination=" + routeinfo.get(i).getLatitude() + "," + routeinfo.get(i).getLongitude();
            }else if(!previousbusstopname.equals(routeinfo.get(i).getName())){ //remove duplicates in the event of transfer
                waypointsstring += routeinfo.get(i).getLatitude() + "," + routeinfo.get(i).getLongitude() + "|";
            }
            previousbusstopname = routeinfo.get(i).getName();
        }

        // Building the parameters to the web service
        String parameters = str_origin+"&"+str_dest+"&"+key+"&waypoints="+waypointsstring;

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/json?"+parameters;
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
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((line = rd.readLine()) != null) {
                    content += line + "\n";
                }
                rd.close();
            } catch (IOException e) {
                System.out.println(R.string.server_unavailable);
            }
            return content;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(String result) {
            // this is executed on the main thread after the process is over
            // update your UI here
            if (result==""){//Error accessing server
                Toast.makeText(getApplicationContext(),R.string.server_unavailable, Toast.LENGTH_LONG).show();
            }else{
                try {
                    JSONObject json = new JSONObject(result);
                    //Routes array is always of length 1 so index 0
                    String overview_polyline = json.getJSONArray("routes").optJSONObject(0).optJSONObject("overview_polyline").getString("points");

                    //draw the blue line
                    List<LatLng> locations = PolyUtil.decode(overview_polyline);
                    blueline = mMap.addPolyline(new PolylineOptions().add(locations.toArray(new LatLng[locations.size()])).width(5).color(Color.BLUE));
                } catch (JSONException e) {
                    Toast.makeText(getApplicationContext(),R.string.server_unavailable, Toast.LENGTH_LONG).show();
                    System.out.println(e);
                }
            }
        }
    }

}

