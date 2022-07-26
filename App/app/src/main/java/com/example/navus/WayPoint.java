package com.example.navus;

public class WayPoint {
    String Name;
    String Latitude;
    String Longitude;
    String IsBusStop;
    String Service;
    String BusArrivalTime;
    String BusArrivalTimeMins;

    public WayPoint() {
    }

    public WayPoint(String name, String latitude, String longitude, String isBusStop, String service, String busArrivalTime, String busArrivalTimeMins) {
        Name = name;
        Latitude = latitude;
        Longitude = longitude;
        IsBusStop = isBusStop;
        Service = service;
        BusArrivalTime = busArrivalTime;
        BusArrivalTimeMins = busArrivalTimeMins;
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

    public String getBusArrivalTime() {
        return BusArrivalTime;
    }

    public void setBusArrivalTime(String busArrivalTime) {
        BusArrivalTime = busArrivalTime;
    }

    public String getBusArrivalTimeMins() {
        return BusArrivalTimeMins;
    }

    public void setBusArrivalTimeMins(String busArrivalTimeMins) {
        BusArrivalTimeMins = busArrivalTimeMins;
    }
}
