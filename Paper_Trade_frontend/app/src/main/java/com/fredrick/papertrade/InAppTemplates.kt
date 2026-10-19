package com.fredrick.papertrade

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clevertap.android.sdk.CleverTapAPI
import com.clevertap.android.sdk.inapp.customtemplates.CustomTemplateContext.FunctionContext
import com.clevertap.android.sdk.inapp.customtemplates.CustomTemplateContext.TemplateContext
import com.clevertap.android.sdk.inapp.customtemplates.FunctionPresenter
import com.clevertap.android.sdk.inapp.customtemplates.TemplatePresenter
import com.clevertap.android.sdk.inapp.customtemplates.function
import com.clevertap.android.sdk.inapp.customtemplates.template

const val MESSAGE_TEMPLATE = "Paper Trade Message"
const val PRIMER_TEMPLATE = "Paper Trade Push Primer"

/** What the Compose overlay shows. Callbacks are wired to the CleverTap template context. */
class InAppCard(
    val template: String,
    val title: String,
    val message: String,
    val primaryText: String,
    val secondaryText: String,
    val accent: String,
    val onPrimary: () -> Unit,
    val onSecondary: () -> Unit
)

/** The card currently waiting to be shown; InAppOverlayHost (in MainActivity's content) renders it. */
object InAppOverlay {
    var current by mutableStateOf<InAppCard?>(null)
}

/**
 * Native templates for CleverTap campaigns. They show up in the dashboard after
 * `syncRegisteredInAppTemplates()` (debug builds only), and must be registered before CleverTap starts.
 */
object InAppTemplates {

    fun register(context: Context) {
        val appContext = context.applicationContext
        CleverTapAPI.registerCustomInAppTemplates {
            setOf(
                template {
                    name(MESSAGE_TEMPLATE)
                    presenter(CardPresenter(appContext, isPrimer = false))
                    stringArgument("title", "Welcome")
                    stringArgument("message", "")
                    stringArgument("primaryText", "Continue")
                    stringArgument("secondaryText", "Not now")
                    stringArgument("accent", "#315D3C")
                    actionArgument("primaryAction")
                },
                template {
                    name(PRIMER_TEMPLATE)
                    presenter(CardPresenter(appContext, isPrimer = true))
                    stringArgument("title", "Stay in the loop")
                    stringArgument("message", "Turn on notifications to hear about price moves and your trades.")
                    stringArgument("primaryText", "Allow")
                    stringArgument("secondaryText", "Not now")
                    stringArgument("accent", "#315D3C")
                },
                // Non-visual functions: pickable as button actions in the dashboard, they just switch tab.
                function(isVisual = false) {
                    name("Open Stocks")
                    presenter(NavPresenter("open_stocks"))
                },
                function(isVisual = false) {
                    name("Open Trade")
                    presenter(NavPresenter("open_trade"))
                },
                function(isVisual = false) {
                    name("Open Portfolio")
                    presenter(NavPresenter("open_portfolio"))
                }
            )
        }
    }
}

private class NavPresenter(private val action: String) : FunctionPresenter {
    override fun onPresent(context: FunctionContext) {
        InAppNav.request.value = action
    }
}

private class CardPresenter(private val appContext: Context, private val isPrimer: Boolean) : TemplatePresenter {

    override fun onPresent(context: TemplateContext) {
        val name = context.templateName
        val card = InAppCard(
            template = name,
            title = context.getString("title").orEmpty(),
            message = context.getString("message").orEmpty(),
            primaryText = context.getString("primaryText").orEmpty(),
            secondaryText = context.getString("secondaryText").orEmpty(),
            accent = context.getString("accent").orEmpty(),
            onPrimary = {
                Analytics.inAppButtonClicked(appContext, name, "primary", name)
                InAppHandler.rememberClick(appContext, name, "primary")
                if (isPrimer) {
                    CleverTapAPI.getDefaultInstance(appContext)?.promptForPushPermission(false)
                } else {
                    context.triggerActionArgument("primaryAction", appContext)
                }
                close(context, "primary")
            },
            onSecondary = {
                Analytics.inAppButtonClicked(appContext, name, "secondary", name)
                close(context, "secondary")
            }
        )
        InAppOverlay.current = card
        context.setPresented()
        Analytics.inAppShown(appContext, name, name)
    }

    override fun onClose(context: TemplateContext) {
        close(context, "closed_by_sdk")
    }

    private fun close(context: TemplateContext, reason: String) {
        if (InAppOverlay.current == null) return
        InAppOverlay.current = null
        context.setDismissed()
        Analytics.inAppDismissed(appContext, context.templateName, context.templateName, reason)
    }
}
