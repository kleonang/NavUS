package com.example.navus;

public class WayPoint {
    String Name;
    String Latitude;
    String Longitude;
    String IsBusStop;
    String Service;

    public WayPoint() {
    }

    public WayPoint(String name, String latitude, String longitude, String isBusStop, String service) {
        Name = name;
        Latitude = latitude;
        Longitude = longitude;
        IsBusStop = isBusStop;
        Service = service;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getLatitude() {
        return Latitude;
    }

    public void setLatitude(String latitude) {
        Latitude = latitude;
    }

    public String getLongitude() {
        return Longitude;
    }

    public void setLongitude(String longitude) {
        Longitude = longitude;
    }

    public String getIsBusStop() {
        return IsBusStop;
    }

    public void setIsBusStop(String isBusStop) {
        IsBusStop = isBusStop;
    }

    public String getService() {
        return Service;
    }

    public void setService(String service) {
        Service = service;
    }
}
