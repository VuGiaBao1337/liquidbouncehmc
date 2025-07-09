/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.altmanager

import com.thealtening.AltService
import kotlinx.coroutines.launch
import me.liuli.elixir.account.CrackedAccount
import me.liuli.elixir.account.MicrosoftAccount
import me.liuli.elixir.account.MinecraftAccount
import me.liuli.elixir.account.MojangAccount
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_CLOUD
import net.ccbluex.liquidbounce.event.EventManager.call
import net.ccbluex.liquidbounce.event.SessionUpdateEvent
import net.ccbluex.liquidbounce.file.FileManager.accountsConfig
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.lang.translationButton
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.lang.translationText
import net.ccbluex.liquidbounce.ui.client.altmanager.menus.GuiDonatorCape
import net.ccbluex.liquidbounce.ui.client.altmanager.menus.GuiLoginIntoAccount
import net.ccbluex.liquidbounce.ui.client.altmanager.menus.GuiSessionLogin
import net.ccbluex.liquidbounce.ui.client.altmanager.menus.altgenerator.GuiTheAltening
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance.Companion.mc
import net.ccbluex.liquidbounce.utils.io.*
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.randomAccount
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.kotlin.swap
import net.ccbluex.liquidbounce.utils.login.UserUtils.isValidTokenOffline
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorderRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiSlot
import net.minecraft.client.gui.GuiTextField
import net.minecraft.util.Session
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.util.*
import kotlin.math.*

class GuiAltManager(private val prevGui: GuiScreen) : AbstractScreen() {

    var status = "§7Idle..."
    private lateinit var loginButton: GuiButton
    private lateinit var randomAltButton: GuiButton
    private lateinit var randomNameButton: GuiButton
    private lateinit var addButton: GuiButton
    private lateinit var removeButton: GuiButton
    private lateinit var copyButton: GuiButton
    private lateinit var altsList: GuiList
    private lateinit var searchField: GuiTextField
    
    // Morphing gradient animation system
    private var animationTime = 0f
    private var morphingSpeed = 0.008f
    
    private val gradientSets = arrayOf(
    // Set 1: Deep Blue to Sky Blue
    arrayOf(Color(25, 55, 109), Color(30, 80, 150), Color(40, 120, 200), Color(60, 150, 240)),
    // Set 2: Sky Blue to Light Blue  
    arrayOf(Color(60, 150, 240), Color(80, 170, 255), Color(100, 180, 255), Color(120, 200, 255)),
    // Set 3: Royal Blue variations
    arrayOf(Color(15, 82, 186), Color(25, 100, 200), Color(40, 120, 220), Color(60, 140, 240)),
    // Set 4: Ocean Blue cycle
    arrayOf(Color(10, 70, 160), Color(20, 90, 180), Color(35, 110, 200), Color(25, 55, 109))
)
    
    private var currentGradientIndex = 0
    private var nextGradientIndex = 1
    private var morphProgress = 0f
    private val meshResolution = 20
    private val gradientMesh = Array(meshResolution + 1) { Array(meshResolution + 1) { Color.WHITE } }

    override fun initGui() {
        val textFieldWidth = (width / 8).coerceAtLeast(70)
        searchField = GuiTextField(2, Fonts.fontSemibold40, width - textFieldWidth - 10, 10, textFieldWidth, 20)
        searchField.maxStringLength = Int.MAX_VALUE

        altsList = GuiList(this).apply {
            registerScrollButtons(7, 8)
            val mightBeTheCurrentAccount = accountsConfig.accounts.indexOfFirst { it.name == mc.session.username }
            elementClicked(mightBeTheCurrentAccount, false, 0, 0)
            scrollBy(mightBeTheCurrentAccount * this.getSlotHeight())
        }

        // Setup buttons
        val startPositionY = 22
        addButton = +GuiButton(1, width - 80, startPositionY + 24, 70, 20, translationButton("add"))
        removeButton = +GuiButton(2, width - 80, startPositionY + 24 * 2, 70, 20, translationButton("remove"))
        +GuiButton(13, width - 80, startPositionY + 24 * 3, 70, 20, translationButton("moveUp"))
        +GuiButton(14, width - 80, startPositionY + 24 * 4, 70, 20, translationButton("moveDown"))
        +GuiButton(7, width - 80, startPositionY + 24 * 5, 70, 20, translationButton("import"))
        +GuiButton(12, width - 80, startPositionY + 24 * 6, 70, 20, translationButton("export"))
        copyButton = +GuiButton(8, width - 80, startPositionY + 24 * 7, 70, 20, translationButton("altManager.copy"))
        +GuiButton(0, width - 80, height - 65, 70, 20, translationButton("back"))

        loginButton = +GuiButton(3, 5, startPositionY + 24, 90, 20, translationButton("altManager.login"))
        randomAltButton = +GuiButton(4, 5, startPositionY + 24 * 2, 90, 20, translationButton("altManager.randomAlt"))
        randomNameButton = +GuiButton(5, 5, startPositionY + 24 * 3, 90, 20, translationButton("altManager.randomName"))
        +GuiButton(6, 5, startPositionY + 24 * 4, 90, 20, translationButton("altManager.directLogin"))
        +GuiButton(10, 5, startPositionY + 24 * 5, 90, 20, translationButton("altManager.sessionLogin"))

        if (activeGenerators.getOrDefault("thealtening", true)) {
            +GuiButton(9, 5, startPositionY + 24 * 6, 90, 20, translationButton("altManager.theAltening"))
        }

        +GuiButton(11, 5, startPositionY + 24 * 7, 90, 20, translationButton("altManager.cape"))
    }

