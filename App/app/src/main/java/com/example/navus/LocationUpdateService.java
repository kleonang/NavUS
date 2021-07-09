package com.example.navus;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class LocationUpdateService extends Service {
    Notification notification;
    LocationManager locationManager;
    String channelId = "NavUS";
    String channelName = "Navigation";
    ArrayList<String> directionstextarray = new ArrayList<String>();
    ArrayList<LatLng> routelatlngarray = new ArrayList<LatLng>();
    int lastidnotified = -1;

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(NotificationManager notificationManager){
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        directionstextarray = (ArrayList<String>) intent.getExtras().get("directionstextarray");
        routelatlngarray = (ArrayList<LatLng>) intent.getExtras().get("routelatlngarray");

        // Create the Foreground Service
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel(notificationManager) : "";
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
        //to return to activity when user taps the notification
        Intent myintent = new Intent(getApplicationContext(), MapsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, myintent, PendingIntent.FLAG_ONE_SHOT);
        notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.appicon)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.running))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return START_NOT_STICKY;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 10, mLocationListener);


        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY;
    }

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            for (int i=0; i<routelatlngarray.size(); i++) {
                LatLng waypoint = routelatlngarray.get(i);
                Location waypointlocation = new Location("waypoint");
                waypointlocation.setLatitude(waypoint.latitude);
                waypointlocation.setLongitude(waypoint.longitude);

                if (location.distanceTo(waypointlocation) < 200 && lastidnotified<i) {//less than 200m and did not notify before
                    //if directionstext is not empty then notify the user, else it is just a normal bus stop
                    if (!directionstextarray.get(i).equals("")){
                        //send notification
                        Intent intent = new Intent(getApplicationContext(), MapsActivity.class);
                        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, intent, PendingIntent.FLAG_ONE_SHOT);
                        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                                .setSmallIcon(R.drawable.appicon)
                                .setContentTitle(getString(R.string.app_name))
                                .setContentText(directionstextarray.get(i))
                                .setAutoCancel(true)
                                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                                .setContentIntent(pendingIntent);
                        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                        notificationManager.notify(1, notificationBuilder.build()); // 0 is the request code, it should be unique id
                    }
                    lastidnotified=i;
                }

            }
        }

    };

    @Override
    public void onDestroy() {
        locationManager.removeUpdates(mLocationListener);
        locationManager = null;
        stopSelf();
        stopForeground(true);
        super.onDestroy();
    }
}


