//
// BarcodeTrackerFactory.java
//
// Created by Michail Resvanis on 10/08/2017.
// Unauthorized copying of this file, via any medium is strictly prohibited.
// Proprietary and Confidential.
//
// Copyright (C) 2017 Futurae Technologies AG - All rights reserved.
// For any inquiry, contact: legal@futurae.com
//

package com.futurae.futuraedemo.ui.camera;

import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.barcode.Barcode;

/**
 * Factory for creating a tracker and associated graphic to be associated with a new barcode.  The
 * multi-processor uses this factory to create barcode trackers as needed -- one for each barcode.
 */
public class BarcodeTrackerFactory implements MultiProcessor.Factory<Barcode> {

    // attributes
    private GraphicOverlay<BarcodeGraphic> graphicOverlay;

    // This is the qr capture activity that initiated all this. We call its doCaptureBarcode
    // to automatically trigger the capture process, instead of waiting the user to
    // manually tap on the screen
    private QRCapturable qrCapture;

    // constructors
    public BarcodeTrackerFactory(GraphicOverlay<BarcodeGraphic> graphicOverlay,
            QRCapturable qrCapture) {

        this.graphicOverlay = graphicOverlay;
        this.qrCapture = qrCapture;
    }

    // overrides
    @Override
    public Tracker<Barcode> create(Barcode barcode) {

        BarcodeGraphic graphic = new BarcodeGraphic(graphicOverlay);
        return new BarcodeGraphicTracker(graphicOverlay, graphic, qrCapture);
    }

}

