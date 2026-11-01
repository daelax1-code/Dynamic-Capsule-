package app.capsule.island

import android.app.Application

class CapsuleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        SystemControls.init(this)
    }
}
