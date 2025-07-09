/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import net.ccbluex.liquidbounce.LiquidBounce.scriptManager
import net.ccbluex.liquidbounce.file.FileManager.clickGuiConfig
import net.ccbluex.liquidbounce.file.FileManager.hudConfig
import net.ccbluex.liquidbounce.file.FileManager.loadConfig
import net.ccbluex.liquidbounce.file.FileManager.loadConfigs
import net.ccbluex.liquidbounce.script.ScriptManager
import net.ccbluex.liquidbounce.script.ScriptManager.reloadScripts
import net.ccbluex.liquidbounce.script.ScriptManager.scriptsFolder
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.io.FileFilters
import net.ccbluex.liquidbounce.utils.io.MiscUtils
import net.ccbluex.liquidbounce.utils.io.extractZipTo
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorderRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiSlot
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.awt.Desktop
import kotlin.math.*

class GuiScripts(private val prevGui: GuiScreen) : AbstractScreen() {

    private lateinit var list: GuiList
    
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
        list = GuiList(this)
        list.registerScrollButtons(7, 8)
        list.elementClicked(-1, false, 0, 0)

        val j = 22
        +GuiButton(0, width - 80, height - 65, 70, 20, "Back")
        +GuiButton(1, width - 80, j + 24, 70, 20, "Import")
        +GuiButton(2, width - 80, j + 24 * 2, 70, 20, "Delete")
        +GuiButton(3, width - 80, j + 24 * 3, 70, 20, "Reload")
        +GuiButton(4, width - 80, j + 24 * 4, 70, 20, "Folder")
        +GuiButton(5, width - 80, j + 24 * 5, 70, 20, "Docs")
        +GuiButton(6, width - 80, j + 24 * 6, 70, 20, "Find Scripts")
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
            

            list.drawScreen(mouseX, mouseY, partialTicks)

            Fonts.fontBold180.drawCenteredString("§9§lScripts", width / 2f, height / 8f + 5f, Color.WHITE.rgb, true)
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(prevGui)
            1 -> try {
                val file = MiscUtils.openFileChooser(FileFilters.JAVASCRIPT, FileFilters.ARCHIVE) ?: return

                when (file.extension.lowercase()) {
                    "js" -> {
                        scriptManager.importScript(file)
                        loadConfig(clickGuiConfig)
                    }

                    "zip" -> {
                        val existingFiles = ScriptManager.availableScriptFiles.toSet()
                        file.extractZipTo(scriptsFolder)
                        ScriptManager.availableScriptFiles.filterNot {
                            it in existingFiles
                        }.forEach(scriptManager::loadScript)
                        loadConfigs(clickGuiConfig, hudConfig)
                    }

                    else -> MiscUtils.showMessageDialog("Wrong file extension", "The file extension has to be .js or .zip")
                }
            } catch (t: Throwable) {
                LOGGER.error("Something went wrong while importing a script.", t)
                MiscUtils.showMessageDialog(t.javaClass.name, t.message!!)
            }

            2 -> try {
                if (list.getSelectedSlot() != -1) {
                    val script = ScriptManager[list.getSelectedSlot()]
                    scriptManager.deleteScript(script)
                    loadConfigs(clickGuiConfig, hudConfig)
                }
            } catch (t: Throwable) {
                LOGGER.error("Something went wrong while deleting a script.", t)
                MiscUtils.showMessageDialog(t.javaClass.name, t.message!!)
            }

            3 -> try {
                reloadScripts()
            } catch (t: Throwable) {
                LOGGER.error("Something went wrong while reloading all scripts.", t)
                MiscUtils.showMessageDialog(t.javaClass.name, t.message!!)
            }

            4 -> try {
                Desktop.getDesktop().open(scriptsFolder)
            } catch (t: Throwable) {
                LOGGER.error("Something went wrong while trying to open your scripts folder.", t)
                MiscUtils.showMessageDialog(t.javaClass.name, t.message!!)
            }

            5 -> try {
                MiscUtils.showURL("https://github.com/CCBlueX/Documentation/blob/master/md/scriptapi_v2/getting_started.md")
            } catch (e: Exception) {
                LOGGER.error("Something went wrong while trying to open the web scripts docs.", e)
                MiscUtils.showMessageDialog(
                    "Scripts Error | Manual Link",
                    "github.com/CCBlueX/Documentation/blob/master/md/scriptapi_v2/getting_started.md"
                )
            }

            6 -> try {
                MiscUtils.showURL("https://forums.ccbluex.net/category/9/scripts")
            } catch (e: Exception) {
                LOGGER.error("Something went wrong while trying to open web scripts forums", e)
                MiscUtils.showMessageDialog("Scripts Error | Manual Link", "forums.ccbluex.net/category/9/scripts")
            }
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        super.keyTyped(typedChar, keyCode)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        list.handleMouseInput()
    }

    private inner class GuiList(gui: GuiScreen) :
        GuiSlot(mc, gui.width, gui.height, 40, gui.height - 40, 30) {

        private var selectedSlot = 0

        override fun isSelected(id: Int) = selectedSlot == id

        fun getSelectedSlot() = if (selectedSlot > ScriptManager.size) -1 else selectedSlot

        override fun getSize() = ScriptManager.size

        public override fun elementClicked(id: Int, doubleClick: Boolean, var3: Int, var4: Int) {
            selectedSlot = id
        }

        override fun drawSlot(id: Int, x: Int, y: Int, var4: Int, var5: Int, var6: Int) {
            val script = ScriptManager[id]

            Fonts.fontSemibold40.drawCenteredString(
                "§9" + script.scriptName + " §7v" + script.scriptVersion,
                width / 2f,
                y + 2f,
                Color.WHITE.rgb
            )

            Fonts.fontSemibold40.drawCenteredString(
                "by §c" + script.scriptAuthors.joinToString(", "),
                width / 2f,
                y + 15f,
                Color.WHITE.rgb
            )
        }

        override fun drawBackground() {}
    }
}
