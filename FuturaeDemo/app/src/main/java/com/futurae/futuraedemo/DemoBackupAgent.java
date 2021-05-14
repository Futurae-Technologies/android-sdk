package com.futurae.futuraedemo;

import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.futurae.sdk.BackupAgent;

import java.io.IOException;

public class DemoBackupAgent extends android.app.backup.BackupAgent {

    private static final String TAG = DemoBackupAgent.class.getSimpleName();

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {

        try {
            BackupAgent.onBackupAccounts(this, oldState, data, newState);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) {
        try {
            BackupAgent.onRestoreAccounts(this, data, appVersionCode, newState);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
}