package com.eric.polygonsdemo;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.orhanobut.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import timber.log.Timber;

import static com.eric.polygonsdemo.MapConstants.HEATMAP_DATA;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int ALPHA_SUBTRACT_MAX = 0xCC000000;// 80%
    private static final int ALPHA_SUBTRACT_MEDIUM = 0x80000000;// 50%
    private static final int ALPHA_UBTRACT_MIN = 0x33000000;// 20%

    private GoogleMap mMap;
    private List<PolygonOptions> polygonOptionsList = new ArrayList<>();
    private boolean arePolygonsShowing = false;
    private List<Polygon> polygonsDrawn = new ArrayList<>();
    private Button ctaPolygons;
    private SimpleCountingIdlingResource mapIdlingResource;
    private double demandMax;
    private double demandMin;
    private double demandTier2;
    private double demandTier3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        initPolygonList();

        ctaPolygons = findViewById(R.id.cta_polygons);
        ctaPolygons.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePolygons();
            }
        });

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mapIdlingResource = new SimpleCountingIdlingResource("mapready");
        mapIdlingResource.increment();
    }

    private void togglePolygons() {
        if (!arePolygonsShowing) {
            showPolygons();
        } else {
            hidePolygons();
        }
        arePolygonsShowing = !arePolygonsShowing;
        ctaPolygons.setText(arePolygonsShowing ? getString(R.string.cta_hide_polygons) :
                getString(R.string
                        .cta_show_polygons));
    }

    private void hidePolygons() {
        for (Polygon polygon : polygonsDrawn) {
            polygon.remove();
        }
        polygonsDrawn.clear();
    }

    private void showPolygons() {
        SimpleCountingIdlingResource idlingResource = new SimpleCountingIdlingResource
                ("showpolygons");
//        idlingResource.increment();
        for (int i = 0; i < polygonOptionsList.size(); i++) {
            polygonsDrawn.add(mMap.addPolygon(polygonOptionsList.get(i)));
        }
//        idlingResource.decrement();
    }

    private void initPolygonList() {
        JSONArray array;
        List<double[]> list = new ArrayList<>();
        List<Double> demandScores = new ArrayList<>();
        try {
            JSONObject tmp = new JSONObject(HEATMAP_DATA);
            array = tmp.getJSONArray("data");
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                String geohash = row.getString("geohash");
                double demand = row.getDouble("score");
                demandScores.add(demand);
                list.add(GeoHashUtils.decode(geohash));
            }
        } catch (JSONException e) {
            Log.e("", "problem", e);
        }
        demandMax = Collections.max(demandScores);
        demandTier2 = demandMin + (demandMax - demandMin) / 3;
        demandTier3 = demandMin + (demandMax - demandMin) * 2 / 3;
        demandMin = Collections.min(demandScores);
        Timber.i("demand thresholds: %s", Arrays.toString(new Double[]{demandMin, demandTier2,
                demandTier3,
                demandMax}));

        polygonOptionsList = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            double[] latlngs = list.get(i);
            List<LatLng> polygon = new ArrayList<>();
            polygon.add(new LatLng(latlngs[1], latlngs[2]));
            polygon.add(new LatLng(latlngs[1], latlngs[3]));
            polygon.add(new LatLng(latlngs[0], latlngs[3]));
            polygon.add(new LatLng(latlngs[0], latlngs[2]));
            polygonOptionsList.add(new PolygonOptions().addAll(polygon)
                    .fillColor(getColour(demandScores.get(i)))
                    .strokeColor(Color.TRANSPARENT)
                    .strokeWidth(0));
        }
        Timber.i("number of polygons: %d", polygonOptionsList.size());
    }

    private int getColour(Double demand) {
        if (BuildConfig.FLAVOR.equalsIgnoreCase("versionB")) {
            return getColourBucket(demand);
        }
        double alpha = (demand - demandMin) / (demandMax - demandMin);
        // put alpha into 0.35-0.9 range
        alpha = (alpha * (0.9 - 0.35)) + 0.35;
        return adjustAlpha(Color.MAGENTA, alpha);
    }

    private int adjustAlpha(int color,
                            double factor) {
        int alpha = (int) Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    private int getColourBucket(double demand) {
        Timber.i("getColourBucket");
        final int color;
        int tier;
        if (Double.compare(demand, demandMin) >= 0 && Double.compare(demand, demandTier2) < 0) {
//            color = Color.MAGENTA - ALPHA_SUBTRACT_MAX;
            color = adjustAlpha(Color.MAGENTA, 0.35);
            tier = 1;
        } else if (Double.compare(demand, demandTier2) >= 0 && Double.compare(demand,
                demandTier3) < 0) {
//            color = Color.MAGENTA - ALPHA_SUBTRACT_MEDIUM;
            color = adjustAlpha(Color.MAGENTA, 0.6);
            tier = 2;
        } else {
//            color = Color.MAGENTA - ALPHA_UBTRACT_MIN;
            color = adjustAlpha(Color.MAGENTA, 0.9);
            tier = 3;
        }
        Timber.i("demand %f belongs to tier %d", demand, tier);
        return color;
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng jakarta = new LatLng(-6.175110, 106.865039);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(jakarta, 10.0f));
        mapIdlingResource.decrement();
    }
}
