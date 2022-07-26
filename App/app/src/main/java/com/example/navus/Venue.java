package com.example.navus;

public class Venue {
    String Name;
    String Latitude;
    String Longitude;
    String IsBusStop;

    public Venue(){

    }

    public Venue(String name, String latitude, String longitude, String isBusStop) {
        Name = name;
        Latitude = latitude;
        Longitude = longitude;
        IsBusStop = isBusStop;
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
}
