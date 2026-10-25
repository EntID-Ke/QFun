package me.yxp.qfun.hook.api

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.aio.menu.ui.QQCustomMenuExpandableLayout
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import me.yxp.qfun.R
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseApiHookItem
import me.yxp.qfun.hook.base.Listener
import me.yxp.qfun.plugin.bean.MsgData
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.hook.hookReplace
import me.yxp.qfun.utils.hook.invokeOriginal
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.QQCurrentEnv
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.getObjectByType
import me.yxp.qfun.utils.reflect.getObjectByTypeOrNull
import me.yxp.qfun.utils.reflect.newInstanceWithArgs
import me.yxp.qfun.utils.reflect.setObjectByType
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@HookItemAnnotation("监听消息菜单")
object OnMenuBuild : BaseApiHookItem<MenuClickListener>(), DexKitTask {

    private const val PREFIX = "[QFun]"

    private const val MENU_TYPE = "CopyMenuItem"

    override fun loadHook() {
        if(me.yxp.qfun.utils.qq.HostInfo.isQQ && me.yxp.qfun.utils.qq.HostInfo.versionCode==16580L) {
            val loader=me.yxp.qfun.utils.reflect.ClassUtils.hostClassLoader
            val native=QQ9375MenuItems(loader,me.yxp.qfun.loader.hookapi.HookEngineManager.engine)
            val contribute:(Any)->Unit={menu->
                val enabled=mutableListOf<MenuClickListener>()
                forEachChecked{enabled.add(it)}
                native.append(menu,me.yxp.qfun.utils.qq.HostInfo.hostContext,enabled)
            }
            for(name in listOf("QQCustomMenuExpandableLayout","QQCustomMenuNoIconLayout"))
                loader.loadClass("com.tencent.qqnt.aio.menu.ui.$name").getDeclaredMethod("setMenu",loader.loadClass("com.tencent.qqnt.aio.menu.ui.c"),View::class.java)
                    .hookBefore(this){param->contribute(param.args[0]!!)}
            io.github.qqmodulemanager.bridge.BridgeClient.registerMenuContributor{contribute(it)}
            return
        }

        val itemClass = requireClass(MENU_TYPE)
        val itemSuperClass = itemClass.superclass!!

        io.github.qqmodulemanager.bridge.BridgeClient.registerMenuContributor { customMenu ->
            contribute(customMenu, itemClass, itemSuperClass)
        }
        QQCustomMenuExpandableLayout::class.java.findMethod { name = "setMenu" }
            .hookBefore(this) { param ->
                // Keep QFun's own contribution alive even while the manager/provider is restarting.
                // The manager runs last and deduplicates these same item instances before sorting.
                param.args[0]?.let { contribute(it, itemClass, itemSuperClass) }
            }

        QQCustomMenuExpandableLayout::class.java.findMethod {
            returnType = view
            paramTypes(int, itemClass, boolean, floatArr)
        }.hookReplace(this) { param ->
            val menuItem = param.args[1] ?: return@hookReplace param.invokeOriginal()
            if (menuItem.javaClass != itemClass)
                return@hookReplace param.invokeOriginal()
            val label = menuItem.getObjectByType<String>()

            if (label.startsWith(PREFIX)) {

                val aioMsgItem = menuItem.getObjectByType<AIOMsgItem>(itemSuperClass)
                val msgRecord = aioMsgItem.msgRecord

                return@hookReplace createMenuItemView(
                    label,
                    msgRecord,
                    param.thisObject as QQCustomMenuExpandableLayout
                )
            }

            return@hookReplace param.invokeOriginal()
        }
    }

    private fun contribute(customMenu: Any, itemClass: Class<*>, itemSuperClass: Class<*>) {
        val items = customMenu.getObjectByTypeOrNull<MutableList<Any>>() ?: return
        if (items.isEmpty()) return
        val aioMsgItem = items[0].getObjectByType<AIOMsgItem>(itemSuperClass)
        val msgType = aioMsgItem.msgRecord.msgType.toString()
        forEachChecked { listener ->
            if (items.any { isModuleItem(it, listener.menuKey) }) return@forEachChecked
            val args = listener.menuKey.split(",")
            if (args.size < 5) return@forEachChecked
            val targetTypes = args.subList(4, args.size).filter { it.isNotEmpty() }
            if (targetTypes.isNotEmpty() && !targetTypes.contains(msgType)) return@forEachChecked
            addMenuItem(items, itemClass, listener.menuKey, aioMsgItem)
        }
    }

    private fun addMenuItem(
        items: MutableList<Any>,
        itemClass: Class<*>,
        key: String,
        aioMsgItem: AIOMsgItem
    ) {
        val context = QQCurrentEnv.activity ?: QQCurrentEnv.qQAppInterface.application
        val newItem = itemClass.newInstanceWithArgs(context, aioMsgItem)
        newItem.setObjectByType(key)
        val parts = key.split(",")
        io.github.qqmodulemanager.bridge.BridgeClient.tagMenuItem(newItem, "qfun", parts[1] + ":" + parts[2], parts[2])
        items.add(0, newItem)
    }

    private fun createMenuItemView(
        menuKey: String,
        msgRecord: MsgRecord,
        expandable: QQCustomMenuExpandableLayout
    ): View {

        val activity = QQCurrentEnv.activity ?: throw IllegalStateException("Activity is null")
        val args = menuKey.split(",")
        val menuName = args[2]

        val layout = LayoutInflater.from(activity).inflate(R.layout.item_msg_menu, null)
        val nameText = layout.findViewById<TextView>(R.id.tv_msg_menu)
        nameText.apply {
            text = menuName
            setTextColor(Color.WHITE)
        }
        layout.setOnClickListener {
            try {
                listenerSet.single { it.menuKey == menuKey }.onClick(MsgData(msgRecord))
            } catch (t: Throwable) {
                LogUtils.e(this, t)
            } finally {
                expandable.dismiss()
            }
        }
        return layout
    }

    private fun isModuleItem(item: Any, key: String): Boolean {
        return runCatching {
            val label = item.getObjectByType<String>()
            label == key
        }.getOrElse { false }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        MENU_TYPE to FindClass().apply {
            searchPackages("com.tencent.qqnt.aio.menu")
            matcher {
                usingStrings(MENU_TYPE)
            }
        }
    )

}

interface MenuClickListener : Listener {
    val menuKey: String
    fun onClick(msgData: MsgData)
}