    private fun updateMorphingGradient() {
        animationTime += morphingSpeed
        morphProgress += 0.01f
        
        if (morphProgress >= 1f) {
            morphProgress = 0f
            currentGradientIndex = nextGradientIndex
            nextGradientIndex = (nextGradientIndex + 1) % gradientSets.size
        }
        
        updateGradientMesh()
    }

    private fun updateGradientMesh() {
        val currentSet = gradientSets[currentGradientIndex]
        val nextSet = gradientSets[nextGradientIndex]
        
        for (x in 0..meshResolution) {
            for (y in 0..meshResolution) {
                val xRatio = x.toFloat() / meshResolution
                val yRatio = y.toFloat() / meshResolution
                
                val noiseX = sin(animationTime * 0.5f + xRatio * 4f + yRatio * 2f) * 0.1f
                val noiseY = cos(animationTime * 0.7f + xRatio * 3f + yRatio * 4f) * 0.1f
                
                val adjustedX = (xRatio + noiseX).coerceIn(0f, 1f)
                val adjustedY = (yRatio + noiseY).coerceIn(0f, 1f)
                
                val currentColor = interpolateGradientColors(currentSet, adjustedX, adjustedY)
                val nextColor = interpolateGradientColors(nextSet, adjustedX, adjustedY)
                
                gradientMesh[x][y] = morphColors(currentColor, nextColor, morphProgress)
            }
        }
    }

    private fun interpolateGradientColors(colorSet: Array<Color>, x: Float, y: Float): Color {
        val topLeft = colorSet[0]
        val topRight = colorSet[1]
        val bottomLeft = colorSet[2]
        val bottomRight = colorSet[3]
        
        val topColor = interpolateColors(topLeft, topRight, x)
        val bottomColor = interpolateColors(bottomLeft, bottomRight, x)
        return interpolateColors(topColor, bottomColor, y)
    }

    private fun interpolateColors(color1: Color, color2: Color, ratio: Float): Color {
        val r = (color1.red + (color2.red - color1.red) * ratio).toInt().coerceIn(0, 255)
        val g = (color1.green + (color2.green - color1.green) * ratio).toInt().coerceIn(0, 255)
        val b = (color1.blue + (color2.blue - color1.blue) * ratio).toInt().coerceIn(0, 255)
        return Color(r, g, b)
    }

