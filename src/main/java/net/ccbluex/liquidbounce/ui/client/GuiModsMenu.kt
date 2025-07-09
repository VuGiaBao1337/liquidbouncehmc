/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.LiquidBounce.clientRichPresence
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.file.FileManager.valuesConfig
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorderRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import net.minecraftforge.fml.client.GuiModList
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.*

class GuiModsMenu(private val prevGui: GuiScreen) : AbstractScreen() {

    private lateinit var customTextField: GuiTextField
    
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
        +GuiButton(0, width / 2 - 100, height / 4 + 48, "Forge Mods")
        +GuiButton(1, width / 2 - 100, height / 4 + 48 + 25, "Scripts")
        +GuiButton(
            2,
            width / 2 - 100,
            height / 4 + 48 + 85,
            "Toggle: ${if (clientRichPresence.showRPCValue) "§aON" else "§cOFF"}"
        )
        +GuiButton(
            3,
            width / 2 - 100,
            height / 4 + 48 + 110,
            "Show IP: ${if (clientRichPresence.showRPCServerIP) "§aON" else "§cOFF"}"
        )
        +GuiButton(
            4,
            width / 2 - 100,
            height / 4 + 48 + 135,
            "Show Modules Count: ${if (clientRichPresence.showRPCModulesCount) "§aON" else "§cOFF"}"
        )
        +GuiButton(5, width / 2 - 100, height / 4 + 48 + 255, "Back")

        customTextField = GuiTextField(2, Fonts.fontSemibold35, width / 2 - 100, height / 4 + 48 + 190, 200, 20)
        customTextField.maxStringLength = Int.MAX_VALUE
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
        when (val id = button.id) {
            0 -> mc.displayGuiScreen(GuiModList(this))
            1 -> mc.displayGuiScreen(GuiScripts(this))
            2 -> {
                val rpc = clientRichPresence

                rpc.showRPCValue = if (rpc.showRPCValue) {
                    rpc.shutdown()
                    changeDisplayState(id, false)
                    false
                } else {
                    var value = true
                    SharedScopes.IO.launch {
                        value = try {
                            rpc.setup()
                            true
                        } catch (throwable: Throwable) {
                            LOGGER.error("Failed to setup Discord RPC.", throwable)
                            false
                        }
                    }
                    changeDisplayState(id, value)
                    value
                }
            }
            3 -> {
                val rpc = clientRichPresence
                rpc.showRPCServerIP = if (rpc.showRPCServerIP) {
                    changeDisplayState(id, false)
                    false
                } else {
                    var value = true
                    SharedScopes.IO.launch {
                        value = try {
                            rpc.update()
                            true
                        } catch (throwable: Throwable) {
                            LOGGER.error("Failed to update Discord RPC.", throwable)
                            false
                        }
                    }
                    changeDisplayState(id, value)
                    value
                }
            }
            4 -> {
                val rpc = clientRichPresence
                rpc.showRPCModulesCount = if (rpc.showRPCModulesCount) {
                    rpc.shutdown()
                    changeDisplayState(id, false)
                    false
                } else {
                    var value = true
                    SharedScopes.IO.launch {
                        value = try {
                            rpc.update()
                            true
                        } catch (throwable: Throwable) {
                            LOGGER.error("Failed to update Discord RPC.", throwable)
                            false
                        }
                    }
                    changeDisplayState(id, value)
                    value
                }
            }
            5 -> mc.displayGuiScreen(prevGui)
        }
    }

    private fun changeDisplayState(buttonId: Int, state: Boolean) {
        val button = buttonList[buttonId]
        val displayName = button.displayString
        button.displayString = when (state) {
            false -> displayName.replace("§aON", "§cOFF")
            true -> displayName.replace("§cOFF", "§aON")
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Draw morphing gradient background
        drawMorphingGradientBackground()
        

        Fonts.fontBold180.drawCenteredString(translationMenu("mods"), width / 2F, height / 8F + 5F, Color.WHITE.rgb, true)

        Fonts.fontSemibold40.drawCenteredString("Rich Presence Settings:", width / 2F, height / 4 + 48 + 70F, 0xffffff, true)
        Fonts.fontSemibold40.drawCenteredString("Rich Presence Text:", width / 2F, height / 4 + 48 + 175F, 0xffffff, true)

        customTextField.drawTextBox()
        if (customTextField.text.isEmpty() && !customTextField.isFocused) {
            Fonts.fontSemibold35.drawStringWithShadow(
                clientRichPresence.customRPCText.ifEmpty { translationMenu("discordRPC.typeBox") },
                customTextField.xPosition + 4f,
                customTextField.yPosition + (customTextField.height - Fonts.fontSemibold35.FONT_HEIGHT) / 2F,
                0xffffff
            )
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        customTextField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        if (customTextField.isFocused) {
            customTextField.textboxKeyTyped(typedChar, keyCode)
            clientRichPresence.customRPCText = customTextField.text
            saveConfig(valuesConfig)
        }

        super.keyTyped(typedChar, keyCode)
    }
}
