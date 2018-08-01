package com.eric.polygonsdemo;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.util.Pair;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polygon;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.functions.Functions;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import timber.log.Timber;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap
        .OnCameraMoveListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnCameraMoveCanceledListener {
    private static final int SAMPLE_SIZE = 500;
    private GoogleMap mMap;
    private boolean arePolygonsShowing = false;
    private List<Polygon> polygonsDrawn = new ArrayList<>();
    private List<GroundOverlay> groundOverlayList = new ArrayList<>();
    private Map<MyPolygon, GroundOverlay> geohashCache = new HashMap<>();
    private Button ctaPolygons;
    private Button ctaRefresh;
    PublishSubject<List<MyPolygon>> polygonData = PublishSubject.create();
    List<MyPolygon> polygonData2 = new ArrayList<>();
    PublishSubject<Boolean> cameraIdle = PublishSubject.create();

    private Disposable disposable;
    private Disposable disposable1;
    private Button ctaPolygonsBatches;
    private Disposable disposable2;
    private boolean batchDrawing = true;
    private boolean shouldRepeat = false;
    private Button ctaToggleRepeat;
    private LatLng topLeft;
    private LatLng bottomRight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        ctaToggleRepeat = findViewById(R.id.cta_toggle_loop);
        ctaToggleRepeat.setOnClickListener(v -> {
            shouldRepeat = !shouldRepeat;
            ctaToggleRepeat.setText(shouldRepeat ? getString(R.string.repeat) : getString(R.string.not_repeating));
        });
        ctaRefresh = findViewById(R.id.cta_refresh);
        ctaRefresh.setOnClickListener(v -> showPolygons());
        ctaPolygons = findViewById(R.id.cta_polygons);
        ctaPolygonsBatches = findViewById(R.id.cta_polygons_batches);
        ctaPolygons.setOnClickListener(v -> {
            batchDrawing = false;
            togglePolygons();
        });
        ctaPolygonsBatches.setOnClickListener(v -> {
            batchDrawing = true;
            togglePolygons();
        });

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //        disposable1 = polygonData.flatMap(myPolygons -> {
        //            Timber.i("consuming %d polygons", myPolygons.size());
        //            mMap.setTrafficEnabled(true);
        //            AtomicInteger drawnCount = new AtomicInteger();
        //            return Observable.fromIterable(myPolygons)
        //                    .subscribeOn(Schedulers.io())
        //                    .observeOn(AndroidSchedulers.mainThread())
        //                    .doOnNext(myPolygon -> {
        //                        final boolean isMyPolygonVisible = isVisible(myPolygon);
        //                        if (isMyPolygonVisible && geohashCache.get(myPolygon) == null) {
        //                            drawnCount.getAndIncrement();
        //                            final LatLngBounds latLngBounds = new LatLngBounds(myPolygon.getLatLngs()
        //                                    .get(3), myPolygon.getLatLngs()
        //                                    .get(1));
        //                            final GroundOverlayOptions overlayOptions = new GroundOverlayOptions().image(BitmapDescriptorFactory
        //                                    .fromResource(R.drawable.polygon_colour))
        //                                    .transparency(myPolygon.getFillOpacity())
        //                                    .positionFromBounds(latLngBounds);
        //                            final GroundOverlay groundOverlay = mMap.addGroundOverlay(overlayOptions);
        //                            geohashCache.put(myPolygon, groundOverlay);
        //                        }
        //                        //                            polygonsDrawn.add(mMap.addPolygon(new PolygonOptions().addAll(myPolygon
        //                        // .getLatLngs())
        //                        //                                    .fillColor(myPolygon.getColourFillFinal())
        //                        //                                    .strokeColor(Color.TRANSPARENT)
        //                        //                                    .strokeWidth(0)));
        //                    })
        //                    .doOnComplete(() -> Timber.i("Number of polygons actually drawn due to visibility is %d", drawnCount.get()));
        //        })
        //                .subscribe(Functions.emptyConsumer(), Timber::d);

        disposable1 = cameraIdle.startWith(true)
                .filter(o -> o)
                .throttleFirst(500L, TimeUnit.MILLISECONDS, Schedulers.io())
                .flatMap((Function<Boolean, ObservableSource<MyPolygon>>) aBoolean -> {
                    mMap.setTrafficEnabled(true);
                    AtomicInteger drawnCount = new AtomicInteger();
                    return Observable.fromIterable(polygonData2)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnNext(myPolygon -> {
                                final boolean isMyPolygonVisible = isVisible(myPolygon);
                                if (isMyPolygonVisible && geohashCache.get(myPolygon) == null) {
                                    drawnCount.getAndIncrement();
                                    final LatLngBounds latLngBounds = new LatLngBounds(myPolygon.getLatLngs()
                                            .get(3), myPolygon.getLatLngs()
                                            .get(1));
                                    final GroundOverlayOptions overlayOptions = new GroundOverlayOptions().image(BitmapDescriptorFactory
                                            .fromResource(R.drawable.polygon_colour))
                                            .transparency(myPolygon.getFillOpacity())
                                            .positionFromBounds(latLngBounds);
                                    final GroundOverlay groundOverlay = mMap.addGroundOverlay(overlayOptions);
                                    geohashCache.put(myPolygon, groundOverlay);
                                }

                                //                            polygonsDrawn.add(mMap.addPolygon(new PolygonOptions().addAll(myPolygon
                                // .getLatLngs())
                                //                                    .fillColor(myPolygon.getColourFillFinal())
                                //                                    .strokeColor(Color.TRANSPARENT)
                                //                                    .strokeWidth(0)));
                            })
                            .doOnComplete(() -> Timber.i("Number of polygons actually drawn due to visibility is %d", drawnCount.get()));
                })
                .subscribe(Functions.emptyConsumer(), Timber::d);

        disposable1 = Observable.combineLatest(cameraIdle.filter(o -> o)
                .throttleFirst(1L, TimeUnit.SECONDS), polygonData.filter(myPolygons -> !myPolygons.isEmpty()), Pair::create)
                .flatMap((Function<Pair<Boolean, List<MyPolygon>>, ObservableSource<MyPolygon>>) booleanListPair -> {
                    Timber.i("camera is idle and there are polygons yaY!");
                    Timber.i("consuming %d polygons in total", booleanListPair.second.size());
                    AtomicInteger drawnCount = new AtomicInteger();
                    return Observable.fromIterable(booleanListPair.second)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnNext(myPolygon -> {
                                final boolean isMyPolygonVisible = isVisible(myPolygon);
                                if (isMyPolygonVisible && geohashCache.get(myPolygon) == null) {
                                    drawnCount.getAndIncrement();
                                    final LatLngBounds latLngBounds = new LatLngBounds(myPolygon.getLatLngs()
                                            .get(3), myPolygon.getLatLngs()
                                            .get(1));
                                    final GroundOverlayOptions overlayOptions = new GroundOverlayOptions().image(BitmapDescriptorFactory
                                            .fromResource(R.drawable.polygon_colour))
                                            .transparency(myPolygon.getFillOpacity())
                                            .positionFromBounds(latLngBounds);
                                    final GroundOverlay groundOverlay = mMap.addGroundOverlay(overlayOptions);
                                    geohashCache.put(myPolygon, groundOverlay);
                                }

                                //                            polygonsDrawn.add(mMap.addPolygon(new PolygonOptions().addAll
                                //         (myPolygon
                                // .getLatLngs())
                                //                                    .fillColor(myPolygon.getColourFillFinal())
                                //                                    .strokeColor(Color.TRANSPARENT)
                                //                                    .strokeWidth(0)));
                            })
                            .doOnComplete(() -> Timber.i("Number of polygons actually drawn due to visibility is %d", drawnCount.get()));
                })
                .subscribe(Functions.emptyConsumer(), Timber::d);
    }

    private boolean isVisible(MyPolygon myPolygon) {
        double minLat = myPolygon.getLatLngs()
                .get(2).latitude;
        double maxLat = myPolygon.getLatLngs()
                .get(0).latitude;
        double minLng = myPolygon.getLatLngs()
                .get(0).longitude;
        double maxLng = myPolygon.getLatLngs()
                .get(2).latitude;
        return (isVisibleLat(minLat) && isVisibleLong(minLng)) || (isVisibleLat(minLat) && isVisibleLong(maxLng)) || (isVisibleLat
                (maxLat) && isVisibleLong(minLng)) || (isVisibleLat(maxLat) && isVisibleLong(maxLng));
    }

    private boolean isVisibleLat(double lat) {
        return bottomRight.latitude < lat && lat < topLeft.latitude;
    }

    private boolean isVisibleLong(double lng) {
        return bottomRight.longitude > lng && lng > topLeft.longitude;
    }

    @Override
    protected void onDestroy() {
        if (disposable != null) { disposable.dispose(); }
        if (disposable1 != null) { disposable1.dispose(); }
        if (disposable2 != null) { disposable2.dispose(); }
        super.onDestroy();
    }

    /**
     * Read polygon data from the geohashes in 5k_geohashes_jakarta.json file
     *
     * @return
     */
    Single<List<MyPolygon>> fetchPolygonsOnce() {
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
                        float opacity = (float) row.getDouble("shade");
                        return GeoHashUtils.decodeGeohash(geohash)
                                .map(doubles -> MyPolygon.create(GeoHashUtils.getPolygonPoints(doubles), 0xFFC32C01, opacity));
                    })
                    .toList();
        } catch (JSONException e) {
            return Single.error(e);
        }
    }

    Observable<List<MyPolygon>> fetchPolygonsRep() {
        if (shouldRepeat) {
            return Observable.interval(0, 3L, TimeUnit.MINUTES, Schedulers.io())
                    .flatMapSingle(aLong -> fetchPolygonsOnce());
        } else { return fetchPolygonsOnce().toObservable(); }
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


    private void togglePolygons() {
        if (!arePolygonsShowing) {
            showPolygons();
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
        mMap.setTrafficEnabled(false);
        for (Polygon polygon : polygonsDrawn) {
            polygon.remove();
        }
        polygonsDrawn.clear();
        for (GroundOverlay groundOverlay : groundOverlayList) {
            groundOverlay.remove();
        }
        groundOverlayList.clear();
        for (MyPolygon myPolygon : geohashCache.keySet()) {
            GroundOverlay groundOverlay = geohashCache.get(myPolygon);
            groundOverlay.remove();
        }
        geohashCache.clear();
    }

    private void showPolygons() {
        // Prevent multiple subscription when user taps button too fast
        if (disposable != null) { disposable.dispose(); }

        disposable = fetchPolygonsRep().subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
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
                    // list = list.subList(0, 2000);
                    if (batchDrawing) { return split(list); }
                    return Observable.just(MyPolygonBatch.create(0, list));
                })
                .concatMap((Function<MyPolygonBatch, ObservableSource<List<MyPolygon>>>) heatmapsPolygonBatch -> {
                    // Start the 1st batch right away, subsequent batches waits 5 seconds
                    long delay = heatmapsPolygonBatch.getBatchId() == 0 ? 0 : 5L;

                    return Observable.just(heatmapsPolygonBatch.getPolygonList())
                            .delay(delay, TimeUnit.SECONDS, Schedulers.io());
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(polygons -> {
                    Timber.i("drawing batch of %d polygons starting with %s", polygons.size(), polygons.get(0)
                            .getLatLngs());
                    drawPolygons(polygons);
                })
                .subscribe(Functions.emptyConsumer(), Timber::e);
        //        disposable2 = Single.timer(3, TimeUnit.MINUTES, Schedulers.io())
        //                .observeOn(AndroidSchedulers.mainThread())
        //                .doOnSubscribe(new Consumer<Disposable>() {
        //                    @Override
        //                    public void accept(Disposable disposable) throws Exception {
        //                        ctaRefresh.setVisibility(View.GONE);
        //                    }
        //                })
        //                .doOnSuccess(new Consumer<Long>() {
        //                    @Override
        //                    public void accept(Long aLong) throws Exception {
        //                        ctaRefresh.setVisibility(View.VISIBLE);
        //                    }
        //                })
        //                .flatMapObservable(new Function<Long, ObservableSource<Polygon>>() {
        //                    @Override
        //                    public ObservableSource<Polygon> apply(Long aLong) throws Exception {
        //                        return Observable.fromIterable(polygonsDrawn);
        //                    }
        //                })
        //                .observeOn(AndroidSchedulers.mainThread())
        //                .subscribe(new Consumer<Polygon>() {
        //                    @Override
        //                    public void accept(Polygon polygon) throws Exception {
        //                        int color = Color.argb(Color.alpha(polygon.getFillColor()), Color.red(Color.BLACK), Color.green(Color
        // .BLACK),
        //                                Color.blue(Color.BLACK));
        //                        polygon.setFillColor(color);
        //                    }
        //                }, Timber::e);
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
        polygonData2 = polygons;
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
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(jakarta, 11.0f));

        mMap.setOnCameraIdleListener(this);
        mMap.setOnCameraMoveListener(this);
        mMap.setOnCameraMoveStartedListener(this);
        mMap.setOnCameraMoveCanceledListener(this);
    }

    @Override
    public void onCameraIdle() {
        Timber.d("onCameraIdle");
        topLeft = mMap.getProjection()
                .getVisibleRegion().farLeft;
        bottomRight = mMap.getProjection()
                .getVisibleRegion().nearRight;
        cameraIdle.onNext(true);
    }

    @Override
    public void onCameraMove() {
        Timber.d("onCameraMove");
    }

    @Override
    public void onCameraMoveStarted(int i) {

    }

    @Override
    public void onCameraMoveCanceled() {

    }
}
