package com.flx_apps.distraquit.system_integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flx_apps.distraquit.features.FeaturesProvider
import com.flx_apps.distraquit.feature_types.OnScreenTurnedOffSubscriptionFeature

/**
 * A [BroadcastReceiver] that receives the [Intent.ACTION_SCREEN_OFF] event and forwards it to the
 * [FeaturesProvider.onScreenTurnedOffFeatures].
 */
class ScreenTurnedOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        FeaturesProvider.activeFeatures.intersect(FeaturesProvider.onScreenTurnedOffFeatures)
            .forEach {
                (it as OnScreenTurnedOffSubscriptionFeature).onScreenTurnedOff(context)
            }
    }
}