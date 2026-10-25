package me.yxp.qfun.hook.base

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.qq.QQCurrentEnv
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("DEPRECATION")
abstract class BaseSwitchHookItem : BaseHookItem() {

    val tag: String get() = annotation?.tag ?: "Unknown"
    val desc: String get() {
        if (io.github.qqmodulemanager.bridge.BridgeClient.isManaged(
                io.github.qqmodulemanager.bridge.BridgeClient.featureForClass(javaClass.name))) return "当前由 QIII管理"
        val originalDesc = annotation?.desc ?: ""
        return if (isNeedRestart) "$originalDesc，重启生效" else originalDesc
    }
    val category: String get() = annotation?.category ?: HookCategory.OTHER

    override var isEnable: Boolean
        get() = io.github.qqmodulemanager.bridge.BridgeClient.effective(
            io.github.qqmodulemanager.bridge.BridgeClient.featureForClass(javaClass.name), prefs.getBoolean(name, false))
        set(value) {
            if (!io.github.qqmodulemanager.bridge.BridgeClient.isManaged(
                    io.github.qqmodulemanager.bridge.BridgeClient.featureForClass(javaClass.name))) {
                prefs.edit { putBoolean(name, value) }
            }
        }
    var isAvailable: Boolean = false
    private var qqmmInitialized = false

    open val isNeedRestart: Boolean = false

    fun init() {
        val managedFeature = io.github.qqmodulemanager.bridge.BridgeClient.featureForClass(javaClass.name)
        if (io.github.qqmodulemanager.bridge.BridgeClient.deferInitialization(managedFeature)) return
        if (managedFeature.isNotEmpty() && qqmmInitialized) return
        try {
            isAvailable = onInit()
            if (isAvailable && isInTargetProcess()) {
                if (this is BaseClickableHookItem<*>) initData()
                io.github.qqmodulemanager.bridge.BridgeClient.hooksStarting(managedFeature)
                onHook()
                if (managedFeature.isNotEmpty()) qqmmInitialized = true
            }
        } catch (t: Throwable) {
            LogUtils.e(this, t)
            isAvailable = false
        }
    }

    protected open fun onInit(): Boolean = true

    protected open fun onHook() {}

    class BooleanPreference(private val key: String, private val default: Boolean) :
        ReadWriteProperty<Any, Boolean> {


        override fun getValue(thisRef: Any, property: KProperty<*>): Boolean {
            return prefs.getBoolean(key, default)
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
            prefs.edit { putBoolean(key, value) }
        }
    }

    companion object {
        val prefs: SharedPreferences
            get() = HostInfo.hostContext.getSharedPreferences(
                "QFun_Config_${QQCurrentEnv.currentUin}",
                Context.MODE_MULTI_PROCESS
            )

    }

}
