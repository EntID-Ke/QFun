package me.yxp.qfun.hook.chat

import com.tencent.qqnt.kernel.nativeinterface.*
import org.junit.Assert.*
import org.junit.Test

class DefaultMessageStyleTest {
    private fun message():MsgRecord = MsgRecord().apply {
        senderUin=42L
        msgAttrs=hashMapOf(1 to MsgAttributeInfo().apply {
            attrId=99L
            vasMsgInfo=VASMsgElement().apply {
                bubbleInfo.bubbleId=17; bubbleInfo.subBubbleId=18
                vasFont.fontId=29; vasFont.magicFontType=30
            }
        },2 to MsgAttributeInfo().apply{attrId=101L})
    }
    @Test fun defaultBubbleDoesNotDiscardFontOrOtherAttributes() {
        val record=message();applyDefaultMessageStyle(record,true,false,100L)
        assertEquals(0,record.msgAttrs[1]!!.vasMsgInfo.bubbleInfo.bubbleId.toInt())
        assertEquals(29,record.msgAttrs[1]!!.vasMsgInfo.vasFont.fontId.toInt())
        assertEquals(99L,record.msgAttrs[1]!!.attrId);assertEquals(101L,record.msgAttrs[2]!!.attrId)
    }
    @Test fun defaultFontDoesNotDiscardBubble() {
        val record=message();applyDefaultMessageStyle(record,false,true,100L)
        assertEquals(17,record.msgAttrs[1]!!.vasMsgInfo.bubbleInfo.bubbleId.toInt())
        assertEquals(0,record.msgAttrs[1]!!.vasMsgInfo.vasFont.fontId.toInt())
        assertEquals(2,record.msgAttrs.size)
    }
    @Test fun ownMessagesKeepTheirExistingAppearance() {
        val record=message();applyDefaultMessageStyle(record,true,true,42L)
        assertEquals(17,record.msgAttrs[1]!!.vasMsgInfo.bubbleInfo.bubbleId.toInt())
        assertEquals(29,record.msgAttrs[1]!!.vasMsgInfo.vasFont.fontId.toInt())
    }
}
