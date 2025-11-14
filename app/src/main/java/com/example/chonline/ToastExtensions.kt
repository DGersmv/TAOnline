package com.example.chonline

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast

fun Context.showTopToast(message: String, duration: Int = Toast.LENGTH_SHORT, yOffsetDp: Int = 24) {
    val toastContext = applicationContext ?: this
    val toast = Toast.makeText(toastContext, message, duration)
    val yOffsetPx = (toastContext.resources.displayMetrics.density * yOffsetDp).toInt()
    toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, yOffsetPx)
    toast.show()
}

fun Context.postTopToast(message: String, duration: Int = Toast.LENGTH_SHORT, yOffsetDp: Int = 24) {
    Handler(Looper.getMainLooper()).post {
        showTopToast(message, duration, yOffsetDp)
    }
}
