package me.yxp.qfun.hook.base

import me.yxp.qfun.generated.HookRegistry
import java.lang.reflect.ParameterizedType

abstract class BaseApiHookItem<T : Listener> : BaseHookItem() {
    var qqmmReady: Boolean = false


    @Suppress("UNCHECKED_CAST")
    val listenerSet: MutableSet<T> by lazy {

        val superclass = this::class.java.genericSuperclass

        if (superclass !is ParameterizedType) {
            throw IllegalStateException("Class ${this::class.java.name} must inherit BaseApiHookItem with a generic type.")
        }

        val listenerClass = superclass.actualTypeArguments[0] as Class<T>


        HookRegistry.hookItems
            .filter { listenerClass.isInstance(it) }
            .map { it as T }
            .toMutableSet()

    }

    abstract fun loadHook()

    fun addListener(listener: T) {
        listenerSet.add(listener)
    }

    fun removeListener(listener: T) {
        listenerSet.remove(listener)
    }

    protected inline fun forEachChecked(action: (T) -> Unit) {
        for (listener in listenerSet) {
            if (listener !is BaseSwitchHookItem || listener.isEnable) {
                if (listener is BaseSwitchHookItem && io.github.qqmodulemanager.bridge.BridgeClient.featureForClass(listener.javaClass.name).isNotEmpty()) {
                    try { action(listener) } catch (t: Throwable) { me.yxp.qfun.utils.log.LogUtils.e(listener, t) }
                } else action(listener)
            }
        }
    }

}

interface Listener

