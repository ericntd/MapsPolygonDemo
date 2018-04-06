package com.eric.polygonsdemo;

import android.graphics.Color;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.eric.polygonsdemo.MapConstants.HEATMAP_DATA;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private List<PolygonOptions> polygonOptionsList = new ArrayList<>();
    private boolean arePolygonsShowing = false;
    private List<Polygon> polygonsDrawn = new ArrayList<>();
    private Button ctaPolygons;

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
    }

    private void togglePolygons() {
        if (!arePolygonsShowing) {
            showPolygons();
        } else {
            hidePolygons();
        }
        arePolygonsShowing = !arePolygonsShowing;
        ctaPolygons.setText(arePolygonsShowing ? "Hide polygons" : "Show polygons");
    }

    private void hidePolygons() {
        for (Polygon polygon : polygonsDrawn) {
            polygon.remove();
        }
        polygonsDrawn.clear();
    }

    private void showPolygons() {
        for (int i = 0; i < polygonOptionsList.size(); i++) {
            polygonsDrawn.add(mMap.addPolygon(polygonOptionsList.get(i)));
        }
    }

    private void initPolygonList() {
        JSONArray array;
        List<double[]> list = new ArrayList<>();
        try {
            JSONObject tmp = new JSONObject(HEATMAP_DATA);
            array = tmp.getJSONArray("data");
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                String geohash = row.getString("geohash");
                list.add(GeoHashUtils.decode(geohash));
            }
        } catch (JSONException e) {
            Log.e("", "problem", e);
        }

        polygonOptionsList = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            double[] latlngs = list.get(i);
            List<LatLng> polygon = new ArrayList<>();
            polygon.add(new LatLng(latlngs[1], latlngs[2]));
            polygon.add(new LatLng(latlngs[1], latlngs[3]));
            polygon.add(new LatLng(latlngs[0], latlngs[3]));
            polygon.add(new LatLng(latlngs[0], latlngs[2]));
            int randomNum = new Random().nextInt(2);
            int alphaAdjustment = 0x77000000;
            int color = Color.YELLOW - alphaAdjustment;
            switch (randomNum) {
                case 2:
                    color = Color.RED - alphaAdjustment;
                    break;
                case 1:
                    color = Color.GREEN - alphaAdjustment;
                    break;
                case 0:
                    color = Color.BLUE - alphaAdjustment;
            }
            polygonOptionsList.add(new PolygonOptions().addAll(polygon)
                    .fillColor(color)
                    .strokeColor(Color.TRANSPARENT)
                    .strokeWidth(0));
        }
        Log.i("", String.format("number of polygons: %d", polygonOptionsList.size()));
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
    }
}
