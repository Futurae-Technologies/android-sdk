package com.futurae.futuraedemo

import com.futurae.sdk.FuturaeClient
import com.futurae.sdk.FuturaeSDK

object FuturaeSdkWrapper {

    val sdk: FuturaeSDK
        get() = FuturaeSDK.INSTANCE

    val client: FuturaeClient
        get() = sdk.client
}