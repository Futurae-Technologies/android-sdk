/*
 * QRCaptureAbstract.java
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

public interface QRCapturable {

    void createCameraSource(boolean autoFocus, boolean useFlash);

    boolean doCaptureBarcode();

    boolean onTap(float rawX, float rawY);

    void startCameraSource() throws SecurityException;
}
