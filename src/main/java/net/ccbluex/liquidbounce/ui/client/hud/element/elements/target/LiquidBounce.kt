/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements.target

import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawGradientRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawHead
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorderRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.withClipping
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.abs
import kotlin.math.max

class LiquidBounce(
    private val roundedRectRadius: Float,
    private val borderStrength: Float,
    private val backgroundColor: Color,
    private val healthBarColor1: Color,
    private val healthBarColor2: Color,
    private val roundHealthBarShape: Boolean,
    private val borderColor: Color,
    private val textColor: Color,
    private val titleFont: Any,
    private val healthFont: Any,
    private val textShadow: Boolean
) : TargetStyle("LiquidBounce") {

    override fun drawTarget(entity: EntityLivingBase, easingHealth: Float, maxHealth: Float, easingHurtTime: Float) {
        val width = (40f + (entity.name?.let { Fonts.fontSemibold40.getStringWidth(it) } ?: 0)).coerceAtLeast(118F)
        val height = 36f

        // Draw background and border
        drawRoundedBorderRect(
            0F, 0F, width, height,
            borderStrength,
            backgroundColor.rgb,
            borderColor.rgb,
            roundedRectRadius
        )

        // Draw health bar
        val healthBarTop = 24F
        val healthBarHeight = 8F
        val healthBarStart = 36F
        val healthBarTotal = (width - 39F).coerceAtLeast(0F)
        val currentWidth = (easingHealth / maxHealth).coerceIn(0F, 1F) * healthBarTotal

        // Draw background bar
        val backgroundBar = {
            drawRoundedRect(
                healthBarStart,
                healthBarTop,
                healthBarStart + healthBarTotal,
                healthBarTop + healthBarHeight,
                Color.BLACK.rgb,
                6F,
            )
        }

        if (roundHealthBarShape) {
            backgroundBar()
        }

        // Draw main health bar
        withClipping(main = {
            if (roundHealthBarShape) {
                drawRoundedRect(
                    healthBarStart,
                    healthBarTop,
                    healthBarStart + currentWidth,
                    healthBarTop + healthBarHeight,
                    0,
                    6F
                )
            } else {
                backgroundBar()
            }
        }, toClip = {
            drawGradientRect(
                healthBarStart.toInt(),
                healthBarTop.toInt(),
                healthBarStart.toInt() + currentWidth.toInt(),
                healthBarTop.toInt() + healthBarHeight.toInt(),
                healthBarColor1.rgb,
                healthBarColor2.rgb,
                0f
            )
        })

        // Draw health percentage
        val healthPercentage = (easingHealth / maxHealth * 100).toInt()
        val percentageText = "$healthPercentage%"
        val textWidth = Fonts.fontRegular30.getStringWidth(percentageText)
        val calcX = healthBarStart + currentWidth - textWidth
        val textX = max(healthBarStart, calcX)
        val textY = healthBarTop - Fonts.fontRegular30.fontHeight / 2 - 2F
        Fonts.fontRegular30.drawString(percentageText, textX, textY, textColor.rgb, textShadow)

        // Draw head
        glPushMatrix()
        val scale = 1 - easingHurtTime / 10f
        val f1 = (0.7F..1F).lerpWith(scale)
        val color = ColorUtils.interpolateColor(Color.RED, Color.WHITE, scale)
        val centerX1 = (4..32).lerpWith(0.5F)
        val midY = (4f..28f).lerpWith(0.5F)

        glTranslatef(centerX1, midY, 0f)
        glScalef(f1, f1, f1)
        glTranslatef(-centerX1, -midY, 0f)

        val entityTexture = mc.renderManager.getEntityRenderObject<net.minecraft.entity.Entity>(entity)?.getEntityTexture(entity)
        if (entityTexture != null) {
            withClipping(main = {
                drawRoundedRect(4f, 4f, 32f, 32f, 0, roundedRectRadius)
            }, toClip = {
                drawHead(entityTexture, 4, 4, 8f, 8f, 8, 8, 28, 28, 64F, 64F, color)
            })
        }
        glPopMatrix()

        // Draw name
        entity.name?.let {
            (titleFont as net.ccbluex.liquidbounce.ui.font.GameFontRenderer).drawString(it, healthBarStart, 6F, textColor.rgb, textShadow)
        }
    }

    override fun getBorder(entity: EntityLivingBase, easingHealth: Float, maxHealth: Float): Border {
        val width = (40f + (entity.name?.let { Fonts.fontSemibold40.getStringWidth(it) } ?: 0)).coerceAtLeast(118F)
        return Border(0F, 0F, width, 36F)
    }

    private fun ClosedFloatingPointRange<Float>.lerpWith(alpha: Float): Float {
        return start + (endInclusive - start) * alpha
    }

    private fun IntRange.lerpWith(alpha: Float): Float {
        return first + (last - first) * alpha
    }
}
