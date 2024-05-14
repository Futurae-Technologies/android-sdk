package com.futurae.futuraedemo

import com.futurae.sdk.Callback
import com.futurae.sdk.FuturaeClient
import com.futurae.sdk.FuturaeResultCallback
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.model.ApproveInfo
import com.futurae.sdk.model.SessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Since the SDK is compiled to a java artefact, we lose all the kotlin features that we need when integrating the SDK directly.
 * To have a workaround for that, reference the sdk using this class, changing the correct reference for it and the client. In this way,
 * we won't have to update all usages across the app, only in this file
 */
object FuturaeSdkWrapper {

    val sdk: FuturaeSDK
        get() = FuturaeSDK.INSTANCE

    val client: FuturaeClient
        get() = sdk.getClient()

    val coroutines by lazy { FuturaeCoroutinesWrapper(client) }
}

class FuturaeCoroutinesWrapper(
    private val client: FuturaeClient
) {
    suspend fun sessionInfoById(
        userId: String?,
        sessionId: String
    ): Result<SessionInfo> = withContext(Dispatchers.IO) {
        return@withContext suspendCoroutine { continuation ->
            client.sessionInfoById(userId, sessionId,
                object :  FuturaeResultCallback<SessionInfo> {

                    override fun success(result: SessionInfo) {
                        continuation.resume(Result.success(result))
                    }

                    override fun failure(throwable: Throwable) {
                        continuation.resume(Result.failure(throwable))
                    }
                }
            )
        }
    }

    suspend fun approveAuth(
        userId: String,
        sessionId: String,
        approveInfo: Array<ApproveInfo>?,
        multiNumberedChallengeResult: Int? = null
    ): Unit = withContext(Dispatchers.IO) {
        return@withContext suspendCoroutine<Unit> { continuation ->

            val callback = object : Callback<Unit> {
                override fun onSuccess(result: Unit) {
                    continuation.resume(result)
                }

                override fun onError(throwable: Throwable) {
                    continuation.resumeWithException(throwable)
                }
            }

            when (multiNumberedChallengeResult != null) {
                true -> client
                    .approveAuth(
                        userId,
                        sessionId,
                        approveInfo,
                        multiNumberedChallengeResult,
                        callback
                    )

                false -> FuturaeSdkWrapper.client
                    .approveAuth(userId, sessionId, approveInfo, callback)
            }
        }
    }
}