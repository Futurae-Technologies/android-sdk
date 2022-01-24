package com.futurae.futuraedemo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.futurae.futuraedemo.databinding.ActivityHomeBinding
import com.futurae.sdk.*
import com.futurae.sdk.approve.ApproveSession
import com.futurae.sdk.model.AccountsMigrationResource
import com.futurae.sdk.model.SessionInfo
import com.google.android.gms.vision.barcode.Barcode
import timber.log.Timber

class HomeActivity : AppCompatActivity() {

    private val qrResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            result.data?.let { onQRCodeScanned(it) }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            Timber.i("Permissions request result received")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.buttonLogout.setOnClickListener {
            //For demo purposes, we simply logout the first account
            val accounts = FuturaeClient.sharedClient().accounts
            if (accounts == null || accounts.size == 0) {
                showAlert("Error", "No account to logout")
                return@setOnClickListener
            }

            val account = accounts[0]
            FuturaeClient.sharedClient().logout(
                account.userId,
                object : FuturaeCallback {
                    override fun success() {
                        showAlert("Success", "Logged out " + account.username)
                    }

                    override fun failure(throwable: Throwable) {
                        showAlert("Error", "Could not logout")
                    }
                })
        }
        binding.buttonTotp.setOnClickListener {
            val accounts = FuturaeClient.sharedClient().accounts
            if (accounts == null || accounts.size == 0) {
                showAlert("Error", "No account enrolled")
                return@setOnClickListener
            }

            val totp = FuturaeClient.sharedClient().nextTotp(accounts[0].userId)

            showAlert(
                "TOTP", """Code: ${totp.getPasscode()}Remaining seconds: ${totp.getRemainingSecs()}""".trimIndent()
            )
        }

        val qrClickListener = View.OnClickListener { qrResult.launch(FTRQRCodeActivity.getIntent(this)) }
        binding.buttonEnroll.setOnClickListener(qrClickListener)
        binding.buttonQrCodeAuth.setOnClickListener(qrClickListener)
        binding.buttonQrCodeOffline.setOnClickListener(qrClickListener)
        binding.buttonScanQR.setOnClickListener(qrClickListener)

        binding.checkMigrationButton.setOnClickListener {
            FuturaeClient.sharedClient().checkAccountMigrationPossible(
                object : FuturaeResultCallback<Int> {

                    override fun failure(throwable: Throwable) {
                        Timber.e(throwable.localizedMessage)
                        showAlert("Error", "Checking for accounts migration failed")
                    }

                    override fun success(numAccounts: Int) {
                        if (numAccounts > 0) {
                            Timber.i("Accounts Migration is possible. Number of accounts that can be migrated: $numAccounts")
                            showAlert(
                                "Success",
                                "Accounts Migration is possible.\nNumber of accounts that can be migrated: $numAccounts"
                            )
                        } else {
                            Timber.e("Accounts Migration is not possible. There were no accounts found that can be migrated.")
                            showAlert(
                                "Info",
                                "Accounts Migration is not possible\nNo accounts found that can be migrated"
                            )
                        }
                    }
                })
        }
        binding.performMigrationButton.setOnClickListener {
            binding.migrationCard.isVisible = true
            FuturaeClient.sharedClient().executeAccountMigration(
                object : FuturaeResultCallback<Array<AccountsMigrationResource.MigrationAccount>> {
                    override fun success(result: Array<AccountsMigrationResource.MigrationAccount>) {
                        binding.migrationCard.isVisible = false
                        showAlert("Migration Successful", "Migrated ${result.size} accounts.")
                    }

                    override fun failure(throwable: Throwable) {
                        binding.migrationCard.isVisible = false
                        showAlert(
                            "Migration Failed",
                            "${throwable.message}"
                        )
                        Timber.e(throwable)
                    }

                }
            )
        }

