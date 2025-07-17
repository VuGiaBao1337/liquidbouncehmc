/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
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
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiSlot
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.awt.Desktop
import kotlin.math.sin

class GuiScripts(private val prevGui: GuiScreen) : AbstractScreen() {

    private lateinit var list: GuiList
    private var animationTime = 0f

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
        val topR = (0.2f + 0.3f * sin(time * 0.6f)).coerceIn(0f, 1f)
        val topG = (0.4f + 0.4f * sin(time * 0.8f + 1f)).coerceIn(0f, 1f)
        val topB = (0.8f + 0.2f * sin(time * 0.4f + 2f)).coerceIn(0f, 1f)
        
        worldRenderer.pos(width.toDouble(), 0.0, zLevel.toDouble()).color(topR, topG, topB, 1.0f).endVertex()
        worldRenderer.pos(0.0, 0.0, zLevel.toDouble()).color(topR, topG, topB, 1.0f).endVertex()
        worldRenderer.pos(0.0, height.toDouble(), zLevel.toDouble()).color(0.85f, 0.9f, 1.0f, 1.0f).endVertex()
        worldRenderer.pos(width.toDouble(), height.toDouble(), zLevel.toDouble()).color(0.85f, 0.9f, 1.0f, 1.0f).endVertex()
        
        tessellator.draw()
        
        GlStateManager.shadeModel(GL11.GL_FLAT)
        GlStateManager.disableBlend()
        GlStateManager.enableAlpha()
        GlStateManager.enableTexture2D()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile {
            drawGradientBackground()

            list.drawScreen(mouseX, mouseY, partialTicks)

            // Animated title
            val titleTime = animationTime * 1.8f
            val titleR = (0.1f + 0.4f * sin(titleTime)).coerceIn(0f, 1f)
            val titleG = (0.3f + 0.5f * sin(titleTime + 1.5f)).coerceIn(0f, 1f)
            val titleB = (0.9f + 0.1f * sin(titleTime + 3f)).coerceIn(0f, 1f)
            val titleColor = ((titleR * 255).toInt() shl 16) or ((titleG * 255).toInt() shl 8) or (titleB * 255).toInt()

            Fonts.fontSemibold40.drawCenteredString("§9§lScripts", width / 2f, 28f, titleColor)
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

            // Animated script name colors
            val nameGlow = (0.7f + 0.3f * sin(animationTime * 2f + id * 0.5f)).coerceIn(0f, 1f)
            val nameColor = (Color.LIGHT_GRAY.rgb and 0x00FFFFFF) or ((255 * nameGlow).toInt() shl 24)

            Fonts.fontSemibold40.drawCenteredString(
                "§9" + script.scriptName + " §7v" + script.scriptVersion,
                width / 2f,
                y + 2f,
                nameColor
            )

            Fonts.fontSemibold40.drawCenteredString(
                "by §c" + script.scriptAuthors.joinToString(", "),
                width / 2f,
                y + 15f,
                nameColor
            )
        }

        override fun drawBackground() {}
    }
}
