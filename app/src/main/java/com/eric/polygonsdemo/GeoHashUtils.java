package com.eric.polygonsdemo;

import android.support.annotation.VisibleForTesting;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.Single;

public class GeoHashUtils {
    private static final char[] BASE_32 = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k',
            'm', 'n', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    private final static Map<Character, Integer> DECODE_MAP = new HashMap<>();

    private static final int PRECISION = 12;
    private static final int[] BITS = {16, 8, 4, 2, 1};

    static {
        for (int i = 0; i < BASE_32.length; i++) {
            DECODE_MAP.put(BASE_32[i], i);
        }
    }

    private GeoHashUtils() {
        // not-allowed
    }

    static Single<List<Double>> decodeGeohash(String geohash) {
        final double[] latInterval = {-90.0, 90.0};
        final double[] lngInterval = {-180.0, 180.0};

        final boolean[] isEvenBit = {true};

        return Observable.range(0, geohash.length())
                .concatMapSingle(index -> {
                    final int cd = DECODE_MAP.get(geohash.charAt(index));
                    return Observable.range(0, BITS.length)
                            .concatMapSingle(index1 -> {
                                int mask = BITS[index1];
                                if (isEvenBit[0]) {
                                    if ((cd & mask) != 0) {
                                        lngInterval[0] = (lngInterval[0] + lngInterval[1]) / 2D;
                                    } else {
                                        lngInterval[1] = (lngInterval[0] + lngInterval[1]) / 2D;
                                    }
                                } else {
                                    if ((cd & mask) != 0) {
                                        latInterval[0] = (latInterval[0] + latInterval[1]) / 2D;
                                    } else {
                                        latInterval[1] = (latInterval[0] + latInterval[1]) / 2D;
                                    }
                                }
                                isEvenBit[0] = !isEvenBit[0];
                                return Single.just(true);
                            })
                            .toList();
                })
                .toList()
                .map(lists -> Arrays.asList(latInterval[1], lngInterval[0], latInterval[0], lngInterval[1]));
    }

    /**
     * @param latlngs latitudes and longitudes in order: top-left point's lat, top-left point'
     *                long, bottom-right point's lat,
     *                bottom-right points' long
     * @return points (LatLng) in order: (top-)left, (top-)right, bottom(-right), (bottom-)left
     */
    static List<LatLng> getPolygonPoints(List<Double> latlngs) {
        List<LatLng> polygon = new ArrayList<>(4);
        polygon.add(new LatLng(latlngs.get(0), latlngs.get(1)));
        polygon.add(new LatLng(latlngs.get(0), latlngs.get(3)));
        polygon.add(new LatLng(latlngs.get(2), latlngs.get(3)));
        polygon.add(new LatLng(latlngs.get(2), latlngs.get(1)));
        return polygon;
    }

    public static void main(String[] args) {
        String geohash = "qqgvne";
    }
}