        checkRuntimePermissions()
    }

    private fun checkRuntimePermissions() {
        val permissions = mutableListOf<String>()
        //Location && scans
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showPermissionDialog("Permission Required", "We need Location permission for scans")
            }
            else -> permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        //Camera
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                permissions.add(Manifest.permission.CAMERA)
            }
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionDialog("Permission Required", "We need Camera permission for reading QR codes")
            }
            else -> permissions.add(Manifest.permission.CAMERA)
        }

        //BlueTooth connect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED -> {
                    permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) -> {
                    showPermissionDialog(
                        "Permission Required",
                        "We need BT Connect permission for connected bt devices"
                    )
                }
                else -> permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED -> {
                    permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN) -> {
                    showPermissionDialog("Permission Required", "We need BT Scan permission for scans")
                }
                else -> permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        //Camera
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                permissions.add(Manifest.permission.CAMERA)
            }
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionDialog("Permission Required", "We need Camera permission for reading QR codes")
            }
            else -> permissions.add(Manifest.permission.CAMERA)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun showPermissionDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ok") { dialog, which ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    this.data = Uri.fromParts("package", packageName, null)
                    startActivity(this)
                }
            }
            .show()
    }

    private fun onAuthQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrcode ->
            try {
                val userId = FuturaeClient.getUserIdFromQrcode(qrcode.rawValue)
                val sessionToken = FuturaeClient.getSessionTokenFromQrcode(qrcode.rawValue)
                FuturaeClient.sharedClient().sessionInfoByToken(userId, sessionToken,
                    object : FuturaeResultCallback<SessionInfo?> {
                        override fun success(sessionInfo: SessionInfo?) {
                            showApproveAlertDialog(ApproveSession(sessionInfo), false)
                        }

                        override fun failure(t: Throwable) {
                            showAlert("Error", "Error: ${t.message}")
                        }
                    })
            } catch (e: MalformedQRCodeException) {
                Timber.e(e)
                return
            }
        } ?: showAlert("Error", "QR code not found from result")
    }

    private fun onOfflineAuthQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrCode ->
            showOfflineQrcodeDialog(qrCode.rawValue)
        } ?: showAlert("Error", "QR code not found from result")
    }

    private fun onQRCodeScanned(data: Intent) {
        (data.getParcelableExtra(FTRQRCodeActivity.PARAM_BARCODE) as? Barcode)?.let { qrCode ->
            when (FuturaeClient.getQrcodeType(qrCode.rawValue)) {
                FuturaeClient.QR_ENROLL -> onEnrollQRCodeScanned(data)
                FuturaeClient.QR_ONLINE -> onAuthQRCodeScanned(data)
                FuturaeClient.QR_OFFLINE -> onOfflineAuthQRCodeScanned(data)
            }
        } ?: showAlert("Error", "QR code not found from result")
    }

    private fun onEnrollQRCodeScanned(data: Intent) {
        data.getParcelableExtra<Barcode>(FTRQRCodeActivity.PARAM_BARCODE)?.let { qrCode ->
            FuturaeClient.sharedClient().enroll(
                qrCode.rawValue,
                object : FuturaeCallback {
                    override fun success() {
                        showAlert("Success", "Enrollment successful")
                    }

                    override fun failure(throwable: Throwable) {
                        showAlert("Error", throwable.message ?: "")
                    }
                })
        }
    }

    private fun showApproveAlertDialog(session: ApproveSession, isFromUri: Boolean) {
        session.info?.let {
            val sb = StringBuffer()
            sb.append("\n")
            for (info in session.info) {
                sb.append(info.key).append(": ").append(info.value).append("\n")
            }
            AlertDialog.Builder(this)
                .setTitle("Approve")
                .setMessage("Would you like to approve the request? $sb")
                .setPositiveButton("Approve") { dialog, which ->
                    FuturaeClient.sharedClient().approveAuth(
                        session.userId,
                        session.sessionId, object : FuturaeCallback {
                            override fun success() {
                                if (isFromUri) {
                                    dialog.dismiss()
                                }
                            }

                            override fun failure(t: Throwable) {
                                Timber.e("Failed to approve session: " + t.localizedMessage)
                            }
                        }, session.info
                    )
                }
                .setNegativeButton("Deny") { dialog, id ->
                    FuturaeClient.sharedClient().rejectAuth(
                        session.userId,
                        session.sessionId, false, object : FuturaeCallback {
                            override fun success() {
                                if (isFromUri) {
                                    dialog.dismiss()
                                }
                            }

                            override fun failure(t: Throwable) {
                                Timber.e("Failed to approve session: " + t.localizedMessage)
                            }
                        }, session.info
                    )
                }
                .show()
        } ?: showAlert("Error", "Session info not found")
    }

    private fun showOfflineQrcodeSignatureDialog(signature: String) {
        val sb = StringBuffer()
        sb.append("To Approve the transaction, enter: $signature in the browser")
        AlertDialog.Builder(this)
            .setTitle("Confirm Transaction")
            .setMessage(sb.toString())
            .setPositiveButton("Done") { dialog, id ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showOfflineQrcodeDialog(qrCode: String?) {
        val extras = try {
            FuturaeClient.getExtraInfoFromOfflineQrcode(qrCode)
        } catch (e: MalformedQRCodeException) {
            Timber.e(e)
            null
        }
        extras?.let {
            val sb = StringBuffer()
            sb.append("\n")

            for (info in extras) {
                sb.append(info!!.key).append(": ").append(info.value).append("\n")
            }

            AlertDialog.Builder(this)
                .setTitle("Approve")
                .setMessage("Request Information: $sb")
                .setPositiveButton("Approve") { dialog, which ->
                    try {
                        val verificationSignature =
                            FuturaeClient.sharedClient().computeVerificationCodeFromQrcode(qrCode)
                        showOfflineQrcodeSignatureDialog(verificationSignature)
                    } catch (e: MalformedQRCodeException) {
                        Timber.e(e)
                    } catch (e: UnknownAccountException) {
                        Timber.e(e)
                    }
                }
                .setNegativeButton("Deny") { dialog, id ->
                    dialog.dismiss()
                }
                .show()
        } ?: showAlert("Error", "QR code extras not found")

    }

    private fun showAlert(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }
}