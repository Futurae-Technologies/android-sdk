package com.futurae.futuraedemo.ui

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.futurae.sdk.approve.ApproveSession
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
        .setPositiveButton(positiveButton) { dialog, which ->
            positiveButtonCallback.invoke()
            dialog.dismiss()
        }
    if (negativeButton != null && negativeButtonCallback != null) {
        builder.setNegativeButton(negativeButton) { dialog, which ->
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
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("ok") { dialog, which ->
            dialog.dismiss()
        }
        .create()
        .show()
}

fun Fragment.showAlert(
    title: String,
    message: String
) {
    Timber.i(message)
    requireContext().showAlert(title, message)
}

fun Fragment.showErrorAlert(
    title: String,
    throwable : Throwable
) {
    Timber.e(throwable)
    requireContext().showAlert(title, "Error:\n${throwable.localizedMessage}")
}

fun Activity.showErrorAlert(
    title: String,
    throwable : Throwable
) {
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
    requireContext().showDialog(
        title,
        message,
        positiveButton,
        positiveButtonCallback,
        negativeButton,
        negativeButtonCallback
    )
}


fun ApproveSession.toDialogMessage(): String {
    val sb = StringBuffer()
    if (info != null) {
        sb.append("\n")
        for (info in info) {
            sb.append(info.key).append(": ").append(info.value).append("\n")
        }
    }
    return sb.toString()
}