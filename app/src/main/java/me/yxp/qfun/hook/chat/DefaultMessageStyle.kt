package me.yxp.qfun.hook.chat

import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.api.GetMsgRecordListener
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.qq.QQCurrentEnv

/** Keep unrelated attributes (honors, reply metadata, etc.) intact when splitting the controls. */
internal fun applyDefaultMessageStyle(record: MsgRecord, bubble: Boolean, font: Boolean, selfUin: Long = QQCurrentEnv.currentUin.toLong()) {
    if (record.senderUin == selfUin) return
    record.msgAttrs?.values?.forEach { attribute ->
        val vas = attribute.vasMsgInfo ?: return@forEach
        if (bubble) vas.bubbleInfo?.let { it.bubbleId = 0; it.subBubbleId = 0 }
        if (font) vas.vasFont?.let { it.fontId = 0; it.magicFontType = 0 }
    }
}

@HookItemAnnotation("默认消息气泡", "独立控制他人的消息气泡", HookCategory.CHAT)
object DefaultBubble : BaseSwitchHookItem(), GetMsgRecordListener {
    override fun onGet(msgRecord: MsgRecord) = applyDefaultMessageStyle(msgRecord, true, false)
}

@HookItemAnnotation("默认消息字体", "独立控制他人的消息字体", HookCategory.CHAT)
object DefaultFont : BaseSwitchHookItem(), GetMsgRecordListener {
    override fun onGet(msgRecord: MsgRecord) = applyDefaultMessageStyle(msgRecord, false, true)
}
