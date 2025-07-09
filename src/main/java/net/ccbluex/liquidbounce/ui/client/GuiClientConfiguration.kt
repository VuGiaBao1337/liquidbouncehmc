/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import net.ccbluex.liquidbounce.LiquidBounce.background
import net.ccbluex.liquidbounce.file.FileManager.backgroundImageFile
import net.ccbluex.liquidbounce.file.FileManager.backgroundShaderFile
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.file.FileManager.valuesConfig
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.altsLength
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.altsPrefix
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.clientTitle
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.customBackground
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.overrideLanguage
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.particles
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.stylisedAlts
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.unformattedAlts
import net.ccbluex.liquidbounce.file.configs.models.ClientConfiguration.updateClientWindow
import net.ccbluex.liquidbounce.lang.LanguageManager
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.io.FileFilters
import net.ccbluex.liquidbounce.utils.io.MiscUtils
import net.ccbluex.liquidbounce.utils.io.MiscUtils.showErrorPopup
import net.ccbluex.liquidbounce.utils.io.MiscUtils.showMessageDialog
import net.ccbluex.liquidbounce.utils.render.shader.Background
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorderRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import net.minecraftforge.fml.client.config.GuiSlider
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.*

class GuiClientConfiguration(val prevGui: GuiScreen) : AbstractScreen() {

    private lateinit var languageButton: GuiButton
    private lateinit var backgroundButton: GuiButton
    private lateinit var particlesButton: GuiButton
    private lateinit var altsModeButton: GuiButton
    private lateinit var unformattedAltsButton: GuiButton
    private lateinit var altsSlider: GuiSlider
    private lateinit var titleButton: GuiButton
    private lateinit var altPrefixField: GuiTextField
    
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
        titleButton = +GuiButton(
            4, width / 2 - 100, height / 4 + 25, "Client title (${if (clientTitle) "On" else "Off"})"
        )

        languageButton = +GuiButton(
            7,
            width / 2 - 100,
            height / 4 + 50,
            "Language (${overrideLanguage.ifBlank { "Game" }})"
        )

        backgroundButton = +GuiButton(
            0,
            width / 2 - 100,
            height / 4 + 25 + 75,
            "Enabled (${if (customBackground) "On" else "Off"})"
        )

        particlesButton = +GuiButton(
            1, width / 2 - 100, height / 4 + 25 + 75 + 25, "Particles (${if (particles) "On" else "Off"})"
        )

        +GuiButton(2, width / 2 - 100, height / 4 + 25 + 75 + 25 * 2, 98, 20, "Change wallpaper")
        +GuiButton(3, width / 2 + 2, height / 4 + 25 + 75 + 25 * 2, 98, 20, "Reset wallpaper")

        altsModeButton = +GuiButton(
            6,
            width / 2 - 100,
            height / 4 + 25 + 185,
            "Random alts mode (${if (stylisedAlts) "Stylised" else "Legacy"})"
        )

        altsSlider = +GuiSlider(
            -1,
            width / 2 - 100,
            height / 4 + 210 + 25,
            200,
            20,
            "${if (stylisedAlts && unformattedAlts) "Random alt max" else "Random alt"} length (",
            ")",
            6.0,
            16.0,
            altsLength.toDouble(),
            false,
            true
        ) {
            altsLength = it.valueInt
        }

        unformattedAltsButton = +GuiButton(
            5,
            width / 2 - 100,
            height / 4 + 235 + 25,
            "Unformatted alt names (${if (unformattedAlts) "On" else "Off"})"
        ).also {
            it.enabled = stylisedAlts
        }

        altPrefixField = GuiTextField(2, Fonts.fontSemibold35, width / 2 - 100, height / 4 + 260 + 25, 200, 20)
        altPrefixField.maxStringLength = 16

        +GuiButton(8, width / 2 - 100, height / 4 + 25 + 25 + 25 * 11, "Back")
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



    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> {
                customBackground = !customBackground
                backgroundButton.displayString = "Enabled (${if (customBackground) "On" else "Off"})"
            }

            1 -> {
                particles = !particles
                particlesButton.displayString = "Particles (${if (particles) "On" else "Off"})"
            }

            4 -> {
                clientTitle = !clientTitle
                titleButton.displayString = "Client title (${if (clientTitle) "On" else "Off"})"
                updateClientWindow()
            }

