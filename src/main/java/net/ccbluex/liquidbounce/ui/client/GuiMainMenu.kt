/*
 * LiquidBounce Hacked Client
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
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorderRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiOptions
import net.minecraft.client.gui.GuiSelectWorld
import net.minecraft.client.resources.I18n
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*
import kotlin.random.Random

class GuiMainMenu : AbstractScreen() {
    
    private var popup: PopupScreen? = null
    
    // Morphing gradient animation system
    private var animationTime = 0f
    private var morphingSpeed = 0.008f
    
    // Gradient color sets for morphing - More vibrant colors
    private val gradientSets = arrayOf(
        // Set 1: Blue to Cyan
        arrayOf(
            Color(74, 144, 226),   // Light Blue
            Color(56, 189, 248),   // Sky Blue
            Color(34, 211, 238),   // Cyan
            Color(6, 182, 212)     // Dark Cyan
        ),
        // Set 2: Cyan to Green
        arrayOf(
            Color(34, 211, 238),   // Cyan
            Color(6, 182, 212),    // Dark Cyan
            Color(16, 185, 129),   // Emerald
            Color(34, 197, 94)     // Green
        ),
        // Set 7: Pink to Purple
        arrayOf(
            Color(236, 72, 153),   // Pink
            Color(192, 132, 252),  // Light Purple
            Color(147, 51, 234),   // Purple
            Color(109, 40, 217)    // Deep Purple
        ),
        // Set 8: Purple to Blue (completing cycle)
        arrayOf(
            Color(147, 51, 234),   // Purple
            Color(109, 40, 217),   // Deep Purple
            Color(99, 102, 241),   // Indigo
            Color(74, 144, 226)    // Back to Light Blue
        )
    )
    
    private var currentGradientIndex = 0
    private var nextGradientIndex = 1
    private var morphProgress = 0f
    
    // Gradient mesh points for smooth morphing
    private val meshResolution = 20
    private val gradientMesh = Array(meshResolution + 1) { Array(meshResolution + 1) { Color.WHITE } }
    
    companion object {
        private var popupOnce = false
        var lastWarningTime: Long? = null
        private val warningInterval = TimeUnit.DAYS.toMillis(7)
        fun shouldShowWarning() = lastWarningTime == null || Instant.now().toEpochMilli() - lastWarningTime!! > warningInterval
    }

    init {
        if (!popupOnce) {
            javaVersion?.let {
                when {
                    it.major == 1 && it.minor == 8 && it.update < 100 -> showOutdatedJava8Warning()
                    it.major > 8 -> showJava11Warning()
                }
            }

            popupOnce = true
        }
    }

    override fun initGui() {
        val defaultHeight = height / 4 + 48
        val baseCol1 = width / 2 - 100
        val baseCol2 = width / 2 + 2

        +GuiButton(100, baseCol1, defaultHeight + 24, 98, 20, translationMenu("altManager"))
        +GuiButton(103, baseCol2, defaultHeight + 24, 98, 20, translationMenu("mods"))
        +GuiButton(109, baseCol1, defaultHeight + 24 * 2, 98, 20, translationMenu("fontManager"))
        +GuiButton(102, baseCol2, defaultHeight + 24 * 2, 98, 20, translationMenu("configuration"))
        +GuiButton(101, baseCol1, defaultHeight + 24 * 3, 98, 20, translationMenu("serverStatus"))
        +GuiButton(108, baseCol2, defaultHeight + 24 * 3, 98, 20, translationMenu("contributors"))
        +GuiButton(1, baseCol1, defaultHeight, 98, 20, I18n.format("menu.singleplayer"))
        +GuiButton(2, baseCol2, defaultHeight, 98, 20, I18n.format("menu.multiplayer"))
        +GuiButton(0, baseCol1, defaultHeight + 24 * 4, 98, 20, I18n.format("menu.options"))
        +GuiButton(4, baseCol2, defaultHeight + 24 * 4, 98, 20, I18n.format("menu.quit"))
    }

    private fun updateMorphingGradient() {
        animationTime += morphingSpeed
        morphProgress += 0.01f
        
        // Switch to next gradient set when morph is complete
        if (morphProgress >= 1f) {
            morphProgress = 0f
            currentGradientIndex = nextGradientIndex
            nextGradientIndex = (nextGradientIndex + 1) % gradientSets.size
        }
        
        // Update gradient mesh with morphed colors
        updateGradientMesh()
    }

    private fun updateGradientMesh() {
        val currentSet = gradientSets[currentGradientIndex]
        val nextSet = gradientSets[nextGradientIndex]
        
        for (x in 0..meshResolution) {
            for (y in 0..meshResolution) {
                val xRatio = x.toFloat() / meshResolution
                val yRatio = y.toFloat() / meshResolution
                
                // Add noise for organic movement
                val noiseX = sin(animationTime * 0.5f + xRatio * 4f + yRatio * 2f) * 0.1f
                val noiseY = cos(animationTime * 0.7f + xRatio * 3f + yRatio * 4f) * 0.1f
                
                val adjustedX = (xRatio + noiseX).coerceIn(0f, 1f)
                val adjustedY = (yRatio + noiseY).coerceIn(0f, 1f)
                
                // Interpolate between 4 corner colors
                val currentColor = interpolateGradientColors(currentSet, adjustedX, adjustedY)
                val nextColor = interpolateGradientColors(nextSet, adjustedX, adjustedY)
                
                // Morph between current and next color
                gradientMesh[x][y] = morphColors(currentColor, nextColor, morphProgress)
            }
        }
    }

    private fun interpolateGradientColors(colorSet: Array<Color>, x: Float, y: Float): Color {
        // Bilinear interpolation between 4 corner colors
        val topLeft = colorSet[0]
        val topRight = colorSet[1]
        val bottomLeft = colorSet[2]
        val bottomRight = colorSet[3]
        
        // Interpolate top edge
        val topColor = interpolateColors(topLeft, topRight, x)
        // Interpolate bottom edge
        val bottomColor = interpolateColors(bottomLeft, bottomRight, x)
        // Interpolate between top and bottom
        return interpolateColors(topColor, bottomColor, y)
    }

    private fun interpolateColors(color1: Color, color2: Color, ratio: Float): Color {
        val r = (color1.red + (color2.red - color1.red) * ratio).toInt().coerceIn(0, 255)
        val g = (color1.green + (color2.green - color1.green) * ratio).toInt().coerceIn(0, 255)
        val b = (color1.blue + (color2.blue - color1.blue) * ratio).toInt().coerceIn(0, 255)
        return Color(r, g, b)
    }

    private fun morphColors(color1: Color, color2: Color, progress: Float): Color {
        // Smooth morphing with easing
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
        
        // Update morphing animation
        updateMorphingGradient()
        
        // Draw clean gradient mesh only
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
                
                // Get colors for quad corners
                val color1 = gradientMesh[x][y]
                val color2 = gradientMesh[x + 1][y]
                val color3 = gradientMesh[x + 1][y + 1]
                val color4 = gradientMesh[x][y + 1]
                
                // Draw quad with smooth color interpolation
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



    // Rainbow text effect
    private fun getRainbowColor(offset: Float = 0f): Color {
        val hue = ((System.currentTimeMillis() * 0.001f + offset) % 6f) / 6f
        return Color.getHSBColor(hue, 0.8f, 1f)
    }

    // Pulsing effect
    private fun getPulsingAlpha(): Float {
        return 0.7f + sin(animationTime * 4f) * 0.3f
    }

    // Wave effect for text
    private fun getWaveOffset(index: Int): Float {
        return sin(animationTime * 3f + index * 0.5f) * 3f
    }

    private fun drawTitleWithGlow() {
        val titleX = width / 2f
        val titleY = height / 8f
        val titleText = "Rinbounce"
        
        // Beautiful white color instead of cyan
        val whiteColor = Color(255, 255, 255) // Pure white
        val grayColor = Color(200, 200, 200)  // Light gray for depth

        // Multi-layer glow effect with white
        for (i in 1..6) {
            val glowAlpha = (0.4f - i * 0.06f) * getPulsingAlpha()
            val glowOffset = i * 2f

            Fonts.fontBold180.drawCenteredString(
                titleText, 
                titleX + glowOffset, titleY + glowOffset, 
                Color(whiteColor.red, whiteColor.green, whiteColor.blue, (glowAlpha * 255).toInt()).rgb, 
                false
            )
        }

        // Shadow effect with gray
        Fonts.fontBold180.drawCenteredString(
            titleText, titleX + 3, titleY + 3, 
            Color(grayColor.red, grayColor.green, grayColor.blue, 120).rgb, false
        )

        // Main white text with wave effect
        val totalWidth = Fonts.fontBold180.getStringWidth(titleText)
        var currentX = titleX - (totalWidth / 2f)

        for (i in titleText.indices) {
            val char = titleText[i].toString()
            val charWidth = Fonts.fontBold180.getStringWidth(char)
            val charY = titleY + getWaveOffset(i)

            // Pulsing white color
            val pulseIntensity = getPulsingAlpha()
            val finalWhite = Color(
                (whiteColor.red * pulseIntensity).toInt().coerceIn(200, 255),
                (whiteColor.green * pulseIntensity).toInt().coerceIn(200, 255),
                (whiteColor.blue * pulseIntensity).toInt().coerceIn(200, 255),
                255
            )

            Fonts.fontBold180.drawString(
                char, currentX, charY,
                finalWhite.rgb, true
            )

            currentX += charWidth
        }

        // Sparkle effects around title with white color
        drawWhiteSparkleEffects(titleX, titleY, titleText)

        // Version text with white
        val versionAlpha = 0.8f + sin(animationTime * 2f) * 0.2f
        Fonts.fontSemibold35.drawCenteredString(
            clientVersionText,
            titleX + 148, titleY + Fonts.fontSemibold35.fontHeight,
            Color(whiteColor.red, whiteColor.green, whiteColor.blue, (versionAlpha * 255).toInt()).rgb, true
        )
    }

    private fun drawWhiteSparkleEffects(titleX: Float, titleY: Float, titleText: String) {
        val titleWidth = Fonts.fontBold180.getStringWidth(titleText)
        val titleHeight = Fonts.fontBold180.fontHeight
        val whiteColor = Color(255, 255, 255)
        val lightGray = Color(240, 240, 240)
        
        // Generate sparkles around title
        for (i in 0..8) {
            val sparkleTime = animationTime * 2f + i * 0.8f
            val sparkleAlpha = (sin(sparkleTime) * 0.5f + 0.5f) * 0.8f

            if (sparkleAlpha > 0.3f) {
                val sparkleX = titleX + sin(sparkleTime * 0.7f) * (titleWidth * 0.6f)
                val sparkleY = titleY + cos(sparkleTime * 0.9f) * (titleHeight * 0.8f)
                val sparkleSize = 2f + sin(sparkleTime * 1.5f) * 1f
                val sparkleColor = if (i % 2 == 0) whiteColor else lightGray
            
                // Draw sparkle
                glDisable(GL_TEXTURE_2D)
                glEnable(GL_BLEND)
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            
                glColor4f(
                    sparkleColor.red / 255f, 
                    sparkleColor.green / 255f, 
                    sparkleColor.blue / 255f, 
                    sparkleAlpha
                )
            
                glBegin(GL_QUADS)
                glVertex2f(sparkleX - sparkleSize, sparkleY - sparkleSize)
                glVertex2f(sparkleX + sparkleSize, sparkleY - sparkleSize)
                glVertex2f(sparkleX + sparkleSize, sparkleY + sparkleSize)
                glVertex2f(sparkleX - sparkleSize, sparkleY + sparkleSize)
                glEnd()
            
                glEnable(GL_TEXTURE_2D)
            }
        }
    }

    private fun showWelcomePopup() {
        popup = PopupScreen {
            title("§a§lWelcome!")
            message("""
                §eThank you for downloading and installing §bLiquidBounce§e!
                
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
                §6§lThis version of $CLIENT_NAME is designed for Java 8 environment.§r
                
                §fHigher versions of Java might cause bug or crash.
                You can get JRE 8 from the Internet.
            """.trimIndent())
            button("§aDownload Java") { MiscUtils.showURL(JavaVersion.DOWNLOAD_PAGE) }
            button("§eI realized")
            onClose { popup = null }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Draw morphing gradient background
        drawMorphingGradientBackground()
        
        
        // Draw title with enhanced glow effect
        drawTitleWithGlow()
        
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
