//
// FTRQRCodeActivity.java
//
// Created by Michail Resvanis on 10/08/2017.
// Unauthorized copying of this file, via any medium is strictly prohibited.
// Proprietary and Confidential.
//
// Copyright (C) 2017 Futurae Technologies AG - All rights reserved.
// For any inquiry, contact: legal@futurae.com
//

package com.futurae.futuraedemo.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.futurae.futuraedemo.BuildConfig;
import com.futurae.futuraedemo.R;
import com.futurae.futuraedemo.ui.camera.BarcodeGraphic;
import com.futurae.futuraedemo.ui.camera.BarcodeTrackerFactory;
import com.futurae.futuraedemo.ui.camera.CameraSource;
import com.futurae.futuraedemo.ui.camera.CameraSourcePreview;
import com.futurae.futuraedemo.ui.camera.CaptureGestureListener;
import com.futurae.futuraedemo.ui.camera.GraphicOverlay;
import com.futurae.futuraedemo.ui.camera.QRCapturable;
import com.futurae.futuraedemo.ui.camera.ScaleListener;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import java.io.IOException;

public class FTRQRCodeActivity extends AppCompatActivity implements QRCapturable {

    // constants
    private static final String TAG = FTRQRCodeActivity.class.getSimpleName();

    public static final int RESULT_BARCODE = 10000;
    public static final int RESULT_BARCODE_AUTH = 20000;
    public static final int RESULT_BARCODE_OFFLINE = 30000;
    public static final int RESULT_BARCODE_GENERIC = 40000;

    public static final String PARAM_BARCODE = "ftr_barcode";
    public static final String PARAM_AUTOFOCUS = "ftr_autofocus";
    public static final String PARAM_USE_FLASH = "ftr_use_flash";

    // Barcode scanner settings
    private static final int SCANNER_BARCODE_FORMAT = Barcode.QR_CODE;

    // attributes
    private CameraSource cameraSource;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    @BindView(R.id.qrcode_preview)
    protected CameraSourcePreview preview;

    @BindView(R.id.qrcode_overlay)
    protected GraphicOverlay<BarcodeGraphic> graphicOverlay;

    // static public
    public static Intent getIntent(Context context) {

        return getIntent(context, false, false);
    }

    public static Intent getIntent(Context context, boolean autofocus, boolean useFlash) {

        Intent intent = new Intent(context, FTRQRCodeActivity.class);
        intent.putExtra(PARAM_AUTOFOCUS, autofocus);
        intent.putExtra(PARAM_USE_FLASH, useFlash);

        return intent;
    }

    // overrides
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcode);
        ButterKnife.bind(this);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.qrcode_title);
        actionBar.setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();

        // read parameters from the intent used to launch the activity.
        boolean autoFocus = intent.getBooleanExtra(PARAM_AUTOFOCUS, false);
        boolean useFlash = intent.getBooleanExtra(PARAM_USE_FLASH, false);

        createCameraSource(autoFocus, useFlash);

        gestureDetector = new GestureDetector(this, new CaptureGestureListener(this));
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener(cameraSource));
    }

    @Override
    protected void onResume() {

        super.onResume();
        startCameraSource();
    }

    @Override
    protected void onPause() {

        super.onPause();
        if (preview != null) {
            preview.stop();
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
        if (preview != null) {
            preview.release();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {

        boolean b = scaleGestureDetector.onTouchEvent(e);
        boolean c = gestureDetector.onTouchEvent(e);
        return b || c || super.onTouchEvent(e);
    }

    // handlers - QRCapturable

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     * <p/>
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @Override
    @SuppressLint("InlinedApi")
    public void createCameraSource(boolean autoFocus, boolean useFlash) {

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        // NOTE: We pass the activity itself as an extra argument so that the tracker object
        // can call the doCaptureBarcode to automatically trigger the capture process, instead
        // of waiting the user to manually tap on the screen
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(SCANNER_BARCODE_FORMAT).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(graphicOverlay, this);
        barcodeDetector.setProcessor(new MultiProcessor.Builder<>(barcodeFactory).build());

        if (!barcodeDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.error_low_storage, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.error_low_storage));
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        CameraSource.Builder builder = new CameraSource.Builder(this,
                barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f);

        // make sure that auto focus is an available option
        this.cameraSource = builder
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null)
                .build();
    }

    /**
     * doCaptureBarcode is called by the respective BarcodeGraphicTracker
     * to capture the first barcode detected and
     * return it to the caller.
     *
     * @return true if the activity is ending.
     */
    @Override
    public boolean doCaptureBarcode() {

        BarcodeGraphic graphic = graphicOverlay.getFirstGraphic();
        if (graphic == null) {
            Log.d(TAG, "no barcode detected");
            return false;
        }

        Barcode barcode = graphic.getBarcode();
        if (barcode == null) {
            Log.d(TAG, "barcode data is null");
            return false;
        }

        finishActivity(barcode);
        return true;
    }

    /**
     * onTap is called to capture the oldest barcode currently detected and
     * return it to the caller.
     *
     * @param rawX - the raw position of the tap
     * @param rawY - the raw position of the tap.
     * @return true if the activity is ending.
     */
    @Override
    public boolean onTap(float rawX, float rawY) {

        BarcodeGraphic graphic = graphicOverlay.getFirstGraphic();
        if (graphic == null) {
            Log.d(TAG, "no barcode detected");
            return false;
        }

        Barcode barcode = graphic.getBarcode();
        if (barcode == null) {
            Log.d(TAG, "barcode data is null");
            return false;
        }

        finishActivity(barcode);
        return true;
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    @Override
    public void startCameraSource() throws SecurityException {

        if (cameraSource == null) {
            return;
        }

        try {
            preview.start(cameraSource, graphicOverlay);

        } catch (IOException e) {
            Log.e(TAG, "Unable to start camera source.", e);
            cameraSource.release();
            cameraSource = null;
        }
    }

    // private
    private void finishActivity(Barcode barcode) {

        Intent data = new Intent();
        data.putExtra(PARAM_BARCODE, barcode);
        setResult(Activity.RESULT_OK, data);
        finish();
    }
}
