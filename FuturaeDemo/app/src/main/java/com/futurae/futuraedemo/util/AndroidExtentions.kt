package com.futurae.futuraedemo.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.futurae.sdk.public_api.session.model.ApproveSession
import com.futurae.sdk.utils.FTNotificationUtils.PARAM_APPROVE_SESSION
import timber.log.Timber

fun Context.showDialog(
    title: String,
    message: String,
    positiveButton: String,
    positiveButtonCallback: () -> Unit,
    negativeButton: String? = null,
    negativeButtonCallback: (() -> Unit)? = null,
) {
    val builder = AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveButton) { dialog, _ ->
            positiveButtonCallback.invoke()
            dialog.dismiss()
        }
    if (negativeButton != null && negativeButtonCallback != null) {
        builder.setNegativeButton(negativeButton) { dialog, _ ->
            negativeButtonCallback.invoke()
            dialog.dismiss()
        }
    }
    builder
        .create()
        .show()
}

fun Context.showAlert(
    title: String,
    message: String,
) {
    android.os.Handler(Looper.getMainLooper()).post {
        AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("ok") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
    }
}

fun Fragment.showAlert(
    title: String,
    message: String
) {
    Timber.i(message)
    val context = this.context ?: return
    context.showAlert(title, message)
}

fun Fragment.showErrorAlert(
    title: String,
    throwable: Throwable
) {
    Timber.e(throwable)
    val context = this.context ?: return
    context.showAlert(title, "Error:\n${throwable.localizedMessage}")
}

fun Activity.showErrorAlert(
    title: String,
    throwable: Throwable
) {
    Timber.e(throwable)
    showAlert(title, "Error:\n${throwable.localizedMessage}")
}

fun Fragment.showDialog(
    title: String,
    message: String,
    positiveButton: String,
    positiveButtonCallback: () -> Unit,
    negativeButton: String? = null,
    negativeButtonCallback: (() -> Unit)? = null,
) {
    val context = context ?: return
    context.showDialog(
        title,
        message,
        positiveButton,
        positiveButtonCallback,
        negativeButton,
        negativeButtonCallback
    )
}

fun ApproveSession.toDialogMessage() = buildString {
    append("\n")
    append(info?.joinToString(separator = "\n") {
        "${it.key}: ${it.value}"
    } ?: "")
    append("\nTimeout: $sessionTimeout")
}

fun <T> Intent.getParcelable(key: String, typeClass: Class<T>) : T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getParcelableExtra(key, typeClass)
    } else {
        getParcelableExtra(key)
    }
}