//
// BarcodeGraphicTracker.java
//
// Created by Michail Resvanis on 10/08/2017.
// Unauthorized copying of this file, via any medium is strictly prohibited.
// Proprietary and Confidential.
//
// Copyright (C) 2017 Futurae Technologies AG - All rights reserved.
// For any inquiry, contact: legal@futurae.com
//

package com.futurae.futuraedemo.ui.camera;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.barcode.Barcode;

/**
 * Generic tracker which is used for tracking or reading a barcode (and can really be used for
 * any type of item).  This is used to receive newly detected items, add a graphical representation
 * to an overlay, update the graphics as the item changes, and remove the graphics when the item
 * goes away.
 */
class BarcodeGraphicTracker extends Tracker<Barcode> {

    // attributes
    private GraphicOverlay<BarcodeGraphic> graphicOverlay;
    private BarcodeGraphic graphic;

    // This is the qr capture activity that initiated all this. We call its doCaptureBarcode
    // to automatically trigger the capture process, instead of waiting the user to
    // manually tap on the screen
    private QRCapturable qrCapture;

    BarcodeGraphicTracker(GraphicOverlay<BarcodeGraphic> graphicOverlay, BarcodeGraphic graphic,
            QRCapturable qrCapture) {

        this.graphicOverlay = graphicOverlay;
        this.graphic = graphic;
        this.qrCapture = qrCapture;
    }

    /**
     * Start tracking the detected item instance within the item overlay.
     */
    @Override
    public void onNewItem(int id, Barcode item) {

        graphic.setId(id);
    }

    /**
     * Update the position/characteristics of the item within the overlay.
     */
    @Override
    public void onUpdate(Detector.Detections<Barcode> detectionResults, Barcode item) {

        this.graphicOverlay.add(graphic);
        graphic.updateItem(item);

        qrCapture.doCaptureBarcode();
    }

    /**
     * Hide the graphic when the corresponding object was not detected.  This can happen for
     * intermediate frames temporarily, for example if the object was momentarily blocked from
     * view.
     */
    @Override
    public void onMissing(Detector.Detections<Barcode> detectionResults) {

        this.graphicOverlay.remove(graphic);
    }

    /**
     * Called when the item is assumed to be gone for good. Remove the graphic annotation from
     * the overlay.
     */
    @Override
    public void onDone() {

        this.graphicOverlay.remove(graphic);
    }
}
