package com.futurae.futuraedemo.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.futurae.futuraedemo.R;
import com.futurae.sdk.FuturaeCallback;
import com.futurae.sdk.FuturaeClient;
import com.futurae.sdk.Shared;
import com.futurae.sdk.approve.ApproveSession;
import com.futurae.sdk.gcm.FTRApproveNotification;
import com.futurae.sdk.gcm.FTRNotification;
import com.futurae.sdk.gcm.FTRRegistrationIntentService;
import com.futurae.sdk.gcm.FTRUnenrollNotification;
import com.futurae.sdk.model.Account;
import com.futurae.sdk.model.CurrentTotp;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.barcode.Barcode;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    // constants
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    // overrides
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case FTRQRCodeActivity.RESULT_BARCODE:
                    onEnrollQRCodeScanned(data);
                    break;
                case FTRQRCodeActivity.RESULT_BARCODE_AUTH:
                    onAuthQRCodeScanned(data);
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        managePermissions();

        initRegistrationService();
        initLocalBroadcastReceiver();

        // TODO: Handle URI call
        final String uriCall = getIntent().getDataString();
        if (!TextUtils.isEmpty(uriCall)) {
            FuturaeClient.sharedClient().handleUri(uriCall, new FuturaeCallback() {
                @Override
                public void success() {
                    Log.i(TAG, "success: URI handled");

                    // TODO: Handle enrollment and MobileAuth
                    if (uriCall.contains("enroll")) {
                        // URI Enrollment
                        showAlert("Success", "Successfully enrolled");
                    } else {
                        // MobileAuth
                        finish();
                    }
                }

                @Override
                public void failure(Throwable throwable) {
                    Log.e(TAG, "failure: failed to handle URI: " + throwable);
                    showAlert("Error", "Could not handle URI call");
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initRegistrationService();
    }

    // handlers
    @OnClick(R.id.main_btn_enroll)
    protected void onEnroll() {
        startActivityForResult(FTRQRCodeActivity.getIntent(this, true, false), FTRQRCodeActivity.RESULT_BARCODE);
    }


    @OnClick(R.id.main_btn_logout)
    protected void onLogout() {
        // TODO: For demo purposes, we simply logout the first account we can find

        List<Account> accounts = FuturaeClient.sharedClient().getAccounts();
        if (accounts == null || accounts.size() == 0) {
            showAlert("Error", "No account to logout");
            return;
        }

        final Account account = accounts.get(0);
        FuturaeClient.sharedClient().logout(account.getUserId(),
                new FuturaeCallback() {
                    @Override
                    public void success() {
                        Log.i(TAG, "Logout successful");
                        showAlert("Success", "Logged out " + account.getUsername());
                    }

                    @Override
                    public void failure(Throwable throwable) {
                        Log.e(TAG, "Logout failed: " + throwable.getLocalizedMessage());
                        showAlert("Error", "Could not logout");
                    }
                });
    }

    @OnClick(R.id.main_btn_qr_code_auth)
    protected void onQRCodeAuth() {
        Log.i(TAG, "QR Code factor authentication started");
        startActivityForResult(FTRQRCodeActivity.getIntent(this, true, false),
                FTRQRCodeActivity.RESULT_BARCODE_AUTH);
    }

    @OnClick(R.id.main_btn_totp)
    protected void onTOTPAuth() {
        List<Account> accounts = FuturaeClient.sharedClient().getAccounts();
        if (accounts == null || accounts.size() == 0) {
            showAlert("Error", "No account enrolled");
            return;
        }

        final Account account = accounts.get(0);
        CurrentTotp totp = FuturaeClient.sharedClient().nextTotp(account.getUserId());

        showAlert("TOTP", "Code: " + totp.passcode + "\nRemaining seconds: " + totp.remainingSecs);
    }

    // QRCode callbacks
    private void onEnrollQRCodeScanned(Intent data) {
        // TODO: Handle enrollment response

        Barcode qrcode = data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE);
        Log.i(TAG, "Scanned activation code from the QR code; will enroll device");
        FuturaeClient.sharedClient().enroll(qrcode.rawValue,
            new FuturaeCallback() {
                @Override
                public void success() {
                    Log.i(TAG, "Enrollment successful");
                    showAlert("Success", "Enrollment successful");
                }

                @Override
                public void failure(Throwable throwable) {
                    Log.e(TAG, "Enrollment failed: " + throwable.getLocalizedMessage());
                    showAlert("Error", "Enrollment failed");
                }
            });
    }

    private void onAuthQRCodeScanned(Intent data) {
        // TODO: Handle QRCode auth response

        Barcode qrcode = data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE);
        Log.i(TAG, "Scanned authentication data from the QR code; will reply to server");
        FuturaeClient.sharedClient().approveAuth(qrcode.rawValue,
            new FuturaeCallback() {
                @Override
                public void success() {
                    Log.i(TAG, "QR Code authentication succeeded");
                }

                @Override
                public void failure(Throwable throwable) {
                    Log.e(TAG, "QR Code authentication failed: "
                        + throwable.getLocalizedMessage());
                }
            });
    }

    // Approve dialog
    void showApproveAlertDialog(final ApproveSession session) {
        // TODO: For demo purposes we simply show an alert instead of an approve screen

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Approve");
        builder.setMessage("Would you like to approve the request?");
        builder.setNeutralButton("Approve", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                FuturaeClient.sharedClient().approveAuth(session.getUserId(),
                        session.getSessionId(), new FuturaeCallback() {
                            @Override
                            public void success() {
                                Log.i(TAG, "Approve session allowed");
                            }

                            @Override
                            public void failure(Throwable t) {
                                Log.e(TAG, "Failed to approve session: " + t.getLocalizedMessage());
                            }
                        });
            }
        });
        builder.setNegativeButton("Deny", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                FuturaeClient.sharedClient().rejectAuth(session.getUserId(),
                        session.getSessionId(), false, new FuturaeCallback() {
                            @Override
                            public void success() {
                                Log.i(TAG, "Approve session rejected");
                            }

                            @Override
                            public void failure(Throwable t) {
                                Log.e(TAG, "Failed to approve session: " + t.getLocalizedMessage());
                            }
                        });
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // private
    private void managePermissions() {
        final String permission = Manifest.permission.CAMERA;
        final int requestID = 1;
        final Activity activity = this;

        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {

            // ALWAYS show explanation if SDK >= 23
            if (Build.VERSION.SDK_INT >= 23) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getString(R.string.camera_perm_explain))
                        .setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // Request the permission
                                ActivityCompat
                                        .requestPermissions(activity, new String[]{permission},
                                                requestID);
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            } else {

                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{permission}, requestID);
            }
        }

    }

    private void initLocalBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Shared.INTENT_ACCOUNT_UNENROLL_MESSAGE);
        intentFilter.addAction(Shared.INTENT_APPROVE_AUTH_MESSAGE);
        intentFilter.addAction(Shared.INTENT_GENERIC_NOTIFICATION_ERROR);

        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String error = intent.getStringExtra(FTRNotification.PARAM_ERROR);
                if (!TextUtils.isEmpty(error)) {
                    Log.e(TAG, "Received Intent '" + intent.getAction() + "' with error: " + error);
                    return;
                }

                switch(intent.getAction()) {

                    // TODO: Handle unenroll notification (e.g. refresh lists)
                    case Shared.INTENT_ACCOUNT_UNENROLL_MESSAGE:
                        Log.d(TAG, Shared.INTENT_ACCOUNT_UNENROLL_MESSAGE);

                        String userId = intent.getStringExtra(FTRUnenrollNotification.PARAM_USER_ID);
                        Log.i(TAG, "Received logout and deleted account: " + userId);
                        break;

                    // TODO: Handle approve notification (e.g. show approve view)
                    case Shared.INTENT_APPROVE_AUTH_MESSAGE:
                        Log.d(TAG, Shared.INTENT_APPROVE_AUTH_MESSAGE);

                        final ApproveSession session = intent.getParcelableExtra(
                                FTRApproveNotification.PARAM_APPROVE_SESSION);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showApproveAlertDialog(session);
                            }
                        });
                        break;

                    case Shared.INTENT_GENERIC_NOTIFICATION_ERROR:
                        Log.d(TAG, Shared.INTENT_GENERIC_NOTIFICATION_ERROR);
                        break;
                }
            }
        }, intentFilter);
    }

    private void initRegistrationService() {
        if (!checkPlayServices()) {
            Log.i(TAG, "No valid Google Play Services APK found.");
            return;
        }

        startService(new Intent(this, FTRRegistrationIntentService.class));
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();

        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode == ConnectionResult.SUCCESS) {
            return true;
        }

        if (apiAvailability.isUserResolvableError(resultCode)) {
            apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();

        } else {
            Log.i(TAG, "This device is not supported.");
            finish();
        }

        return false;
    }

    private void showAlert(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