    private fun morphColors(color1: Color, color2: Color, progress: Float): Color {
        val easedProgress = easeInOutCubic(progress)
        return interpolateColors(color1, color2, easedProgress)
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4f * t * t * t
        } else {
            1f - (-2f * t + 2f).pow(3) / 2f
        }
    }

    private fun drawMorphingGradientBackground() {
        glDisable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glShadeModel(GL_SMOOTH)
        
        updateMorphingGradient()
        drawGradientMesh()
        
        glShadeModel(GL_FLAT)
        glEnable(GL_TEXTURE_2D)
    }

    private fun drawGradientMesh() {
        val stepX = width.toFloat() / meshResolution
        val stepY = height.toFloat() / meshResolution
        
        for (x in 0 until meshResolution) {
            for (y in 0 until meshResolution) {
                val x1 = x * stepX
                val y1 = y * stepY
                val x2 = (x + 1) * stepX
                val y2 = (y + 1) * stepY
                
                val color1 = gradientMesh[x][y]
                val color2 = gradientMesh[x + 1][y]
                val color3 = gradientMesh[x + 1][y + 1]
                val color4 = gradientMesh[x][y + 1]
                
                glBegin(GL_QUADS)
                
                glColor3f(color1.red / 255f, color1.green / 255f, color1.blue / 255f)
                glVertex2f(x1, y1)
                
                glColor3f(color2.red / 255f, color2.green / 255f, color2.blue / 255f)
                glVertex2f(x2, y1)
                
                glColor3f(color3.red / 255f, color3.green / 255f, color3.blue / 255f)
                glVertex2f(x2, y2)
                
                glColor3f(color4.red / 255f, color4.green / 255f, color4.blue / 255f)
                glVertex2f(x1, y2)
                
                glEnd()
            }
        }
    }



    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile {
            // Draw morphing gradient background
            drawMorphingGradientBackground()
            

            altsList.drawScreen(mouseX, mouseY, partialTicks)

            Fonts.fontSemibold40.drawCenteredString(translationMenu("altManager"), width / 2f, 6f, 0xffffff)
            Fonts.fontSemibold35.drawCenteredString(
                if (searchField.text.isEmpty()) "${accountsConfig.accounts.size} Alts" else altsList.accounts.size.toString() + " Search Results",
                width / 2f,
                18f,
                0xffffff
            )
            Fonts.fontSemibold35.drawCenteredString(status, width / 2f, 32f, 0xffffff)

            Fonts.fontSemibold35.drawStringWithShadow(
                "§7User: §a${mc.getSession().username}", 6f, 6f, 0xffffff
            )

            Fonts.fontSemibold35.drawStringWithShadow(
                "§7Type: §a${
                    if (altService.currentService == AltService.EnumAltService.THEALTENING) "TheAltening" else if (isValidTokenOffline(
                            mc.getSession().token
                        )
                    ) "Premium" else "Cracked"
                }", 6f, 15f, 0xffffff
            )

            searchField.drawTextBox()
            if (searchField.text.isEmpty() && !searchField.isFocused) Fonts.fontSemibold40.drawStringWithShadow(
                translationText("Search"), searchField.xPosition + 4f, 17f, 0xffffff
            )
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    public override fun actionPerformed(button: GuiButton) {
        // Not enabled buttons should be ignored
        if (!button.enabled) return

        when (button.id) {
            0 -> mc.displayGuiScreen(prevGui)
            1 -> mc.displayGuiScreen(GuiLoginIntoAccount(this))
            2 -> { // Delete button
                status = if (altsList.selectedSlot != -1 && altsList.selectedSlot < altsList.size) {
                    accountsConfig.removeAccount(altsList.accounts[altsList.selectedSlot])
                    saveConfig(accountsConfig)
                    "§aThe account has been removed."
                } else {
                    "§cSelect an account."
                }
            }

            3 -> { // Login button
                status = altsList.selectedAccount?.let {
                    loginButton.enabled = false
                    randomAltButton.enabled = false
                    randomNameButton.enabled = false
                    login(it, {
                        status = "§aLogged into §f§l${mc.session.username}§a."
                    }, { exception ->
                        status = "§cLogin failed due to '${exception.message}'."
                    }, {
                        loginButton.enabled = true
                        randomAltButton.enabled = true
                        randomNameButton.enabled = true
                    })
                    "§aLogging in..."
                } ?: "§cSelect an account."
            }

            4 -> { // Random alt button
                status = altsList.accounts.randomOrNull()?.let {
                    loginButton.enabled = false
                    randomAltButton.enabled = false
                    randomNameButton.enabled = false
                    login(it, {
                        status = "§aLogged into §f§l${mc.session.username}§a."
                    }, { exception ->
                        status = "§cLogin failed due to '${exception.message}'."
                    }, {
                        loginButton.enabled = true
                        randomAltButton.enabled = true
                        randomNameButton.enabled = true
                    })
                    "§aLogging in..."
                } ?: "§cYou do not have any accounts."
            }

            5 -> { // Random name button
                status = "§aLogged into §f§l${randomAccount().name}§a."
                altService.switchService(AltService.EnumAltService.MOJANG)
            }

            6 -> { // Direct login button
                mc.displayGuiScreen(GuiLoginIntoAccount(this, directLogin = true))
            }

            7 -> { // Import button
                val file = MiscUtils.openFileChooser(FileFilters.TEXT) ?: return

                file.forEachLine {
                    val accountData = it.split(':', limit = 2)
                    if (accountData.size > 1) {
                        // Most likely a mojang account
                        accountsConfig.addMojangAccount(accountData[0], accountData[1])
                    } else if (accountData[0].length < 16) {
                        // Might be cracked account
                        accountsConfig.addCrackedAccount(accountData[0])
                    } // skip account
                }

                saveConfig(accountsConfig)
                status = "§aThe accounts were imported successfully."
            }

            12 -> { // Export button
                if (accountsConfig.accounts.isEmpty()) {
                    status = "§cYou do not have any accounts to export."
                    return
                }

                val file = MiscUtils.saveFileChooser()
                if (file == null || file.isDirectory) {
                    return
                }

                try {
                    if (!file.exists()) {
                        file.createNewFile()
                    }

                    val accounts = accountsConfig.accounts.joinToString(separator = "\n") { account ->
                        when (account) {
                            is MojangAccount -> "${account.email}:${account.password}" // EMAIL:PASSWORD
                            is MicrosoftAccount -> "${account.name}:${account.session.token}" // NAME:SESSION
                            else -> account.name
                        }
                    }

                    file.writeText(accounts)
                    status = "§aExported successfully!"
                } catch (e: Exception) {
                    status = "§cUnable to export due to error: ${e.message}"
                }
            }

            8 -> {
                val currentAccount = altsList.selectedAccount
                if (currentAccount == null) {
                    status = "§cSelect an account."
                    return
                }

                try {
                    // Format data for other tools
                    val formattedData = when (currentAccount) {
                        is MojangAccount -> "${currentAccount.email}:${currentAccount.password}" // EMAIL:PASSWORD
                        is MicrosoftAccount -> "${currentAccount.name}:${currentAccount.session.token}" // NAME:SESSION
                        else -> currentAccount.name
                    }

                    // Copy to clipboard
                    MiscUtils.copy(formattedData)
                    status = "§aCopied account into your clipboard."
                } catch (any: Exception) {
                    any.printStackTrace()
                }
            }

            9 -> { // Altening Button
                mc.displayGuiScreen(GuiTheAltening(this))
            }

            10 -> { // Session Login Button
                mc.displayGuiScreen(GuiSessionLogin(this))
            }

            11 -> { // Donator Cape Button
                mc.displayGuiScreen(GuiDonatorCape(this))
            }

            13 -> { // Move Up Button
                val currentAccount = altsList.selectedAccount
                if (currentAccount == null) {
                    status = "§cSelect an account."
                    return
                }

                val currentIndex = altsList.accounts.indexOf(currentAccount)
                if (currentIndex == 0) {
                    return
                }

                val prevElement = altsList.accounts[currentIndex - 1]
                val prevIndex = accountsConfig.accounts.indexOf(prevElement)
                val currentOriginalIndex = accountsConfig.accounts.indexOf(currentAccount)

                // Move currentAccount
                accountsConfig.accounts.swap(prevIndex, currentOriginalIndex)
                accountsConfig.saveConfig()
                altsList.selectedSlot--
            }

            14 -> { // Move Down Button
                val currentAccount = altsList.selectedAccount
                if (currentAccount == null) {
                    status = "§cSelect an account."
                    return
                }

                val currentIndex = altsList.accounts.indexOf(currentAccount)
                if (currentIndex == altsList.accounts.lastIndex) {
                    return
                }

                val nextElement = altsList.accounts[currentIndex + 1]
                val nextIndex = accountsConfig.accounts.indexOf(nextElement)
                val currentOriginalIndex = accountsConfig.accounts.indexOf(currentAccount)

                // Move currentAccount
                accountsConfig.accounts.swap(nextIndex, currentOriginalIndex)
                accountsConfig.saveConfig()
                altsList.selectedSlot++
            }
        }
    }

    public override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (searchField.isFocused) {
            searchField.textboxKeyTyped(typedChar, keyCode)
        }

        when (keyCode) {
            // Go back
            Keyboard.KEY_ESCAPE -> mc.displayGuiScreen(prevGui)
            // Go one up in account list
            Keyboard.KEY_UP -> altsList.selectedSlot -= 1
            // Go one down in account list
            Keyboard.KEY_DOWN -> altsList.selectedSlot += 1
            // Go up or down in account list
            Keyboard.KEY_TAB -> altsList.selectedSlot += if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) -1 else 1
            // Login into account
            Keyboard.KEY_RETURN -> altsList.elementClicked(altsList.selectedSlot, true, 0, 0)
            // Scroll account list
            Keyboard.KEY_NEXT -> altsList.scrollBy(height - 100)
            // Scroll account list
            Keyboard.KEY_PRIOR -> altsList.scrollBy(-height + 100)
            // Add account
            Keyboard.KEY_ADD -> actionPerformed(addButton)
            // Remove account
            Keyboard.KEY_DELETE, Keyboard.KEY_MINUS -> actionPerformed(removeButton)
            // Copy when CTRL+C gets pressed
            Keyboard.KEY_C -> {
                if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) actionPerformed(copyButton)
                else super.keyTyped(typedChar, keyCode)
            }

            else -> super.keyTyped(typedChar, keyCode)
        }
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        altsList.handleMouseInput()
    }

    public override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        searchField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun updateScreen() = searchField.updateCursorCounter()

    private inner class GuiList(prevGui: GuiScreen) :
        GuiSlot(mc, prevGui.width, prevGui.height, 40, prevGui.height - 40, 30) {

        val accounts: List<MinecraftAccount>
            get() {
                var search = searchField.text
                if (search == null || search.isEmpty()) {
                    return accountsConfig.accounts
                }

                search = search.lowercase(Locale.getDefault())
                return accountsConfig.accounts.filter {
                    it.name.contains(
                        search, ignoreCase = true
                    ) || (it is MojangAccount && it.email.contains(search, ignoreCase = true))
                }
            }

        var selectedSlot = 0
            set(value) {
                if (accounts.isEmpty()) return
                field = (value + accounts.size) % accounts.size
            }
            get() {
                return if (field >= accounts.size) -1
                else field
            }

        val selectedAccount
            get() = accounts.getOrNull(selectedSlot)

        override fun isSelected(id: Int) = selectedSlot == id

        public override fun getSize() = accounts.size

        public override fun elementClicked(clickedElement: Int, doubleClick: Boolean, var3: Int, var4: Int) {
            selectedSlot = clickedElement

            if (doubleClick) {
                status = altsList.selectedAccount?.let {
                    loginButton.enabled = false
                    randomAltButton.enabled = false
                    randomNameButton.enabled = false
                    login(it, {
                        status = "§aLogged into §f§l${mc.session.username}§a."
                    }, { exception ->
                        status = "§cLogin failed due to '${exception.message}'."
                    }, {
                        loginButton.enabled = true
                        randomAltButton.enabled = true
                        randomNameButton.enabled = true
                    })
                    "§aLogging in..."
                } ?: "§cSelect an account."
            }
        }

        override fun drawSlot(id: Int, x: Int, y: Int, var4: Int, var5: Int, var6: Int) {
            val minecraftAccount = accounts[id]

            val accountName = if (minecraftAccount is MojangAccount && minecraftAccount.name.isEmpty()) {
                minecraftAccount.email
            } else {
                minecraftAccount.name
            }

            Fonts.fontSemibold40.drawCenteredString(accountName, width / 2f, y + 2f, Color.WHITE.rgb, true)
            Fonts.fontSemibold40.drawCenteredString(
                if (minecraftAccount is CrackedAccount) "Cracked" else if (minecraftAccount is MicrosoftAccount) "Microsoft" else if (minecraftAccount is MojangAccount) "Mojang" else "Something else",
                width / 2f,
                y + 15f,
                if (minecraftAccount is CrackedAccount) Color.GRAY.rgb else Color(118, 255, 95).rgb,
                true
            )
        }

        override fun drawBackground() {}
    }

    companion object {
        val altService = AltService()
        private val activeGenerators = mutableMapOf<String, Boolean>()

        fun loadActiveGenerators() {
            try {
                // Read versions json from cloud
                activeGenerators += HttpClient.get("$CLIENT_CLOUD/generators.json").jsonBody<Map<String, Boolean>>()!!
            } catch (throwable: Throwable) {
                // Print throwable to console
                LOGGER.error("Failed to load enabled generators.", throwable)
            }
        }

        fun login(
            minecraftAccount: MinecraftAccount, success: () -> Unit, error: (Exception) -> Unit, done: () -> Unit
        ) = SharedScopes.IO.launch {
            if (altService.currentService != AltService.EnumAltService.MOJANG) {
                try {
                    altService.switchService(AltService.EnumAltService.MOJANG)
                } catch (e: NoSuchFieldException) {
                    error(e)
                    LOGGER.error("Something went wrong while trying to switch alt service.", e)
                } catch (e: IllegalAccessException) {
                    error(e)
                    LOGGER.error("Something went wrong while trying to switch alt service.", e)
                }
            }

            try {
                minecraftAccount.update()
                mc.session = Session(
                    minecraftAccount.session.username,
                    minecraftAccount.session.uuid,
                    minecraftAccount.session.token,
                    "microsoft"
                )
                call(SessionUpdateEvent)
                success()
            } catch (exception: Exception) {
                error(exception)
            }
            done()
        }
    }
}
