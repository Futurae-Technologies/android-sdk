/*
 * ScaleListener.java
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

import android.view.ScaleGestureDetector;

public class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

    // attributes
    private CameraSource cameraSource;

    // constructors
    public ScaleListener(CameraSource cameraSource) {

        super();
        this.cameraSource = cameraSource;
    }

    // overrides

    /**
     * Responds to scaling events for a gesture in progress.
     * Reported by pointer motion.
     *
     * @param detector The detector reporting the event - use this to retrieve extended info about
     * event state.
     * @return Whether or not the detector should consider this event as handled. If an event was
     * not handled, the detector will continue to accumulate movement until an event is handled.
     * This can be useful if an application, for example, only wants to update scaling factors if
     * the change is greater than 0.01.
     */
    @Override
    public boolean onScale(ScaleGestureDetector detector) {

        return false;
    }

    /**
     * Responds to the beginning of a scaling gesture. Reported by
     * new pointers going down.
     *
     * @param detector The detector reporting the event - use this to retrieve extended info about
     * event state.
     * @return Whether or not the detector should continue recognizing this gesture. For example, if
     * a gesture is beginning with a focal point outside of a region where it makes sense,
     * onScaleBegin() may return false to ignore the rest of the gesture.
     */
    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {

        return true;
    }

    /**
     * Responds to the end of a scale gesture. Reported by existing
     * pointers going up.
     * <p/>
     * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()}
     * and {@link ScaleGestureDetector#getFocusY()} will return focal point
     * of the pointers remaining on the screen.
     *
     * @param detector The detector reporting the event - use this to retrieve extended info about
     * event state.
     */
    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

        cameraSource.doZoom(detector.getScaleFactor());
    }
}
