package me.yxp.qfun.hook.api

import android.content.Context
import android.view.View
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import io.github.qqmodulemanager.bridge.BridgeClient
import me.yxp.qfun.loader.hookapi.IHookEngine
import me.yxp.qfun.plugin.bean.MsgData
import java.util.Collections
import java.util.WeakHashMap

/** Native QQ items carry their own title, ID and click action, including menus without icons. */
internal class QQ9375MenuItems(loader:ClassLoader,engine:IHookEngine) {
    private data class Action(val key:String,val title:String,val id:Int,val record:MsgRecord,val listener:MenuClickListener)
    private val actions=Collections.synchronizedMap(WeakHashMap<Any,Action>())
    private val model=loader.loadClass("com.tencent.qqnt.aio.menu.ui.c").getDeclaredField("a").apply{isAccessible=true}
    private val base=loader.loadClass("com.tencent.qqnt.aio.menu.ui.e")
    private val message=base.getDeclaredMethod("d")
    private val item=loader.loadClass("com.tencent.qqnt.aio.menu.h")
    private val constructor=item.getDeclaredConstructor(Context::class.java,AIOMsgItem::class.java)
    init {
        engine.hookBefore(item.getDeclaredMethod("h")){param->actions[param.thisObject]?.let{param.result=it.title}}
        engine.hookBefore(item.getDeclaredMethod("c")){param->actions[param.thisObject]?.let{param.result=it.id}}
        engine.hookBefore(item.getDeclaredMethod("k")){param->
            val action=actions[param.thisObject]
            if(action!=null) {
                param.result=null
                try{action.listener.onClick(MsgData(action.record))}catch(error:Throwable){
                    val owner=action.listener
                    if(owner is me.yxp.qfun.hook.base.BaseHookItem)me.yxp.qfun.utils.log.LogUtils.e(owner,error)
                    else me.yxp.qfun.utils.log.LogUtils.e("消息菜单",error)
                }
            }
        }
    }
    @Suppress("UNCHECKED_CAST")
    fun append(menu:Any,context:Context,listeners:List<MenuClickListener>) {
        val items=model.get(menu) as MutableList<Any>
        val first=items.firstOrNull()?:return
        val aio=message.invoke(first) as AIOMsgItem
        val record=aio.msgRecord
        for(listener in listeners) {
            val key=listener.menuKey;val parts=key.split(",")
            if(parts.size<5 || items.any{actions[it]?.key==key})continue
            // These actions consume actual text/image elements; msgType alone also covers unrelated records.
            val supported=when(parts[1]) {
                "ModifyMsgContent"->record.elements.any{it.textElement?.atType==0}
                "FastReSend"->record.elements.isNotEmpty() && record.elements.all{it.textElement!=null || it.picElement!=null}
                else->parts.drop(4).filter{it.isNotEmpty()}.let{it.isEmpty() || record.msgType.toString() in it}
            }
            if(!supported)continue
            val added=constructor.newInstance(context,aio)
            actions[added]=Action(key,parts[2],View.generateViewId(),record,listener)
            BridgeClient.tagMenuItem(added,"qfun",parts[1]+":"+parts[2],parts[2])
            items.add(0,added)
        }
    }
}
