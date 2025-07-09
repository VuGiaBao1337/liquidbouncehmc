/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import net.ccbluex.liquidbounce.features.special.ClientFixes
import net.ccbluex.liquidbounce.features.special.ClientFixes.blockFML
import net.ccbluex.liquidbounce.features.special.ClientFixes.blockPayloadPackets
import net.ccbluex.liquidbounce.features.special.ClientFixes.blockProxyPacket
import net.ccbluex.liquidbounce.features.special.ClientFixes.blockResourcePackExploit
import net.ccbluex.liquidbounce.features.special.ClientFixes.clientBrand
import net.ccbluex.liquidbounce.features.special.ClientFixes.fmlFixesEnabled
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.file.FileManager.valuesConfig
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorderRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.io.IOException
import kotlin.math.*

class GuiClientFixes(private val prevGui: GuiScreen) : AbstractScreen() {

    private lateinit var enabledButton: GuiButton
    private lateinit var fmlButton: GuiButton
    private lateinit var proxyButton: GuiButton
    private lateinit var payloadButton: GuiButton
    private lateinit var customBrandButton: GuiButton
    private lateinit var resourcePackButton: GuiButton
    
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
        enabledButton = +GuiButton(
            1,
            width / 2 - 100,
            height / 4 + 35,
            "AntiForge (" + (if (fmlFixesEnabled) "On" else "Off") + ")"
        )
        fmlButton =
            +GuiButton(2, width / 2 - 100, height / 4 + 35 + 25, "Block FML (" + (if (blockFML) "On" else "Off") + ")")
        proxyButton = +GuiButton(
            3,
            width / 2 - 100,
            height / 4 + 35 + 25 * 2,
            "Block FML Proxy Packet (" + (if (blockProxyPacket) "On" else "Off") + ")"
        )
        payloadButton = +GuiButton(
            4,
            width / 2 - 100,
            height / 4 + 35 + 25 * 3,
            "Block Non-MC Payloads (" + (if (blockPayloadPackets) "On" else "Off") + ")"
        )
        customBrandButton = +GuiButton(5, width / 2 - 100, height / 4 + 35 + 25 * 4, "Brand ($clientBrand)")
        resourcePackButton = +GuiButton(
            6,
            width / 2 - 100,
            height / 4 + 50 + 25 * 5,
            "Block Resource Pack Exploit (" + (if (blockResourcePackExploit) "On" else "Off") + ")"
        )

        +GuiButton(0, width / 2 - 100, height / 4 + 55 + 25 * 6 + 5, "Back")
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



    public override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            1 -> {
                fmlFixesEnabled = !fmlFixesEnabled
                enabledButton.displayString = "AntiForge (${if (fmlFixesEnabled) "On" else "Off"})"
            }

            2 -> {
                blockFML = !blockFML
                fmlButton.displayString = "Block FML (${if (blockFML) "On" else "Off"})"
            }

            3 -> {
                blockProxyPacket = !blockProxyPacket
                proxyButton.displayString = "Block FML Proxy Packet (${if (blockProxyPacket) "On" else "Off"})"
            }

            4 -> {
                blockPayloadPackets = !blockPayloadPackets
                payloadButton.displayString = "Block FML Payload Packets (${if (blockPayloadPackets) "On" else "Off"})"
            }

            5 -> {
                val brands = listOf(*ClientFixes.possibleBrands)
                clientBrand = brands[(brands.indexOf(clientBrand) + 1) % brands.size]
                customBrandButton.displayString = "Brand ($clientBrand)"
            }

            6 -> {
                blockResourcePackExploit = !blockResourcePackExploit
                resourcePackButton.displayString =
                    "Block Resource Pack Exploit (${if (blockResourcePackExploit) "On" else "Off"})"
            }

            0 -> mc.displayGuiScreen(prevGui)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Draw morphing gradient background
        drawMorphingGradientBackground()
        
        
        Fonts.fontBold180.drawCenteredString("Fixes", width / 2f, height / 8f + 5f, Color.WHITE.rgb, true)

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    @Throws(IOException::class)
    public override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun onGuiClosed() {
        saveConfig(valuesConfig)
        super.onGuiClosed()
    }
}
