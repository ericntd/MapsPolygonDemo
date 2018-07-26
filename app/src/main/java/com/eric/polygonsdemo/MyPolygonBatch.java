package com.eric.polygonsdemo;

import java.util.List;

public class MyPolygonBatch {
    private int batchId;
    private List<MyPolygon> polygonList;

    public int getBatchId() {
        return batchId;
    }

    public List<MyPolygon> getPolygonList() {
        return polygonList;
    }

    private MyPolygonBatch(int batchId, List<MyPolygon> polygonList) {
        this.batchId = batchId;
        this.polygonList = polygonList;
    }

    public static MyPolygonBatch create(int batchId, List<MyPolygon> polygonList) {
        return new MyPolygonBatch(batchId, polygonList);
    }
}
