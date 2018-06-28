/*
 * CaptureGestureListener.java
 *
 * Created by mike on 21/8/2017.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * Proprietary and Confidential.
 *
 * Copyright (C) 2017 Futurae Technologies AG - All rights reserved.
 * For any inquiry, contact: legal@futurae.com
 *
 */

package com.futurae.futuraedemo.ui.camera;

import android.view.GestureDetector;
import android.view.MotionEvent;

public class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {

    // attributes
    private QRCapturable qrCapturable;

    // constructors
    public CaptureGestureListener(QRCapturable qrCapturable) {

        super();
        this.qrCapturable = qrCapturable;
    }

    // overrides
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {

        return qrCapturable.onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
    }
}
