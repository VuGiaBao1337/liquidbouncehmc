/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
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
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.io.IOException
import java.util.*
import kotlin.math.sin

class GuiClientFixes(private val prevGui: GuiScreen) : AbstractScreen() {

    private lateinit var enabledButton: GuiButton
    private lateinit var fmlButton: GuiButton
    private lateinit var proxyButton: GuiButton
    private lateinit var payloadButton: GuiButton
    private lateinit var customBrandButton: GuiButton
    private lateinit var resourcePackButton: GuiButton
    private var animationTime = 0f

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
        val topG = (0.25f + 0.35f * sin(time * 0.7f + 1f)).coerceIn(0f, 1f)
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
        drawGradientBackground()
        
        // Animated title
        val titleTime = animationTime * 1.5f
        val titleR = (0.2f + 0.4f * sin(titleTime)).coerceIn(0f, 1f)
        val titleG = (0.3f + 0.5f * sin(titleTime + 1f)).coerceIn(0f, 1f)
        val titleB = (0.8f + 0.2f * sin(titleTime + 2f)).coerceIn(0f, 1f)
        val titleColor = ((titleR * 255).toInt() shl 16) or ((titleG * 255).toInt() shl 8) or (titleB * 255).toInt()
        
        Fonts.fontBold180.drawCenteredString("Fixes", width / 2f, height / 8f + 5f, titleColor, true)

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
