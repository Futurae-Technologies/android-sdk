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

import com.futurae.futuraedemo.R;
import com.futurae.sdk.FuturaeCallback;
import com.futurae.sdk.FuturaeClient;
import com.futurae.sdk.FuturaeResultCallback;
import com.futurae.sdk.approve.ApproveSession;
import com.futurae.sdk.model.Account;
import com.futurae.sdk.model.ApproveInfo;
import com.futurae.sdk.model.CurrentTotp;
import com.futurae.sdk.model.SessionInfo;
import com.futurae.sdk.utils.NotificationUtils;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.OnClick;


public class MainActivity extends AppCompatActivity {

    // constants
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private AlertDialog approveDialog;

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

        checkAndAskForPermission(Manifest.permission.CAMERA, getString(R.string.camera_perm_explain), 2);

        initLocalBroadcastReceiver();

        // TODO: Handle URI call
        final String uriCall = getIntent().getDataString();
        if (!TextUtils.isEmpty(uriCall)) {

            // Enrollment URI
            if (uriCall.contains("enroll")) {
                FuturaeClient.sharedClient().handleUri(uriCall, new FuturaeCallback() {
                    @Override
                    public void success() {
                        Log.i(TAG, "success: URI handled");
                        showAlert("Success", "Successfully enrolled");
                    }

                    @Override
                    public void failure(Throwable throwable) {
                        Log.e(TAG, "failure: failed to handle URI: " + throwable);
                        showAlert("Error", "Could not handle URI call");
                    }
                });
                return;
            }

            // Auth URI
            if (uriCall.contains("auth")) {
                String userId = FuturaeClient.getUserIdFromUri(uriCall);
                String sessionToken = FuturaeClient.getSessionTokenFromUri(uriCall);
                FuturaeClient.sharedClient().sessionInfoByToken(userId, sessionToken,
                        new FuturaeResultCallback<SessionInfo>() {
                            @Override
                            public void success(SessionInfo sessionInfo) {
                                showApproveAlertDialog(new ApproveSession(sessionInfo), true);
                            }

                            @Override
                            public void failure(Throwable t) {
                                Log.e(TAG, "QR Code authentication failed: " + t.getLocalizedMessage());
                            }
                        });
                return;
            }

            Log.e(TAG, "failure: failed to handle URI: " + uriCall);
            showAlert("Error", "Could not handle URI call");
        }
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

        showAlert("TOTP", "Code: " + totp.getPasscode() + "\nRemaining seconds: " + totp.getRemainingSecs());
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
        Barcode qrcode = data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE);
        String userId = FuturaeClient.getUserIdFromQrcode(qrcode.rawValue);
        String sessionToken = FuturaeClient.getSessionTokenFromQrcode(qrcode.rawValue);

        FuturaeClient.sharedClient().sessionInfoByToken(userId, sessionToken,
                new FuturaeResultCallback<SessionInfo>() {
                    @Override
                    public void success(SessionInfo sessionInfo) {
                        showApproveAlertDialog(new ApproveSession(sessionInfo), false);
                    }

                    @Override
                    public void failure(Throwable t) {
                        Log.e(TAG, "QR Code authentication failed: " + t.getLocalizedMessage());
                    }
                });
    }

    // Approve dialog
    void showApproveAlertDialog(final ApproveSession session, final boolean isFromUri) {
        // TODO: For demo purposes we simply show an alert instead of an approve screen

        StringBuffer sb = new StringBuffer();
        if (session.getInfo() != null) {
            sb.append("\n");
            for (ApproveInfo info : session.getInfo()) {
                sb.append(info.getKey()).append(": ").append(info.getValue()).append("\n");
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Approve");
        builder.setMessage("Would you like to approve the request?" + sb.toString());
        builder.setPositiveButton("Approve", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                FuturaeClient.sharedClient().approveAuth(session.getUserId(),
                        session.getSessionId(), new FuturaeCallback() {
                            @Override
                            public void success() {
                                Log.i(TAG, "Approve session allowed");
                                if (isFromUri) {
                                    finish();
                                }
                            }

                            @Override
                            public void failure(Throwable t) {
                                Log.e(TAG, "Failed to approve session: " + t.getLocalizedMessage());
                            }
                        }, session.getInfo());
            }
        });
        builder.setNegativeButton("Deny", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                FuturaeClient.sharedClient().rejectAuth(session.getUserId(),
                        session.getSessionId(), false, new FuturaeCallback() {
                            @Override
                            public void success() {
                                Log.i(TAG, "Approve session rejected");
                                if (isFromUri) {
                                    finish();
                                }
                            }

                            @Override
                            public void failure(Throwable t) {
                                Log.e(TAG, "Failed to approve session: " + t.getLocalizedMessage());
                            }
                        }, session.getInfo());
            }
        });

        approveDialog = builder.create();
        approveDialog.show();
    }

    // private
    private void checkAndAskForPermission(final String permission, final String message, final int requestID) {
        final Activity activity = this;

        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {

            // ALWAYS show explanation if SDK >= 23
            if (Build.VERSION.SDK_INT >= 23) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(message)
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
        intentFilter.addAction(NotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE);
        intentFilter.addAction(NotificationUtils.INTENT_APPROVE_AUTH_MESSAGE);
        intentFilter.addAction(NotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR);
        intentFilter.addAction(NotificationUtils.INTENT_APPROVE_CANCEL_MESSAGE);

        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String error = intent.getStringExtra(NotificationUtils.PARAM_ERROR);
                if (!TextUtils.isEmpty(error)) {
                    Log.e(TAG, "Received Intent '" + intent.getAction() + "' with error: " + error);
                    return;
                }

                switch(intent.getAction()) {

                    // TODO: Handle unenroll notification (e.g. refresh lists)
                    case NotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE:
                        Log.d(TAG, NotificationUtils.INTENT_ACCOUNT_UNENROLL_MESSAGE);

                        String userId = intent.getStringExtra(NotificationUtils.PARAM_USER_ID);
                        Log.i(TAG, "Received logout and deleted account: " + userId);
                        break;

                    case NotificationUtils.INTENT_APPROVE_AUTH_MESSAGE:
                        Log.d(TAG, NotificationUtils.INTENT_APPROVE_AUTH_MESSAGE);

                        final ApproveSession session = intent.getParcelableExtra(
                                NotificationUtils.PARAM_APPROVE_SESSION);

                        if (approveDialog != null && approveDialog.isShowing()) {
                            approveDialog.dismiss();
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showApproveAlertDialog(session, false);
                            }
                        });
                        break;

                    case NotificationUtils.INTENT_APPROVE_CANCEL_MESSAGE:
                        if (approveDialog != null && approveDialog.isShowing()) {
                            approveDialog.dismiss();
                        }
                        break;

                    case NotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR:
                        Log.d(TAG, NotificationUtils.INTENT_GENERIC_NOTIFICATION_ERROR);
                        break;
                }
            }
        }, intentFilter);
    }

    private void showAlert(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