            5 -> {
                unformattedAlts = !unformattedAlts
                unformattedAltsButton.displayString = "Unformatted alt names (${if (unformattedAlts) "On" else "Off"})"
                altsSlider.dispString = "${if (unformattedAlts) "Max random alt" else "Random alt"} length ("
                altsSlider.updateSlider()
            }

            6 -> {
                stylisedAlts = !stylisedAlts
                altsModeButton.displayString = "Random alts mode (${if (stylisedAlts) "Stylised" else "Legacy"})"
                altsSlider.dispString =
                    "${if (stylisedAlts && unformattedAlts) "Max random alt" else "Random alt"} length ("
                altsSlider.updateSlider()
                unformattedAltsButton.enabled = stylisedAlts
            }

            2 -> {
                val file = MiscUtils.openFileChooser(FileFilters.IMAGE, FileFilters.SHADER) ?: return

                background = null
                if (backgroundImageFile.exists()) backgroundImageFile.deleteRecursively()
                if (backgroundShaderFile.exists()) backgroundShaderFile.deleteRecursively()

                val fileExtension = file.extension

                background = try {
                    val destFile = when (fileExtension.lowercase()) {
                        "png" -> backgroundImageFile
                        "frag", "glsl", "shader" -> backgroundShaderFile
                        else -> {
                            showMessageDialog("Error", "Invalid file extension: $fileExtension")
                            return
                        }
                    }

                    file.copyTo(destFile)
                    Background.fromFile(destFile)
                } catch (e: Exception) {
                    e.showErrorPopup()
                    if (backgroundImageFile.exists()) backgroundImageFile.deleteRecursively()
                    if (backgroundShaderFile.exists()) backgroundShaderFile.deleteRecursively()
                    null
                }
            }

            3 -> {
                background = null
                if (backgroundImageFile.exists()) backgroundImageFile.deleteRecursively()
                if (backgroundShaderFile.exists()) backgroundShaderFile.deleteRecursively()
            }

            7 -> {
                val languageIndex = LanguageManager.knownLanguages.indexOf(overrideLanguage)

                overrideLanguage = when (languageIndex) {
                    -1 -> LanguageManager.knownLanguages.first()
                    LanguageManager.knownLanguages.size - 1 -> ""
                    else -> LanguageManager.knownLanguages[languageIndex + 1]
                }

                languageButton.displayString = "Language (${overrideLanguage.ifBlank { "Game" }})"
            }

            8 -> mc.displayGuiScreen(prevGui)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Draw morphing gradient background
        drawMorphingGradientBackground()
        
        
        Fonts.fontBold180.drawCenteredString(
            translationMenu("configuration"), width / 2F, height / 8F + 5F, Color.WHITE.rgb, true
        )

        Fonts.fontSemibold40.drawString(
            "Window", width / 2F - 98F, height / 4F + 15F, 0xFFFFFF, true
        )

        Fonts.fontSemibold40.drawString(
            "Background", width / 2F - 98F, height / 4F + 90F, 0xFFFFFF, true
        )
        Fonts.fontSemibold35.drawString(
            "Supported background types: (.png, .frag, .glsl)",
            width / 2F - 98F,
            height / 4F + 100 + 25 * 3,
            0xFFFFFF,
            true
        )

        Fonts.fontSemibold40.drawString(
            translationMenu("altManager"), width / 2F - 98F, height / 4F + 200F, 0xFFFFFF, true
        )

        altPrefixField.drawTextBox()
        if (altPrefixField.text.isEmpty() && !altPrefixField.isFocused) {
            Fonts.fontSemibold35.drawStringWithShadow(
                altsPrefix.ifEmpty { translationMenu("altManager.typeCustomPrefix") },
                altPrefixField.xPosition + 4f,
                altPrefixField.yPosition + (altPrefixField.height - Fonts.fontSemibold35.FONT_HEIGHT) / 2F,
                0xffffff
            )
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        if (altPrefixField.isFocused) {
            altPrefixField.textboxKeyTyped(typedChar, keyCode)
            altsPrefix = altPrefixField.text
            saveConfig(valuesConfig)
        }

        super.keyTyped(typedChar, keyCode)
    }

    public override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        altPrefixField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun onGuiClosed() {
        saveConfig(valuesConfig)
        super.onGuiClosed()
    }
}
