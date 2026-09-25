package com.qrconnect.owner

import android.app.Application
import com.qrconnect.owner.util.TokenManager

class QRConnectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
    }
}
