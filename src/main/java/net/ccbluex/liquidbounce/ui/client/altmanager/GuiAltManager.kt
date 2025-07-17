/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
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
import net.ccbluex.liquidbounce.ui.client.altmanager.menus.altgenerator.GuiHeoMCGen
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance.Companion.mc
import net.ccbluex.liquidbounce.utils.io.*
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.randomAccount
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.kotlin.swap
import net.ccbluex.liquidbounce.utils.login.UserUtils.isValidTokenOffline
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiSlot
import net.minecraft.client.gui.GuiTextField
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.Session
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.util.*
import kotlin.math.sin

class GuiAltManager(private val prevGui: GuiScreen) : AbstractScreen() {

    var status = "§7Idle..."
    private var animationTime = 0f

    private lateinit var loginButton: GuiButton
    private lateinit var randomAltButton: GuiButton
    private lateinit var randomNameButton: GuiButton
    private lateinit var addButton: GuiButton
    private lateinit var removeButton: GuiButton
    private lateinit var copyButton: GuiButton
    private lateinit var altsList: GuiList
    private lateinit var searchField: GuiTextField

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
        +GuiButton(15, 5, startPositionY + 24 * 8, 90, 20, "HeoMC Alts")
    }

    private fun drawGradientBackground() {
        animationTime += 0.02f
        
        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        
        GlStateManager.disableTexture2D()
        GlStateManager.enableBlend()
        GlStateManager.disableAlpha()
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0)
        GlStateManager.shadeModel(GL11.GL_SMOOTH)
        
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR)
        
        val time = animationTime
        val topR = (0.15f + 0.25f * sin(time * 0.5f)).coerceIn(0f, 1f)
        val topG = (0.35f + 0.35f * sin(time * 0.7f + 1f)).coerceIn(0f, 1f)
        val topB = (0.75f + 0.25f * sin(time * 0.3f + 2f)).coerceIn(0f, 1f)
        
        worldRenderer.pos(width.toDouble(), 0.0, zLevel.toDouble()).color(topR, topG, topB, 1.0f).endVertex()
        worldRenderer.pos(0.0, 0.0, zLevel.toDouble()).color(topR, topG, topB, 1.0f).endVertex()
        worldRenderer.pos(0.0, height.toDouble(), zLevel.toDouble()).color(0.9f, 0.95f, 1.0f, 1.0f).endVertex()
        worldRenderer.pos(width.toDouble(), height.toDouble(), zLevel.toDouble()).color(0.9f, 0.95f, 1.0f, 1.0f).endVertex()
        
        tessellator.draw()
        
        GlStateManager.shadeModel(GL11.GL_FLAT)
        GlStateManager.disableBlend()
        GlStateManager.enableAlpha()
        GlStateManager.enableTexture2D()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile {
            drawGradientBackground()
            altsList.drawScreen(mouseX, mouseY, partialTicks)
            
            // Animated title
            val titleTime = animationTime * 1.5f
            val titleR = (0.2f + 0.4f * sin(titleTime)).coerceIn(0f, 1f)
            val titleG = (0.4f + 0.4f * sin(titleTime + 1f)).coerceIn(0f, 1f)
            val titleB = (0.8f + 0.2f * sin(titleTime + 2f)).coerceIn(0f, 1f)
            val titleColor = ((titleR * 255).toInt() shl 16) or ((titleG * 255).toInt() shl 8) or (titleB * 255).toInt()
            
            Fonts.fontSemibold40.drawCenteredString(translationMenu("altManager"), width / 2f, 6f, titleColor)
            
            // Glowing text
            val textGlow = (0.7f + 0.3f * sin(animationTime * 2.5f)).coerceIn(0f, 1f)
            val textColor = (0xFFFFFF and 0x00FFFFFF) or ((255 * textGlow).toInt() shl 24)
            
            Fonts.fontSemibold35.drawCenteredString(
                if (searchField.text.isEmpty()) "${accountsConfig.accounts.size} Alts" else altsList.accounts.size.toString() + " Search Results",
                width / 2f, 18f, textColor
            )
            Fonts.fontSemibold35.drawCenteredString(status, width / 2f, 32f, textColor)
            Fonts.fontSemibold35.drawStringWithShadow(
                "§7User: §a${mc.getSession().username}", 6f, 6f, textColor
            )
            Fonts.fontSemibold35.drawStringWithShadow(
                "§7Type: §a${
                    if (altService.currentService == AltService.EnumAltService.THEALTENING) "TheAltening" 
                    else if (isValidTokenOffline(mc.getSession().token)) "Premium" 
                    else "Cracked"
                }", 6f, 15f, textColor
            )
            
            searchField.drawTextBox()
            if (searchField.text.isEmpty() && !searchField.isFocused) {
                val placeholderGlow = (0.5f + 0.3f * sin(animationTime * 1.5f)).coerceIn(0f, 1f)
                val placeholderColor = (0xFFFFFF and 0x00FFFFFF) or ((255 * placeholderGlow).toInt() shl 24)
                Fonts.fontSemibold40.drawStringWithShadow(
                    translationText("Search"), searchField.xPosition + 4f, 17f, placeholderColor
                )
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    public override fun actionPerformed(button: GuiButton) {
        if (!button.enabled) return

        when (button.id) {
            0 -> mc.displayGuiScreen(prevGui)
            1 -> mc.displayGuiScreen(GuiLoginIntoAccount(this))
            2 -> {
                status = if (altsList.selectedSlot != -1 && altsList.selectedSlot < altsList.size) {
                    accountsConfig.removeAccount(altsList.accounts[altsList.selectedSlot])
                    saveConfig(accountsConfig)
                    "§aThe account has been removed."
                } else {
                    "§cSelect an account."
                }
            }
            3 -> {
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
            4 -> {
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
            5 -> {
                status = "§aLogged into §f§l${randomAccount().name}§a."
                altService.switchService(AltService.EnumAltService.MOJANG)
            }
            6 -> mc.displayGuiScreen(GuiLoginIntoAccount(this, directLogin = true))
            7 -> {
                val file = MiscUtils.openFileChooser(FileFilters.TEXT) ?: return
                file.forEachLine {
                    val accountData = it.split(':', limit = 2)
                    if (accountData.size > 1) {
                        accountsConfig.addMojangAccount(accountData[0], accountData[1])
                    } else if (accountData[0].length < 16) {
                        accountsConfig.addCrackedAccount(accountData[0])
                    }
                }
                saveConfig(accountsConfig)
                status = "§aThe accounts were imported successfully."
            }
            8 -> {
                val currentAccount = altsList.selectedAccount
                if (currentAccount == null) {
                    status = "§cSelect an account."
                    return
                }
                try {
                    val formattedData = when (currentAccount) {
                        is MojangAccount -> "${currentAccount.email}:${currentAccount.password}"
                        is MicrosoftAccount -> "${currentAccount.name}:${currentAccount.session.token}"
                        else -> currentAccount.name
                    }
                    MiscUtils.copy(formattedData)
                    status = "§aCopied account into your clipboard."
                } catch (any: Exception) {
                    any.printStackTrace()
                }
            }
            9 -> mc.displayGuiScreen(GuiTheAltening(this))
            10 -> mc.displayGuiScreen(GuiSessionLogin(this))
            11 -> mc.displayGuiScreen(GuiDonatorCape(this))
            12 -> {
                if (accountsConfig.accounts.isEmpty()) {
                    status = "§cYou do not have any accounts to export."
                    return
                }
                val file = MiscUtils.saveFileChooser()
                if (file == null || file.isDirectory) return
                try {
                    if (!file.exists()) file.createNewFile()
                    val accounts = accountsConfig.accounts.joinToString(separator = "\n") { account ->
                        when (account) {
                            is MojangAccount -> "${account.email}:${account.password}"
                            is MicrosoftAccount -> "${account.name}:${account.session.token}"
                            else -> account.name
                        }
                    }
                    file.writeText(accounts)
                    status = "§aExported successfully!"
                } catch (e: Exception) {
                    status = "§cUnable to export due to error: ${e.message}"
                }
            }
            15 -> mc.displayGuiScreen(GuiHeoMCGen(this))
            13, 14 -> {
                val currentAccount = altsList.selectedAccount ?: run {
                    status = "§cSelect an account."
                    return
                }
                val currentIndex = altsList.accounts.indexOf(currentAccount)
                val targetIndex = if (button.id == 13) currentIndex - 1 else currentIndex + 1
                if (targetIndex < 0 || targetIndex >= altsList.accounts.size) return
                
                val targetElement = altsList.accounts[targetIndex]
                val targetOriginalIndex = accountsConfig.accounts.indexOf(targetElement)
                val currentOriginalIndex = accountsConfig.accounts.indexOf(currentAccount)
                
                accountsConfig.accounts.swap(targetOriginalIndex, currentOriginalIndex)
                accountsConfig.saveConfig()
                altsList.selectedSlot += if (button.id == 13) -1 else 1
            }
        }
    }

    public override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (searchField.isFocused) {
            searchField.textboxKeyTyped(typedChar, keyCode)
        }

        when (keyCode) {
            Keyboard.KEY_ESCAPE -> mc.displayGuiScreen(prevGui)
            Keyboard.KEY_UP -> altsList.selectedSlot -= 1
            Keyboard.KEY_DOWN -> altsList.selectedSlot += 1
            Keyboard.KEY_TAB -> altsList.selectedSlot += if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) -1 else 1
            Keyboard.KEY_RETURN -> altsList.elementClicked(altsList.selectedSlot, true, 0, 0)
            Keyboard.KEY_NEXT -> altsList.scrollBy(height - 100)
            Keyboard.KEY_PRIOR -> altsList.scrollBy(-height + 100)
            Keyboard.KEY_ADD -> actionPerformed(addButton)
            Keyboard.KEY_DELETE, Keyboard.KEY_MINUS -> actionPerformed(removeButton)
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
                    it.name.contains(search, ignoreCase = true) || 
                    (it is MojangAccount && it.email.contains(search, ignoreCase = true))
                }
            }

        var selectedSlot = 0
            set(value) {
                if (accounts.isEmpty()) return
                field = (value + accounts.size) % accounts.size
            }
            get() {
                return if (field >= accounts.size) -1 else field
            }

        val selectedAccount get() = accounts.getOrNull(selectedSlot)

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

            // Animated account colors
            val accountGlow = (0.7f + 0.3f * sin(animationTime * 2f + id * 0.2f)).coerceIn(0f, 1f)
            val accountColor = (Color.WHITE.rgb and 0x00FFFFFF) or ((255 * accountGlow).toInt() shl 24)

            Fonts.fontSemibold40.drawCenteredString(accountName, width / 2f, y + 2f, accountColor, true)
            
            val typeColor = if (minecraftAccount is CrackedAccount) {
                (Color.GRAY.rgb and 0x00FFFFFF) or ((255 * accountGlow).toInt() shl 24)
            } else {
                (Color(118, 255, 95).rgb and 0x00FFFFFF) or ((255 * accountGlow).toInt() shl 24)
            }
            
            Fonts.fontSemibold40.drawCenteredString(
                when (minecraftAccount) {
                    is CrackedAccount -> "Cracked"
                    is MicrosoftAccount -> "Microsoft"
                    is MojangAccount -> "Mojang"
                    else -> "Something else"
                },
                width / 2f, y + 15f, typeColor, true
            )
        }

        override fun drawBackground() {}
    }

    companion object {
        val altService = AltService()
        private val activeGenerators = mutableMapOf<String, Boolean>()

        fun loadActiveGenerators() {
            try {
                activeGenerators += HttpClient.get("$CLIENT_CLOUD/generators.json").jsonBody<Map<String, Boolean>>()!!
            } catch (throwable: Throwable) {
                LOGGER.error("Failed to load enabled generators.", throwable)
            }
        }

        fun login(
            minecraftAccount: MinecraftAccount, success: () -> Unit, error: (Exception) -> Unit, done: () -> Unit
        ) = SharedScopes.IO.launch {
            if (altService.currentService != AltService.EnumAltService.MOJANG) {
                try {
                    altService.switchService(AltService.EnumAltService.MOJANG)
                } catch (e: Exception) {
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
