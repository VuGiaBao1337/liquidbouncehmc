package net.ccbluex.liquidbounce.ui.client.hud

import net.ccbluex.liquidbounce.features.module.modules.render.HUD
import net.ccbluex.liquidbounce.ui.utils.render.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import java.awt.Color
import kotlin.math.abs

class ModernHUD(
    var barWidth: Int = 86,
    var barHeight: Int = 9,
    var barRadius: Float = 5f,
    var barAlpha: Int = 180,
    var iconSize: Int = 13,
    var detail: Boolean = false
) {
    private val mc: Minecraft
        get() = Minecraft.getMinecraft()

    private var displayHealth = 20f
    private var displayAbsorption = 0f
    private var displayArmor = 0f
    private var displayExp = 0f
    private var displayFood = 20f
    private var displayAir = 300f

    private val ICON_HEART = ResourceLocation("liquidbounce/hud/icon-heart.png")
    private val ICON_SHIELD = ResourceLocation("liquidbounce/hud/icon-shield.png")
    private val ICON_FOOD = ResourceLocation("liquidbounce/hud/icon-food.png")

    private fun drawBar(
        x: Int, y: Int, percent: Float, icon: ResourceLocation?, iconYOffset: Int, color: Color, text: String?
    ) {
        val yOffset = y + 4
        drawRoundedBar(x, yOffset, barWidth, barHeight, percent.coerceIn(0f, 1f), color, barRadius)
        icon?.let {
            drawIcon(it, x + 4, yOffset + (barHeight - iconSize) / 2 + iconYOffset, iconSize, iconSize)
        }
        text?.let {
            val textWidth = mc.fontRendererObj.getStringWidth(it)
            mc.fontRendererObj.drawStringWithShadow(
                it,
                (x + barWidth - textWidth - 4).toFloat(),
                (yOffset + (barHeight - mc.fontRendererObj.FONT_HEIGHT) / 2).toFloat(),
                Color.WHITE.rgb
            )
        }
    }

    fun drawHealthBar(x: Int, y: Int) {
        val player = mc.thePlayer ?: return
        if (displayHealth < 0f || abs(player.health - displayHealth) > player.maxHealth)
            displayHealth = player.health
        displayHealth = lerp(displayHealth, player.health, HUD.smoothSpeed)
        if (displayHealth <= 0f) return

        val text = if (detail) "${displayHealth.toInt()}/${player.maxHealth.toInt()}" else null
        drawBar(x, y, displayHealth / player.maxHealth, ICON_HEART, 1, Color(252, 65, 48, barAlpha), text)
    }

    fun drawAbsorptionBar(x: Int, y: Int) {
        val player = mc.thePlayer ?: return
        if (player.absorptionAmount <= 0f) return
        if (displayAbsorption < 0f || abs(player.absorptionAmount - displayAbsorption) > player.maxHealth)
            displayAbsorption = player.absorptionAmount
        displayAbsorption = lerp(displayAbsorption, player.absorptionAmount, HUD.smoothSpeed)
        if (displayAbsorption <= 0f) return

        val text = if (detail) "${displayAbsorption.toInt()}/${player.maxHealth.toInt()}" else null
        drawBar(x, y, displayAbsorption / player.maxHealth, ICON_HEART, 1, Color(212, 175, 55, barAlpha), text)
    }

    fun drawArmorBar(x: Int, y: Int) {
        val player = mc.thePlayer ?: return
        if (player.totalArmorValue <= 0) return
        if (displayArmor < 0f || abs(player.totalArmorValue.toFloat() - displayArmor) > 20f)
            displayArmor = player.totalArmorValue.toFloat()
        displayArmor = lerp(displayArmor, player.totalArmorValue.toFloat(), HUD.smoothSpeed)
        if (displayArmor <= 0f) return

        val text = if (detail) "${displayArmor.toInt()}/20" else null
        drawBar(x, y, displayArmor / 20f, ICON_SHIELD, 1, Color(73, 234, 214, barAlpha), text)
    }

    fun drawFoodBar(x: Int, y: Int) {
        val player = mc.thePlayer ?: return
        if (displayFood < 0f || abs(player.foodStats.foodLevel.toFloat() - displayFood) > 20f)
            displayFood = player.foodStats.foodLevel.toFloat()
        displayFood = lerp(displayFood, player.foodStats.foodLevel.toFloat(), HUD.smoothSpeed)
        if (displayFood <= 0f) return

        val text = if (detail) "${displayFood.toInt()}/20" else null
        drawBar(x, y, displayFood / 20f, ICON_FOOD, 1, Color(184, 132, 88, barAlpha), text)
    }

    fun drawAirBar(x: Int, y: Int) {
        val player = mc.thePlayer ?: return
        if (player.air >= 300) return
        if (displayAir < 0f || abs(player.air.toFloat() - displayAir) > 300f)
            displayAir = player.air.toFloat()
        displayAir = lerp(displayAir, player.air.toFloat(), HUD.smoothSpeed).coerceAtLeast(0f)
        if (displayAir <= 0f) return

        val text = if (detail) "${displayAir.toInt().coerceAtLeast(0)}/300" else null
        drawBar(x, y, displayAir / 300f, null, 0, Color(170, 193, 227, barAlpha), text)
    }

    fun drawExpBar(x: Int, y: Int, width: Int) {
        val player = mc.thePlayer ?: return
        if (displayExp < 0f || abs(player.experience - displayExp) > 1f)
            displayExp = player.experience
        displayExp = lerp(displayExp, player.experience, HUD.smoothSpeed)

        val yOffset = y + 2
        drawRoundedBar(x, yOffset, width, barHeight, displayExp.coerceIn(0f, 1f), Color(136, 198, 87, barAlpha), barRadius)

        val text = if (detail) "${(displayExp * 100).toInt()}% | Lv.${player.experienceLevel}" else player.experienceLevel.toString()
        val textWidth = mc.fontRendererObj.getStringWidth(text)
        mc.fontRendererObj.drawStringWithShadow(
            text,
            (x + width - textWidth - 4).toFloat(),
            (yOffset + (barHeight - mc.fontRendererObj.FONT_HEIGHT) / 2).toFloat(),
            Color.WHITE.rgb
        )
    }

    private fun lerp(current: Float, target: Float, speed: Float): Float {
        if (current.isNaN() || target.isNaN()) return target
        if (abs(target - current) < 0.01f) return target
        return current + (target - current) * speed.coerceIn(0.05f, 1.0f)
    }

    private fun drawRoundedBar(x: Int, y: Int, width: Int, height: Int, percent: Float, color: Color, radius: Float) {
        RenderUtils.drawRoundedRect2(x.toFloat(), y.toFloat(), x + width.toFloat(), y + height.toFloat(), Color(0, 0, 0, 90), radius)
        RenderUtils.drawRoundedRect2(x.toFloat(), y.toFloat(), x + (width * percent).toFloat(), y + height.toFloat(), color, radius)
    }

    private fun drawIcon(resource: ResourceLocation, x: Int, y: Int, w: Int, h: Int, color: Color = Color(255, 255, 255, 255)) {
        GlStateManager.enableBlend()
        GlStateManager.enableAlpha()
        GlStateManager.color(color.red / 255.0f, color.green / 255.0f, color.blue / 255.0f, color.alpha / 255.0f)
        mc.textureManager.bindTexture(resource)
        RenderUtils.drawImage(resource, x, y, w, h, color)
        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.disableAlpha()
        GlStateManager.disableBlend()
    }
}