/*
 * DeletedUser has been commented out for easier coding
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.LiquidBounce.clientVersionText
import net.ccbluex.liquidbounce.api.ClientUpdate
import net.ccbluex.liquidbounce.api.ClientUpdate.hasUpdate
import net.ccbluex.liquidbounce.file.FileManager
import net.ccbluex.liquidbounce.file.FileManager.valuesConfig
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.ui.client.altmanager.GuiAltManager
import net.ccbluex.liquidbounce.ui.client.fontmanager.GuiFontManager
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.JavaVersion
import net.ccbluex.liquidbounce.utils.client.javaVersion
import net.ccbluex.liquidbounce.utils.io.MiscUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiOptions
import net.minecraft.client.gui.GuiSelectWorld
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.resources.I18n
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.sin

class GuiMainMenu : AbstractScreen() {

    private var popup: PopupScreen? = null
    private var animationTime = 0f

    companion object {
        private var popupOnce = false
        var lastWarningTime: Long? = null
        private val warningInterval = TimeUnit.DAYS.toMillis(7)

        fun shouldShowWarning() = lastWarningTime == null || Instant.now().toEpochMilli() - lastWarningTime!! > warningInterval
    }

    override fun initGui() {
        // Left side menu buttons
        val buttonWidth = 120
        val buttonHeight = 20
        val leftMargin = 20
        val startY = height / 4

        +GuiButton(1, leftMargin, startY, buttonWidth, buttonHeight, I18n.format("menu.singleplayer"))
        +GuiButton(2, leftMargin, startY + 25, buttonWidth, buttonHeight, I18n.format("menu.multiplayer"))
        +GuiButton(100, leftMargin, startY + 50, buttonWidth, buttonHeight, translationMenu("altManager"))
        +GuiButton(0, leftMargin, startY + 75, buttonWidth, buttonHeight, I18n.format("menu.options"))
        +GuiButton(4, leftMargin, startY + 100, buttonWidth, buttonHeight, I18n.format("menu.quit"))
        
        // Additional buttons
        +GuiButton(103, leftMargin, startY + 125, buttonWidth, buttonHeight, translationMenu("mods"))
        +GuiButton(109, leftMargin, startY + 150, buttonWidth, buttonHeight, translationMenu("fontManager"))
        +GuiButton(102, leftMargin, startY + 175, buttonWidth, buttonHeight, translationMenu("configuration"))
        +GuiButton(101, leftMargin, startY + 200, buttonWidth, buttonHeight, translationMenu("serverStatus"))
        +GuiButton(108, leftMargin, startY + 225, buttonWidth, buttonHeight, translationMenu("contributors"))
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
        // Blue to white gradient
        val topR = (0.2f + 0.1f * sin(time * 0.3f)).coerceIn(0f, 1f)
        val topG = (0.5f + 0.2f * sin(time * 0.4f + 1f)).coerceIn(0f, 1f)
        val topB = (0.8f + 0.2f * sin(time * 0.2f + 2f)).coerceIn(0f, 1f)
        
        worldRenderer.pos(width.toDouble(), 0.0, zLevel.toDouble()).color(topR, topG, topB, 1.0f).endVertex()
        worldRenderer.pos(0.0, 0.0, zLevel.toDouble()).color(topR, topG, topB, 1.0f).endVertex()
        worldRenderer.pos(0.0, height.toDouble(), zLevel.toDouble()).color(0.95f, 0.98f, 1.0f, 1.0f).endVertex()
        worldRenderer.pos(width.toDouble(), height.toDouble(), zLevel.toDouble()).color(0.95f, 0.98f, 1.0f, 1.0f).endVertex()
        
        tessellator.draw()
        
        GlStateManager.shadeModel(GL11.GL_FLAT)
        GlStateManager.disableBlend()
        GlStateManager.enableAlpha()
        GlStateManager.enableTexture2D()
    }

    private fun drawMenuBackground() {
        // Draw menu background full height
        val menuWidth = 180 // Keep same width
        
        // Lighter semi-transparent background
        val bgColor = 0x40000000.toInt() // More transparent black
        
        drawRect(
            0f, // Start from left edge
            0f, // Start from top of screen (full height)
            menuWidth.toFloat(), // Same width as before
            height.toFloat(), // Full height to bottom of screen
            bgColor
        )
    }

    private fun showWelcomePopup() {
        popup = PopupScreen {
            title("§a§lWelcome!")
            message("""
                §eThank you for downloading and installing §bRinBounce§e!
        
                §6Here is some information you might find useful:§r
                §a- §fClickGUI:§r Press §7[RightShift]§f to open ClickGUI.
                §a- §fRight-click modules with a '+' to edit.
                §a- §fHover over a module to see its description.
        
                §6Important Commands:§r
                §a- §f.bind <module> <key> / .bind <module> none
                §a- §f.config load <name> / .config list
        
                §bNeed help? Contact us!§r
                - §fYouTube: §9https://youtube.com/ccbluex
                - §fTwitter: §9https://twitter.com/ccbluex
                - §fForum: §9https://forums.ccbluex.net/
            """.trimIndent())
            button("§aOK")
            onClose { popup = null }
        }
    }

    private fun showUpdatePopup() {
        val newestVersion = ClientUpdate.newestVersion ?: return

        val isReleaseBuild = newestVersion.release
        val updateType = if (isReleaseBuild) "version" else "development build"

        val dateFormatter = SimpleDateFormat("EEEE, MMMM dd, yyyy, h a z", Locale.ENGLISH)
        val newestVersionDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(newestVersion.date)
        val formattedNewestDate = dateFormatter.format(newestVersionDate)

        popup = PopupScreen {
            title("§bNew Update Available!")
            message("""
                §eA new $updateType of RinBounce is available!
        
                - ${if (isReleaseBuild) "§aVersion" else "§aBuild ID"}:§r ${if (isReleaseBuild) newestVersion.lbVersion else newestVersion.buildId}
                - §aMinecraft Version:§r ${newestVersion.mcVersion}
                - §aBranch:§r ${newestVersion.branch}
                - §aDate:§r $formattedNewestDate
        
                §6Changes:§r
                ${newestVersion.message}
        
                §bUpgrade now to enjoy the latest features and improvements!§r
            """.trimIndent())
            button("§aDownload") { MiscUtils.showURL(newestVersion.url) }
            onClose { popup = null }
        }
    }

    private fun showDiscontinuedWarning() {
        popup = PopupScreen {
            title("§c§lUnsupported version")
            message("""
                §6§lThis version is discontinued and unsupported.§r
                
                §eWe strongly recommend switching to §bRinBounce Nextgen§e, 
                which offers the following benefits:
                
                §a- §fSupports all Minecraft versions from §71.7§f to §71.21+§f.
                §a- §fFrequent updates with the latest bypasses and features.
                §a- §fActive development and official support.
                §a- §fImproved performance and compatibility.
                
                §cWhy upgrade?§r
                - No new bypasses or features will be introduced in this version.
                - Auto config support will not be actively maintained.
                - Unofficial forks of this version are discouraged as they lack the full feature set of Nextgen and cannot be trusted.
        
                §9Upgrade to RinBounce Nextgen today for a better experience!§r
            """.trimIndent())
            button("§aDownload Nextgen") { MiscUtils.showURL("https://liquidbounce.net/download") }
            button("§eInstallation Tutorial") { MiscUtils.showURL("https://www.youtube.com/watch?v=i_r1i4m-NZc") }
            onClose {
                popup = null
                lastWarningTime = Instant.now().toEpochMilli()
                FileManager.saveConfig(valuesConfig)
            }
        }
    }

    private fun showOutdatedJava8Warning() {
        popup = PopupScreen {
            title("§c§lOutdated Java Runtime Environment")
            message("""
                §6§lYou are using an outdated version of Java 8 (${javaVersion!!.raw}).§r
                
                §fThis might cause unexpected §c§lBUGS§f.
                Please update it to 8u101+, or get a new one from the Internet.
            """.trimIndent())
            button("§aDownload Java") { MiscUtils.showURL(JavaVersion.DOWNLOAD_PAGE) }
            button("§eI realized")
            onClose { popup = null }
        }
    }

    private fun showJava11Warning() {
        popup = PopupScreen {
            title("§c§lInappropriate Java Runtime Environment")
            message("""
                §6§lThis version of RinBounce is designed for Java 8 environment.§r
                
                §fHigher versions of Java might cause bug or crash.
                You can get JRE 8 from the Internet.
            """.trimIndent())
            button("§aDownload Java") { MiscUtils.showURL(JavaVersion.DOWNLOAD_PAGE) }
            button("§eI realized")
            onClose { popup = null }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawGradientBackground()
        
        // Draw menu background first (behind buttons)
        drawMenuBackground()

        // b1.0.1 in top left corner (white color)
        mc.fontRendererObj.drawStringWithShadow(
            "b1.1.7",
            10f,
            10f,
            0xFFFFFF // White color
        )

        // Large "Rinbounce" title moved more to the right (BLUE color)
        val rinbounceTitle = "Rinbounce"
        val titleScale = 3.0f
        
        GlStateManager.pushMatrix()
        GlStateManager.scale(titleScale, titleScale, titleScale)
        
        // Move title more to the right (increased X offset)
        val scaledX = (width * 0.65f - mc.fontRendererObj.getStringWidth(rinbounceTitle) * titleScale / 2f) / titleScale
        val scaledY = (height / 2f - mc.fontRendererObj.FONT_HEIGHT * titleScale / 2f) / titleScale
        
        mc.fontRendererObj.drawStringWithShadow(rinbounceTitle, scaledX, scaledY, 0xFFFFFF) // White color
        GlStateManager.popMatrix()

        // "credit" text in bottom right corner (white color)
        val creditText = "credit; CCBlueX, RatterMC"
        mc.fontRendererObj.drawStringWithShadow(
            creditText,
            width - mc.fontRendererObj.getStringWidth(creditText) - 10f,
            height - mc.fontRendererObj.FONT_HEIGHT - 10f,
            0xFFFFFF // White color
        )

        super.drawScreen(mouseX, mouseY, partialTicks)

        if (popup != null) {
            popup!!.drawScreen(width, height, mouseX, mouseY)
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        if (popup != null) {
            popup!!.mouseClicked(mouseX, mouseY, mouseButton)
            return
        }

        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun actionPerformed(button: GuiButton) {
        if (popup != null) {
            return
        }

        when (button.id) {
            0 -> mc.displayGuiScreen(GuiOptions(this, mc.gameSettings))
            1 -> mc.displayGuiScreen(GuiSelectWorld(this))
            2 -> mc.displayGuiScreen(GuiMultiplayer(this))
            4 -> mc.shutdown()
            100 -> mc.displayGuiScreen(GuiAltManager(this))
            101 -> mc.displayGuiScreen(GuiServerStatus(this))
            102 -> mc.displayGuiScreen(GuiClientConfiguration(this))
            103 -> mc.displayGuiScreen(GuiModsMenu(this))
            108 -> mc.displayGuiScreen(GuiContributors(this))
            109 -> mc.displayGuiScreen(GuiFontManager(this))
        }
    }

    override fun handleMouseInput() {
        if (popup != null) {
            val eventDWheel = Mouse.getEventDWheel()
            if (eventDWheel != 0) {
                popup!!.handleMouseWheel(eventDWheel)
            }
        }

        super.handleMouseInput()
    }
}
