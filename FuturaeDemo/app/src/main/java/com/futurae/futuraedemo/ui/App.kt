package com.futurae.futuraedemo.ui

import android.app.Application
import com.futurae.futuraedemo.ui.qr_push_action.QRCodeRequestedActionHandler
import com.futurae.futuraedemo.ui.qr_push_action.QRCodeRequestedActionListener
import com.futurae.sdk.FuturaeSDK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class App : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val qrCodeActionListener: QRCodeRequestedActionListener by lazy {
        object : QRCodeRequestedActionListener() {
            override fun onQRCodeScanRequested() {
                QRCodeRequestedActionHandler.handle(this@App)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        applicationScope.launch {
            FuturaeSDK.sdkState().collect{
                Timber.i("SDK state: $it")
            }
        }

        qrCodeActionListener.startListening(this)
    }
}