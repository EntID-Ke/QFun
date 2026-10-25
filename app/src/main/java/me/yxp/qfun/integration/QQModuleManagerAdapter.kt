package me.yxp.qfun.integration

import io.github.qqmodulemanager.bridge.BridgeClient
import me.yxp.qfun.BuildConfig
import io.github.qqmodulemanager.bridge.FeatureCatalog
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.hook.base.BaseApiHookItem
import me.yxp.qfun.hook.base.Listener
import me.yxp.qfun.generated.HookRegistry
import me.yxp.qfun.utils.qq.HostInfo

object QQModuleManagerAdapter {
    fun register() {
        BridgeClient.initialize(HostInfo.hostContext, "qfun", BuildConfig.VERSION_NAME)
        for (feature in FeatureCatalog.ALL) {
            val classes=feature.classes("qfun")
            if (classes.isEmpty()) continue
            BridgeClient.registerFeature(feature.id) {
                val hook=Class.forName(classes.first()).getField("INSTANCE").get(null) as BaseSwitchHookItem
                val dependenciesReady=HookRegistry.hookItems.filterIsInstance<BaseApiHookItem<*>>()
                    .none { api -> hook is Listener && api.listenerSet.any { it === hook } && !api.qqmmReady }
                if (!hook.isInTargetProcess() || !dependenciesReady) false else { hook.init(); hook.isAvailable }
            }
        }
    }
}
