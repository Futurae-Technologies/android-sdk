package com.futurae.futuraedemo.ui.activity.arch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.futurae.sdk.FuturaeSDK
import com.futurae.sdk.public_api.account.model.AccountQuery
import com.futurae.sdk.public_api.exception.FTAccountNotFoundException
import com.futurae.sdk.public_api.exception.FTEncryptedStorageCorruptedException
import com.futurae.sdk.public_api.exception.FTInvalidArgumentException
import com.futurae.sdk.public_api.exception.FTKeystoreOperationException
import com.futurae.sdk.public_api.lock.model.UnlockMethodType
import com.futurae.sdk.public_api.lock.model.UserPresenceVerificationMode
import com.futurae.sdk.public_api.session.model.ApproveInfo
import com.futurae.sdk.public_api.session.model.ApproveSession
import com.futurae.sdk.public_api.session.model.ById
import com.futurae.sdk.public_api.session.model.SessionInfoQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

typealias DoOnUnlockMethodPicked = (UnlockMethodType) -> Unit
typealias DoOnSdkUnlockUnlocked = () -> Unit

class FuturaeViewModel : ViewModel() {

    private val _promptUserToPickUnlockMethod = MutableSharedFlow<DoOnUnlockMethodPicked>()
    val promptUserToPickUnlockMethod = _promptUserToPickUnlockMethod.asSharedFlow()

    private val _promptUserForApproval = MutableSharedFlow<Pair<ApproveSession, String?>>()
    val promptUserForApproval = _promptUserForApproval.asSharedFlow()

    private val _notifyUserAboutError = MutableSharedFlow<Pair<String, Throwable>>()
    val notifyUserAboutError = _notifyUserAboutError.asSharedFlow()

    private val _notifyUser = MutableSharedFlow<Pair<String, String>>()
    val notifyUser = _notifyUser.asSharedFlow()

    private val _onUnlockMethodSelected = MutableSharedFlow<Pair<UnlockMethodType, DoOnSdkUnlockUnlocked>>()
    val onUnlockMethodSelected = _onUnlockMethodSelected.asSharedFlow()

    fun handleBroadcastReceivedMessage(message: BroadcastReceivedMessage) {
        when (message) {
            is BroadcastReceivedMessage.AccountUnenroll -> onAccountUnenroll(message)
            is BroadcastReceivedMessage.ApproveAuth -> handleApproveAuth(message)
            is BroadcastReceivedMessage.Error,
            BroadcastReceivedMessage.Unknown ->  {
                // do nothing
            }
        }

    }

