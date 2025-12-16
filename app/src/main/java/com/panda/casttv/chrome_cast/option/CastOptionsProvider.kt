package com.panda.casttv.chrome_cast.option

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.panda.casttv.uitls.Common

class CastOptionsProvider : OptionsProvider {
    companion object {
        const val CUSTOM_NAMESPACE = "urn:x-cast:custom_namespace"
    }

    override fun getCastOptions(appContext: Context): CastOptions {
        val supportedNamespaces: MutableList<String> = ArrayList()
        supportedNamespaces.add(CUSTOM_NAMESPACE)

        return CastOptions.Builder()
            .setReceiverApplicationId(Common.CAST_APP_ID)
            .setSupportedNamespaces(supportedNamespaces)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}