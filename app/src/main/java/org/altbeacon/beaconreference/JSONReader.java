package org.altbeacon.beaconreference;

import android.os.Message;
import android.util.JsonReader;
import android.util.JsonToken;

import org.altbeacon.beacon.Beacon;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jamesthompson on 6/24/16.
 */
public class JSONReader {
    static InputStream is = null;
    static JSONObject jObj = null;
    static String json = "";

    public ArrayList<DisplayBeacon> readJsonStream(InputStream in) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        reader.setLenient(true);

        try {
            return readBeaconsArray(reader);
        } finally {
            reader.close();
        }
    }

    public ArrayList<DisplayBeacon> readBeaconsArray(JsonReader reader) throws IOException {
        ArrayList<DisplayBeacon> beacons = new ArrayList<DisplayBeacon>();

        reader.beginArray();
        while (reader.hasNext()) {
            beacons.add(readBeacon(reader));
        }
        reader.endArray();
        return beacons;
    }

    public DisplayBeacon readBeacon(JsonReader reader) throws IOException {
        String url = "";
        String title = "";
        String description = "";

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("url")) {
                url = reader.nextString();
            } else if (name.equals("title")) {
                title = reader.nextString();
            } else if (name.equals("description")) {
                description = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return new DisplayBeacon(url, title, description);
    }
}
