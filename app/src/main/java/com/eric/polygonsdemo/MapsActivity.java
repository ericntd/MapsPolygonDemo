package com.eric.polygonsdemo;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.internal.functions.Functions;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import timber.log.Timber;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private static final int SAMPLE_SIZE = 500;
    private GoogleMap mMap;
    private boolean arePolygonsShowing = false;
    private List<Polygon> polygonsDrawn = new ArrayList<>();
    private Button ctaPolygons;
    private SimpleCountingIdlingResource mapIdlingResource;
    PublishSubject<List<MyPolygon>> polygonData = PublishSubject.create();

    private Disposable disposable;
    private Disposable disposable1;
    private Button ctaPolygonsBatches;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        ctaPolygons = findViewById(R.id.cta_polygons);
        ctaPolygonsBatches = findViewById(R.id.cta_polygons_batches);
        ctaPolygons.setOnClickListener(v -> togglePolygons(false));
        ctaPolygonsBatches.setOnClickListener(v -> togglePolygons(true));

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mapIdlingResource = new SimpleCountingIdlingResource("mapready");
        mapIdlingResource.increment();

        disposable1 = polygonData.flatMap(myPolygons -> {
            Timber.i("consuming %d polygons", myPolygons.size());
            return Observable.fromIterable(myPolygons)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext(myPolygon -> polygonsDrawn.add(mMap.addPolygon(new PolygonOptions().addAll(myPolygon.getLatLngs())
                            .fillColor(myPolygon.getColourFillFinal())
                            .strokeColor(Color.TRANSPARENT)
                            .strokeWidth(0))));
        })
                .subscribe(Functions.emptyConsumer(), Timber::d);
    }

    @Override
    protected void onDestroy() {
        if (disposable != null) { disposable.dispose(); }
        if (disposable1 != null) { disposable1.dispose(); }
        super.onDestroy();
    }

    /**
     * Read polygon data from the geohashes in 5k_geohashes_jakarta.json file
     *
     * @return
     */
    Single<List<MyPolygon>> fetchPolygons() {
        Timber.i("fetchPolygons");
        String data = loadJSONFromAsset();
        try {
            JSONObject jsonObject = new JSONObject(data);
            final JSONArray jsonArray = jsonObject.getJSONArray("scores");
            return Observable.just(jsonArray)
                    .flatMap((Function<JSONArray, ObservableSource<Integer>>) jsonArray1 -> Observable.range(0, jsonArray1.length()))
                    .flatMapSingle((Function<Integer, SingleSource<MyPolygon>>) integer -> {
                        JSONObject row = jsonArray.getJSONObject(integer);
                        String geohash = row.getString("geohash");
                        double opacity = row.getDouble("shade");
                        return GeoHashUtils.decodeGeohash(geohash)
                                .map(doubles -> MyPolygon.create(GeoHashUtils.getPolygonPoints(doubles), 0xFFC32C01, opacity));
                    })
                    .toList();
        } catch (JSONException e) {
            return Single.error(e);
        }
    }

    Observable<List<MyPolygon>> fetchPolygonsRep() {
        return Observable.interval(0, 3L, TimeUnit.MINUTES, Schedulers.io())
                .flatMapSingle(aLong -> fetchPolygons());
    }

    public String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = getAssets().open("5k_geohashes_jakarta.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }


    private void togglePolygons(boolean batch) {
        if (!arePolygonsShowing) {
            showPolygons(batch);
        } else {
            if (disposable != null) { disposable.dispose(); }
            hidePolygons();
        }
        arePolygonsShowing = !arePolygonsShowing;
        ctaPolygons.setText(arePolygonsShowing ? getString(R.string.cta_hide_polygons) : getString(R.string.cta_show_polygons));
        ctaPolygonsBatches.setText(arePolygonsShowing ? getString(R.string.cta_hide_polygons) : getString(R.string
                .cta_show_polygons_batches));
    }

    private void hidePolygons() {
        for (Polygon polygon : polygonsDrawn) {
            polygon.remove();
        }
        polygonsDrawn.clear();
    }

    private void showPolygons(boolean batch) {
        // Prevent multiple subscription when user taps button too fast
        if (disposable != null) { disposable.dispose(); }
        disposable = fetchPolygonsRep().observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> {
                    Timber.w("finally - clearing polygons");
                    hidePolygons();
                })
                .doOnNext(list -> {
                    Timber.w("re-drawing - total number of polygons: %d", list.size());
                    hidePolygons();
                })
                .subscribeOn(Schedulers.io())
                .flatMap((Function<List<MyPolygon>, ObservableSource<MyPolygonBatch>>) list -> {
                    if (batch) { return split(list); }
                    return Observable.just(MyPolygonBatch.create(0, list));
                })
                .concatMap((Function<MyPolygonBatch, ObservableSource<List<MyPolygon>>>) heatmapsPolygonBatch -> {
                    // Start the 1st batch right away, subsequent batches waits 5 seconds
                    long delay = heatmapsPolygonBatch.getBatchId() == 0 ? 0 : 1L;

                    return Observable.just(heatmapsPolygonBatch.getPolygonList())
                            .delay(delay, TimeUnit.SECONDS, Schedulers.io());
                })
                .doOnNext(polygons -> {
                    Timber.i("drawing batch of %d polygons starting with %s", polygons.size(), polygons.get(0)
                            .getLatLngs());
                    drawPolygons(polygons);
                })
                .subscribe(Functions.emptyConsumer(), Timber::e);
    }

    /**
     * Sort the original list
     * and split it into smaller list (each has length of {@link #SAMPLE_SIZE}
     *
     * @param orgList
     * @return
     */
    Observable<MyPolygonBatch> split(final List<MyPolygon> orgList) {
        return Observable.fromIterable(orgList)
                .toSortedList((o1, o2) -> Double.compare(o1.getFillOpacity(), o2.getFillOpacity()))
                .flatMapObservable(sortedList -> {
                    if (sortedList.size() < SAMPLE_SIZE) {
                        return Observable.just(MyPolygonBatch.create(0, sortedList));
                    } else {
                        return Observable.just(sortedList)
                                .concatMap(list -> Observable.range(0, list.size()))
                                .filter(integer -> integer % SAMPLE_SIZE == 0)
                                .map(batchId -> {
                                    final int upperBound = sortedList.size() - batchId;
                                    final int lowerBound = (upperBound - SAMPLE_SIZE) >= 0 ? upperBound - SAMPLE_SIZE : 0;
                                    return MyPolygonBatch.create(batchId, sortedList.subList(lowerBound, upperBound));

                                });
                    }
                });
    }

    private void drawPolygons(List<MyPolygon> polygons) {
        Timber.i("drawPolygons - %d polygons - publish event", polygons.size());
        polygonData.onNext(polygons);
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
