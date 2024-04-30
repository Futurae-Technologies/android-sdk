package com.futurae.futuraedemo.ui.qr_push_action

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * This class is a drop-in implementation for how activity can pass QR intent action to a fragment.
 * There are other ways to do it, by a delegate, shared view model, etc.
 */
class QRCodeFlowOpenCoordinator private constructor() {
    companion object {
        val instance = QRCodeFlowOpenCoordinator()
    }

    private val _flow = MutableStateFlow(false)

    val flow: StateFlow<Boolean> = _flow

    fun notifyShouldOpenQRCode() {
        _flow.value = true
    }

    fun notifyShouldOpenQRCodeConsumed() {
        _flow.value = false
    }
}