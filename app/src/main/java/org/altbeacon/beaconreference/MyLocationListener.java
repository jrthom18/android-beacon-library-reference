package org.altbeacon.beaconreference;

import android.location.LocationListener;
import android.location.Location;
import android.os.Bundle;

/**
 * Created by jamesthompson on 5/5/16.
 */

/*---------- Listener class to get coordinates ------------- */
class MyLocationListener implements LocationListener {

    private double latitude;
    private double longitude;

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @Override
    public void onLocationChanged(Location loc) {
            setLatitude(loc.getLatitude());
            setLongitude(loc.getLongitude());
    }

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
