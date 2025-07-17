/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.ui.client.altmanager.menus.altgenerator

import com.mojang.authlib.Agent.MINECRAFT
import com.mojang.authlib.exceptions.AuthenticationException
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.event.EventManager.call
import net.ccbluex.liquidbounce.event.SessionUpdateEvent
import net.ccbluex.liquidbounce.lang.translationButton
import net.ccbluex.liquidbounce.ui.client.altmanager.GuiAltManager
import net.ccbluex.liquidbounce.ui.elements.GuiPasswordField
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.client.TabUtils
import net.ccbluex.liquidbounce.utils.io.MiscUtils
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiTextField
import net.minecraft.util.Session
import org.lwjgl.input.Keyboard
import java.net.Proxy.NO_PROXY

class GuiHeoMCGen(private val prevGui: GuiAltManager) : AbstractScreen() {

    // Data Storage
    companion object {
        const val ALTS_URL = "https://lists.theatlantis.asia/file/alts.txt"
        var lastGeneratedAlt: Triple<String, String, String>? = null
    }

    // Buttons
    private lateinit var loginButton: GuiButton
    private lateinit var generateButton: GuiButton

    // User Input Fields
    private lateinit var usernameField: GuiTextField
    private lateinit var passwordField: GuiTextField
    private lateinit var emailField: GuiTextField

    // Status
    private var status = ""

    /**
     * Initialize HeoMC Generator GUI
     */
    override fun initGui() {
        // Enable keyboard repeat events
        Keyboard.enableRepeatEvents(true)

        // Login button
        loginButton = +GuiButton(2, width / 2 - 100, height / 2 + 20, "Login as Cracked")

        // Generate button
        generateButton = +GuiButton(1, width / 2 - 100, height / 2 + 45, "Generate Random Alt")

        // Back button
        +GuiButton(0, width / 2 - 100, height / 2 + 70, 200, 20, translationButton("back"))

        // Username field
        usernameField = GuiTextField(666, Fonts.fontSemibold40, width / 2 - 100, height / 2 - 120, 200, 20)
        usernameField.isFocused = false
        usernameField.maxStringLength = 64

        // Password field
        passwordField = GuiTextField(1337, Fonts.fontSemibold40, width / 2 - 100, height / 2 - 80, 200, 20)
        passwordField.maxStringLength = 64

        // Email field
        emailField = GuiTextField(1338, Fonts.fontSemibold40, width / 2 - 100, height / 2 - 40, 200, 20)
        emailField.maxStringLength = 128

        lastGeneratedAlt?.let { (username, email, password) ->
            usernameField.text = username
            emailField.text = email
            passwordField.text = password
        }
        super.initGui()
    }

