package me.yxp.qfun.hook.api

import android.content.Context
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.aio.menu.h
import com.tencent.qqnt.aio.menu.ui.c
import com.tencent.qqnt.kernel.nativeinterface.*
import me.yxp.qfun.loader.hookapi.*
import me.yxp.qfun.plugin.bean.MsgData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Member
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class QQ9375MenuItemsTest {
    private class Engine:IHookEngine {
        override val apiLevel=82;override val frameworkName="test";override val frameworkVersion="test";override val frameworkVersionCode=1L;override val bridgeClass:Class<*>?=null
        val callbacks=mutableMapOf<Member,(HookParam)->Unit>()
        override fun hookBefore(method:Member,priority:Int,callback:(HookParam)->Unit):Unhook{callbacks[method]=callback;return Unhook{}}
        override fun hookAfter(method:Member,priority:Int,callback:(HookParam)->Unit)=Unhook{}
        override fun hookReplace(method:Member,priority:Int,callback:(Chain)->Any?):Unhook=throw AssertionError("Native QQ actions must retain the normal hook chain")
        override fun getInvoker(method:Member):Invoker=throw UnsupportedOperationException()
        override fun deoptimize(method:Member)=false
        override fun log(priority:Int,tag:String?,msg:String,t:Throwable?){}
        fun call(target:h,name:String):Any? {
            val targetMethod=h::class.java.getDeclaredMethod(name)
            var intercepted=false
            val param=object:HookParam {
                override val method:Member=targetMethod;override val thisObject:Any=target
                override var args=arrayOfNulls<Any>(0)
                override var result:Any?=null
                    set(value){field=value;intercepted=true}
                override var throwable:Throwable?=null
            }
            callbacks[targetMethod]!!.invoke(param)
            return if(intercepted)param.result else targetMethod.invoke(target)

        }
    }
    private class Listener(id:String,title:String):MenuClickListener {
        override val menuKey="[QFun],$id,$title,,2,9"
        var clicked:MsgRecord?=null
        override fun onClick(msgData:MsgData){clicked=msgData.data}
    }
    private fun text(type:Int=2)=MsgRecord().apply {
        msgType=type;chatType=2;peerUid="test";guildId=""
        elements=arrayListOf(MsgElement().apply{elementType=1;textElement=TextElement().apply{content="test";atType=0}})
    }
    @Test fun bothActionsAreCreatedAndClickedThroughNativeItemMethodsWithoutCustomViews() {
        val context=RuntimeEnvironment.getApplication();val engine=Engine();val adapter=QQ9375MenuItems(javaClass.classLoader!!,engine)
        val record=text();val original=h(context,AIOMsgItem(record));val model=c(listOf(original))
        val resend=Listener("FastReSend","撤回重发");val edit=Listener("ModifyMsgContent","文本修改")
        adapter.append(model,context,listOf(resend,edit));assertEquals(3,model.a.size)
        val titles=model.a.map{engine.call(it as h,"h")}
        assertEquals(setOf("复制","撤回重发","文本修改"),titles.toSet())
        assertEquals(3,model.a.map{engine.call(it as h,"c")}.distinct().size)
        for(item in model.a.filter{it!==original})engine.call(item as h,"k")
        assertSame(record,resend.clicked);assertSame(record,edit.clicked);assertEquals(0,original.clicks)
        engine.call(original,"k");assertEquals(1,original.clicks)
        adapter.append(model,context,listOf(resend,edit));assertEquals(3,model.a.size)
    }
    @Test fun textActionsUseMessageElementsAndDoNotDependOnTheLegacyMsgTypeNumbers() {
        val context=RuntimeEnvironment.getApplication();val engine=Engine();val adapter=QQ9375MenuItems(javaClass.classLoader!!,engine)
        val model=c(listOf(h(context,AIOMsgItem(text(0)))))
        adapter.append(model,context,listOf(Listener("FastReSend","撤回重发"),Listener("ModifyMsgContent","文本修改")))
        assertEquals(3,model.a.size)
    }
    @Test fun messagesWithoutEditableElementsKeepOnlyTheirOriginalAction() {
        val context=RuntimeEnvironment.getApplication();val engine=Engine();val adapter=QQ9375MenuItems(javaClass.classLoader!!,engine)
        val record=text().apply{elements=arrayListOf(MsgElement().apply{elementType=4})}
        val original=h(context,AIOMsgItem(record));val model=c(listOf(original))
        adapter.append(model,context,listOf(Listener("FastReSend","撤回重发"),Listener("ModifyMsgContent","文本修改")))
        assertEquals(listOf(original),model.a)
    }
}