    fun unlockSdk(
        userPresenceVerificationMode: UserPresenceVerificationMode,
        onSDKUnlocked: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                FuturaeSDK.client.lockApi.unlock(
                    userPresenceVerificationMode = userPresenceVerificationMode,
                    shouldWaitForSDKSync = true
                ).await()

                onSDKUnlocked()
            } catch (t: Throwable) {
                dispatchSdkUnlockError(error = t)
            }
        }
    }

    private fun onAccountUnenroll(message: BroadcastReceivedMessage.AccountUnenroll) {
        FuturaeSDK.client.accountApi
            .getAccount(accountQuery = AccountQuery.WhereUserIdAndDevice(userId = message.userId, deviceId = message.deviceId))
            ?.let { FuturaeSDK.client.accountApi.logoutAccountOffline(userId = message.userId) }
    }

    private fun handleApproveAuth(message: BroadcastReceivedMessage.ApproveAuth) {
        when {
            message.encryptedExtras != null -> {
                unlockSdk {
                    decryptExtras(userId = message.userId, encryptedExtras = message.encryptedExtras) {
                        notifyUserForApproval(
                            approveSession = message.approveSession,
                            additionalMessage = it?.let { "PN decrypted extras: ${it.joinToString()}" }
                        )
                    }
                }
            }

            message.approveSession.hasExtraInfo() -> {
                unlockSdk {
                    viewModelScope.launch {
                        fetchSessionInfo(approveSession = message.approveSession)
                            .await()
                            .onSuccess {
                                notifyUserForApproval(
                                    approveSession = message.approveSession,
                                    additionalMessage = it?.let { "Session extras: ${it.joinToString()}" }
                                )
                            }
                    }
                }
            }

            else -> {
                unlockSdk {
                    notifyUserForApproval(approveSession = message.approveSession)
                }
            }
        }
    }

    private fun decryptExtras(
        userId: String?,
        encryptedExtras: String,
        onSuccessfulDecryption: (List<ApproveInfo>?) -> Unit
    ) {
        val decryptedExtras = if (userId == null) {
            Timber.e("User id is required for encrypted extras")
            null
        } else {
            try {
                FuturaeSDK.client.operationsApi.decryptPushNotificationExtraInfo(
                    userId = userId,
                    encryptedExtrasString = encryptedExtras
                )
            } catch (e: FTAccountNotFoundException) {
                Timber.e("Account not found: ${e.message}")
                null
            } catch (e: FTEncryptedStorageCorruptedException) {
                Timber.e("Encrypted storage corrupted. Please use account recovery operation: ${e.message}")
                null
            } catch (e: FTKeystoreOperationException) {
                Timber.e("Keystore operation failed: ${e.message}")
                null
            } catch (e: FTInvalidArgumentException) {
                Timber.e("Unable to parse decrypted extra info: ${e.message}")
                null
            }
        }

        onSuccessfulDecryption(decryptedExtras)
    }

    private fun notifyUserForApproval(approveSession: ApproveSession, additionalMessage: String? = null) {
        viewModelScope.launch {
            _promptUserForApproval.emit(approveSession to additionalMessage)
        }
    }

    private fun unlockSdk(onSDKUnlocked: () -> Unit) {
        if (!FuturaeSDK.client.lockApi.isLocked()) {
            onSDKUnlocked()
            return
        }

        val activeUnlockMethods = FuturaeSDK.client.lockApi.getActiveUnlockMethods()

        when {
            activeUnlockMethods.isEmpty() -> onSDKUnlocked()
            activeUnlockMethods.size == 1 -> dispatchUnlockMethodSelection(activeUnlockMethods.first(), onSDKUnlocked)
            else -> promptUserToPickUnlockMethod {
                dispatchUnlockMethodSelection(it, onSDKUnlocked)
            }
        }
    }

    private fun dispatchUnlockMethodSelection(unlockMethodType: UnlockMethodType, doOnSdkUnlockUnlocked: DoOnSdkUnlockUnlocked) {
        viewModelScope.launch {
            _onUnlockMethodSelected.emit(unlockMethodType to doOnSdkUnlockUnlocked)
        }
    }

    private fun promptUserToPickUnlockMethod(onUnlockMethodPicked: DoOnUnlockMethodPicked) {
        viewModelScope.launch {
            _promptUserToPickUnlockMethod.emit(onUnlockMethodPicked)
        }
    }

    private suspend fun fetchSessionInfo(approveSession: ApproveSession) = viewModelScope.async {
        val userId = approveSession.userId
        val sessionId = approveSession.sessionId

        if (userId == null) {
            notifyUSer(title = "API Error", message = "ApproveSession is incomplete")
            return@async Result.failure<List<ApproveInfo>>(Throwable())
        }

        try {
            val sessionInfo = FuturaeSDK.client.sessionApi
                .getSessionInfo(query = SessionInfoQuery(sessionIdentifier = ById(sessionId), userId = userId))
                .await()

            Result.success(sessionInfo.approveInfo)
        } catch (t: Throwable) {
            Timber.e(t)
            notifyUSer(title =  "Session Api Error", message = "Error fetching session: \n" + t.message)
            Result.failure<List<ApproveInfo>>(Throwable())
        }
    }

    private fun dispatchSdkUnlockError(error: Throwable) {
        viewModelScope.launch {
            _notifyUserAboutError.emit("SDK Unlock" to error)
        }
    }

    private fun notifyUSer(title: String, message: String) {
        viewModelScope.launch {
            _notifyUser.emit(title to message)
        }
    }
}