package com.fredrick.papertrade

import android.content.Context
import com.clevertap.android.sdk.CTInboxListener
import com.clevertap.android.sdk.CTInboxStyleConfig
import com.clevertap.android.sdk.CleverTapAPI
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * CleverTap App Inbox: trade receipts arrive as inbox campaigns. The SDK's inbox screen handles
 * read/delete and the viewed/clicked events; this object only initialises it and exposes the unread count.
 */
object Inbox {
    val unread = MutableStateFlow(0)

    /** Call at startup and again after onUserLogin, because the inbox belongs to the active profile. */
    fun init(ct: CleverTapAPI) {
        ct.ctNotificationInboxListener = object : CTInboxListener {
            override fun inboxDidInitialize() = update(ct)
            override fun inboxMessagesDidUpdate() = update(ct)
        }
        ct.initializeInbox()
    }

    fun update(ct: CleverTapAPI) {
        unread.value = ct.inboxMessageUnreadCount
    }

    fun open(context: Context) {
        val ct = CleverTapAPI.getDefaultInstance(context.applicationContext) ?: return
        val style = CTInboxStyleConfig().apply {
            navBarTitle = "Trade receipts"
            navBarColor = "#315D3C"
            navBarTitleColor = "#FFFFFF"
            backButtonColor = "#FFFFFF"
            inboxBackgroundColor = "#CFE8D2"
            noMessageViewText = "No receipts yet. Your trades will show up here."
            noMessageViewTextColor = "#1F3D2A"
        }
        ct.showAppInbox(style)
    }
}
