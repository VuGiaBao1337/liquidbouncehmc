/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.ui.client.altmanager.menus

import me.liuli.elixir.account.MicrosoftAccount
import me.liuli.elixir.compat.OAuthServer
import net.ccbluex.liquidbounce.file.FileManager.accountsConfig
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.lang.translationButton
import net.ccbluex.liquidbounce.lang.translationText
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.io.MiscUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawLoadingCircle
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import java.net.BindException

class GuiMicrosoftLoginProgress(val updateStatus: (String) -> Unit, val done: () -> Unit) : AbstractScreen() {

    private var oAuthServer: OAuthServer? = null
    private var loginUrl: String? = null

    private var serverStopAlreadyCalled = false

    override fun initGui() {
        // This will start a login server and open the browser.
        try {
            oAuthServer = MicrosoftAccount.buildFromOpenBrowser(object : MicrosoftAccount.OAuthHandler {

                /**
                 * Called when the user has cancelled the authentication process or the thread has been interrupted
                 */
                override fun authError(error: String) {
                    serverStopAlreadyCalled = true
                    errorAndDone(error)
                    loginUrl = null
                }

                /**
                 * Called when the user has completed authentication
                 */
                override fun authResult(account: MicrosoftAccount) {
                    serverStopAlreadyCalled = true

                    loginUrl = null
                    if (accountsConfig.accountExists(account)) {
                        errorAndDone("The account has already been added.")
                        return
                    }

                    accountsConfig.addAccount(account)
                    saveConfig(accountsConfig)
                    successAndDone()
                }

                /**
                 * Called when the server has prepared the user for authentication
                 */
                override fun openUrl(url: String) {
                    loginUrl = url
                    MiscUtils.showURL(url)
                }

            })
        } catch (bindException: BindException) {
            errorAndDone("Failed to start login server. (Port already in use)")
            LOGGER.error("Failed to start login server.", bindException)
        } catch (e: Exception) {
            errorAndDone("Failed to start login server.")
            LOGGER.error("Failed to start login server.", e)
        }

        +GuiButton(0, width / 2 - 100, height / 2 + 60, translationButton("openURL"))
        +GuiButton(1, width / 2 - 100, height / 2 + 90, translationButton("cancel"))

        super.initGui()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile {
            drawDefaultBackground()
            drawLoadingCircle(width / 2f, height / 4f + 70)
            Fonts.fontSemibold40.drawCenteredString(
                translationText(
                "Loggingintoaccount"), width / 2f, height / 2 - 60f, 0xffffff)

        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        // Not enabled buttons should be ignored
        if (!button.enabled) {
            return
        }

        when (button.id) {
            0 -> loginUrl?.let { MiscUtils.showURL(it) }

            1 -> errorAndDone("Login cancelled.")
        }


        super.actionPerformed(button)
    }

    override fun onGuiClosed() {
        if (!serverStopAlreadyCalled) {
            oAuthServer?.stop(isInterrupt = false)
        }

        super.onGuiClosed()
    }

    private fun successAndDone() {
        updateStatus("§aSuccessfully logged in.")
        done()
    }

    private fun errorAndDone(error: String) {
        updateStatus("§c$error")
        done()
    }

}