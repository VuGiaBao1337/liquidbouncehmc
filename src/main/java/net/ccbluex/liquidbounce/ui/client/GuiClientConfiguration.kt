/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
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
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraftforge.fml.client.config.GuiSlider
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.sin

class GuiClientConfiguration(val prevGui: GuiScreen) : AbstractScreen() {

    private lateinit var languageButton: GuiButton
    private lateinit var backgroundButton: GuiButton
    private lateinit var particlesButton: GuiButton
    private lateinit var altsModeButton: GuiButton
    private lateinit var unformattedAltsButton: GuiButton
    private lateinit var altsSlider: GuiSlider
    private lateinit var titleButton: GuiButton
    private lateinit var altPrefixField: GuiTextField
    private var animationTime = 0f

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
        val topR = (0.1f + 0.3f * sin(time * 0.4f)).coerceIn(0f, 1f)
        val topG = (0.25f + 0.35f * sin(time * 0.6f + 1f)).coerceIn(0f, 1f)
        val topB = (0.7f + 0.3f * sin(time * 0.2f + 2f)).coerceIn(0f, 1f)
        
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
        drawGradientBackground()
        
        // Animated title
        val titleTime = animationTime * 1.5f
        val titleR = (0.2f + 0.4f * sin(titleTime)).coerceIn(0f, 1f)
        val titleG = (0.3f + 0.5f * sin(titleTime + 1f)).coerceIn(0f, 1f)
        val titleB = (0.8f + 0.2f * sin(titleTime + 2f)).coerceIn(0f, 1f)
        val titleColor = ((titleR * 255).toInt() shl 16) or ((titleG * 255).toInt() shl 8) or (titleB * 255).toInt()
        
        Fonts.fontBold180.drawCenteredString(
            translationMenu("configuration"), width / 2F, height / 8F + 5F, titleColor, true
        )

        // Glowing section headers
        val headerGlow = (0.7f + 0.3f * sin(animationTime * 2.5f)).coerceIn(0f, 1f)
        val headerColor = (0xFFFFFF and 0x00FFFFFF) or ((255 * headerGlow).toInt() shl 24)

        Fonts.fontSemibold40.drawString(
            "Window", width / 2F - 98F, height / 4F + 15F, headerColor, true
        )

        Fonts.fontSemibold40.drawString(
            "Background", width / 2F - 98F, height / 4F + 90F, headerColor, true
        )
        Fonts.fontSemibold35.drawString(
            "Supported background types: (.png, .frag, .glsl)",
            width / 2F - 98F,
            height / 4F + 100 + 25 * 3,
            headerColor,
            true
        )

        Fonts.fontSemibold40.drawString(
            translationMenu("altManager"), width / 2F - 98F, height / 4F + 200F, headerColor, true
        )

        altPrefixField.drawTextBox()
        if (altPrefixField.text.isEmpty() && !altPrefixField.isFocused) {
            val placeholderGlow = (0.5f + 0.3f * sin(animationTime * 1.5f)).coerceIn(0f, 1f)
            val placeholderColor = (0xFFFFFF and 0x00FFFFFF) or ((255 * placeholderGlow).toInt() shl 24)
            
            Fonts.fontSemibold35.drawStringWithShadow(
                altsPrefix.ifEmpty { translationMenu("altManager.typeCustomPrefix") },
                altPrefixField.xPosition + 4f,
                altPrefixField.yPosition + (altPrefixField.height - Fonts.fontSemibold35.FONT_HEIGHT) / 2F,
                placeholderColor
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
