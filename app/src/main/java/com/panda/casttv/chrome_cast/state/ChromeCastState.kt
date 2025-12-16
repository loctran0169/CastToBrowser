package com.panda.casttv.chrome_cast.state

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.tv.CastReceiverContext
import com.google.android.gms.cast.tv.media.MediaManager
import com.panda.casttv.chrome_cast.models.ChromeCastDevice
import com.panda.casttv.uitls.Common

@Immutable
open class ChromeCastState(context: Context) {

    private val TAG: String
        get() = "ChromeCastState"

    init {
        Log.d(TAG, "init: ")
    }

    val items = mutableStateListOf<ChromeCastDevice>()


    var castSession: Session? = null
    var castSessionEnabled = false
    val mediaRouter: MediaRouter = MediaRouter.getInstance(context)
    val castReceiverContext: CastReceiverContext? = CastReceiverContext.getInstance()

    val castContext = CastContext.getSharedInstance(context)
    val castSessionManager = castContext.sessionManager

    val mediaSession = MediaSessionCompat(context, TAG)
    val mediaManager: MediaManager? = castReceiverContext?.mediaManager?.apply {
        setSessionCompatToken(mediaSession.sessionToken)
    }

    val mediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
        .addControlCategory(CastMediaControlIntent.categoryForCast(Common.CAST_APP_ID))
        .build()

    val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            Log.d(TAG, "onRouteSelected: $route")
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            Log.d(TAG, "onRouteUnselected: ")
        }

        override fun onRoutePresentationDisplayChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Log.d(TAG, "onRoutePresentationDisplayChanged: ")
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Log.d(TAG, "onRouteChanged: ")
        }
    }
    val sessionManagerListener = object : SessionManagerListener<Session> {
        override fun onSessionStarting(session: Session) {
            Log.d(TAG, "onSessionStarting: ")
            castSession = session as? CastSession
            castSessionEnabled = true
        }

        override fun onSessionStarted(session: Session, p1: String) {
            Log.d(TAG, "onSessionStarted: ")
        }

        override fun onSessionStartFailed(session: Session, p1: Int) {
            Log.d(TAG, "onSessionStartFailed: ")
        }

        override fun onSessionEnding(session: Session) {
            Log.d(TAG, "onSessionEnding: ")
        }

        override fun onSessionEnded(session: Session, p1: Int) {
            Log.d(TAG, "onSessionEnded: ")
            castSessionEnabled = false
        }

        override fun onSessionResuming(session: Session, p1: String) {
            Log.d(TAG, "onSessionResuming: ")
        }

        override fun onSessionResumed(session: Session, p1: Boolean) {
            Log.d(TAG, "onSessionResumed: ")
            castSessionEnabled = false
        }

        override fun onSessionResumeFailed(session: Session, p1: Int) {
            Log.d(TAG, "onSessionResumeFailed: ")
        }

        override fun onSessionSuspended(session: Session, p1: Int) {
            Log.d(TAG, "onSessionSuspended: ")
            castSessionEnabled = false
        }
    }
}