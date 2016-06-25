package org.altbeacon.beaconreference;

/**
 * Created by jamesthompson on 6/24/16.
 */
public class DisplayBeacon {
    private String url;
    private String title;
    private String description;

    DisplayBeacon(String u, String t, String d){
        url = u;
        title = t;
        description = d;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return title;
    }
}