    /**
     * Draw screen
     */
    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile {
            // Draw background to screen
            drawBackground(0)
            drawRect(30f, 30f, width - 30f, height - 30f, Integer.MIN_VALUE)

            // Draw title and status
            Fonts.fontSemibold40.drawCenteredString("Alt Generator", width / 2f, height / 2 - 180f, 0xffffff)
            Fonts.fontSemibold35.drawCenteredString(status, width / 2f, height / 2 + 45f, 0xffffff)

            // Draw fields
            usernameField.drawTextBox()
            passwordField.drawTextBox()
            emailField.drawTextBox()

            // Draw text
            if (usernameField.text.isEmpty() && !usernameField.isFocused)
                Fonts.fontSemibold40.drawCenteredString("§7Username", width / 2f - 82, height / 2 - 114f, 0xffffff)
            if (passwordField.text.isEmpty() && !passwordField.isFocused)
                Fonts.fontSemibold40.drawCenteredString("§7Password", width / 2f - 82, height / 2 - 74f, 0xffffff)
            if (emailField.text.isEmpty() && !emailField.isFocused)
                Fonts.fontSemibold40.drawCenteredString("§7Email", width / 2f - 82, height / 2 - 34f, 0xffffff)
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    /**
     * Handle button actions
     */
    override fun actionPerformed(button: GuiButton) {
        if (!button.enabled) return

        when (button.id) {
            0 -> mc.displayGuiScreen(prevGui)
            1 -> {
                loginButton.enabled = false
                generateButton.enabled = false
                
                SharedScopes.IO.launch {
                    try {
                        status = "§aFetching alts list..."
                        
                        val response = java.net.URL(ALTS_URL).readText()
                        val alts = response.lines().filter { it.isNotEmpty() }
                        
                        if (alts.isEmpty()) {
                            status = "§cNo alts available!"
                            return@launch
                        }
                        
                        // Randomly select an alt
                        val randomAlt = alts.random()
                        val parts = randomAlt.split(":")
                        
                        if (parts.size >= 3) {
                            val (username, email, password) = parts
                            lastGeneratedAlt = Triple(username, email, password)
                            
                            usernameField.text = username
                            emailField.text = email
                            passwordField.text = password
                            
                            status = "§aSuccessfully fetch alt!"
                        } else {
                            status = "§cInvalid alt format!"
                        }
                    } catch (throwable: Throwable) {
                        LOGGER.error("Failed to generate alt.", throwable)
                        status = "§cFailed to generate alt: ${throwable.message}"
                    }
                    
                    loginButton.enabled = true
                    generateButton.enabled = true
                }
            }

            2 -> {
                if (usernameField.text.isEmpty()) {
                    status = "§cPlease enter a username!"
                    return
                }

                loginButton.enabled = false
                generateButton.enabled = false

                SharedScopes.IO.launch {
                    try {
                        status = "§aAdding to list and logging in..."
                        
                        // Add to alts list
                        val newAlt = "${usernameField.text}:${emailField.text}:${passwordField.text}"
                        val currentAlts = try {
                            java.net.URL(ALTS_URL).readText().lines().toMutableList()
                        } catch (e: Exception) {
                            mutableListOf()
                        }
                        
                        if (!currentAlts.contains(newAlt)) {
                            currentAlts.add(newAlt)
                        }
                        
                        // Login as cracked
                        mc.session = Session(
                            usernameField.text,
                            "",
                            "",
                            "legacy"
                        )
                        call(SessionUpdateEvent)

                        prevGui.status = "§aYour name is now §b§l${usernameField.text}§c."
                        mc.displayGuiScreen(prevGui)
                        status = "§aLogin successful!"
                        
                    } catch (throwable: Throwable) {
                        LOGGER.error("Failed to login.", throwable)
                        status = "§cFailed to login. Unknown error."
                    }

                    loginButton.enabled = true
                    generateButton.enabled = true
                }
            }
        }
    }

    /**
     * Handle key typed
     */
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        when (keyCode) {
            // Check if user want to escape from screen
            Keyboard.KEY_ESCAPE -> {
                // Send back to prev screen
                mc.displayGuiScreen(prevGui)
                return
            }

            Keyboard.KEY_TAB -> {
                TabUtils.tab(usernameField, emailField, passwordField)
                return
            }

            Keyboard.KEY_RETURN -> {
                actionPerformed(generateButton)
                return
            }
        }

        // Check if field is focused, then call key typed
        if (usernameField.isFocused) usernameField.textboxKeyTyped(typedChar, keyCode)
        if (emailField.isFocused) emailField.textboxKeyTyped(typedChar, keyCode)
        if (passwordField.isFocused) passwordField.textboxKeyTyped(typedChar, keyCode)
        super.keyTyped(typedChar, keyCode)
    }

    /**
     * Handle mouse clicked
     */
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        // Call mouse clicked to field
        usernameField.mouseClicked(mouseX, mouseY, mouseButton)
        emailField.mouseClicked(mouseX, mouseY, mouseButton)
        passwordField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    /**
     * Handle screen update
     */
    override fun updateScreen() {
        usernameField.updateCursorCounter()
        emailField.updateCursorCounter()
        passwordField.updateCursorCounter()
        super.updateScreen()
    }

    /**
     * Handle gui closed
     */
    override fun onGuiClosed() {
        // Disable keyboard repeat events
        Keyboard.enableRepeatEvents(false)
        super.onGuiClosed()
    }
}
