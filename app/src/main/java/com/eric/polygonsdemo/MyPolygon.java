package com.eric.polygonsdemo;

import android.graphics.Color;
import android.support.annotation.ColorRes;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public class MyPolygon {
    private final List<LatLng> latLngs;
    private final int fillColour;
    private final double fillOpacity;

    public List<LatLng> getLatLngs() {
        return latLngs;
    }

    @ColorRes
    public int getFillColour() {
        return fillColour;
    }

    public double getFillOpacity() {
        return fillOpacity;
    }

    private MyPolygon(List<LatLng> latLngList, int fillColour, double fillOpacity) {
        this.latLngs = latLngList;
        this.fillColour = fillColour;
        this.fillOpacity = fillOpacity;
    }

    public static MyPolygon create(List<LatLng> latLngList, int fillColor, double fillOpacity) {
        return new MyPolygon(latLngList, fillColor, fillOpacity);
    }

    /**
     * Final colour
     *
     * @return
     */
    public int getColourFillFinal() {
        return Color.argb((int) Math.round(Color.alpha(getFillColour()) * getFillOpacity()), Color.red(getFillColour()), Color.green
                (getFillColour()), Color.blue(getFillColour()));
    }
}
