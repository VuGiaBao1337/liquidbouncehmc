/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.ui.client

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.io.HttpClient
import net.ccbluex.liquidbounce.utils.io.defaultAgent
import net.ccbluex.liquidbounce.utils.io.newCall
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import okhttp3.Request
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.io.IOException
import kotlin.math.sin

class GuiServerStatus(private val prevGui: GuiScreen) : AbstractScreen() {
    private val status = hashMapOf<String, String?>(
        "https://api.mojang.com" to null,
        "https://session.minecraft.net" to null,
        "https://textures.minecraft.net" to null,
        "https://minecraft.net" to null,
        "https://account.mojang.com" to null,
        "https://sessionserver.mojang.com" to null,
        "https://mojang.com" to null
    )
    
    private var animationTime = 0f

    override fun initGui() {
        +GuiButton(1, width / 2 - 100, height / 4 + 145, "Back")
        loadInformation()
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
        val topR = (0.1f + 0.2f * sin(time * 0.5f)).coerceIn(0f, 1f)
        val topG = (0.3f + 0.3f * sin(time * 0.7f + 1f)).coerceIn(0f, 1f)
        val topB = (0.7f + 0.3f * sin(time * 0.3f + 2f)).coerceIn(0f, 1f)
        
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

            var i = height / 4 + 40
            
            // Enhanced glowing rect
            val glowIntensity = (0.4f + 0.6f * sin(animationTime * 2f)).coerceIn(0f, 1f)
            val rectColor = (Integer.MIN_VALUE and 0x00FFFFFF) or ((80 * glowIntensity).toInt() shl 24)
            
            drawRect(
                width / 2f - 115,
                i - 5f,
                width / 2f + 115,
                height / 4f + 43 + if (status.keys.isEmpty()) 10 else status.keys.size * Fonts.fontSemibold40.fontHeight,
                rectColor
            )

            for (server in status.keys) {
                val color = status[server] ?: "yellow"
                
                // Animated status colors
                val statusColor = when (color) {
                    "green" -> {
                        val pulse = (0.7f + 0.3f * sin(animationTime * 4f)).coerceIn(0f, 1f)
                        Color(0, (255 * pulse).toInt(), 0).rgb
                    }
                    "red" -> {
                        val pulse = (0.7f + 0.3f * sin(animationTime * 4f)).coerceIn(0f, 1f)
                        Color((255 * pulse).toInt(), 0, 0).rgb
                    }
                    else -> {
                        val pulse = (0.7f + 0.3f * sin(animationTime * 4f)).coerceIn(0f, 1f)
                        Color((255 * pulse).toInt(), (255 * pulse).toInt(), 0).rgb
                    }
                }
                
                Fonts.fontSemibold40.drawCenteredString(
                    "${server.replaceFirst("^http[s]?://".toRegex(), "")}: ${
                        if (color.equals("red", ignoreCase = true)) "§c" 
                        else if (color.equals("yellow", ignoreCase = true)) "§e" 
                        else "§a"
                    }${
                        if (color.equals("red", ignoreCase = true)) "Offline" 
                        else if (color.equals("yellow", ignoreCase = true)) "Loading..." 
                        else "Online"
                    }", width / 2f, i.toFloat(), statusColor
                )
                i += Fonts.fontSemibold40.fontHeight
            }

            // Animated title
            val titleTime = animationTime * 1.5f
            val titleR = (0.2f + 0.3f * sin(titleTime)).coerceIn(0f, 1f)
            val titleG = (0.4f + 0.4f * sin(titleTime + 1f)).coerceIn(0f, 1f)
            val titleB = (0.8f + 0.2f * sin(titleTime + 2f)).coerceIn(0f, 1f)
            val titleColor = ((titleR * 255).toInt() shl 16) or ((titleG * 255).toInt() shl 8) or (titleB * 255).toInt()

            Fonts.fontBold180.drawCenteredString(
                translationMenu("serverStatus"),
                width / 2F,
                height / 8f + 5F,
                titleColor,
                true
            )
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun loadInformation() {
        for (url in status.keys) {
            status[url] = null
            SharedScopes.IO.launch {
                try {
                    status[url] = HttpClient.newCall(fun Request.Builder.() {
                        url(url).head().defaultAgent()
                    }).execute().use {
                        if (it.code in 200..499) "green" else "red"
                    }
                } catch (e: IOException) {
                    status[url] = "red"
                }
            }
        }
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 1) mc.displayGuiScreen(prevGui)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        super.keyTyped(typedChar, keyCode)
    }
}
