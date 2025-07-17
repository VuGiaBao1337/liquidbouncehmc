/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.target.LiquidBounce
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.getHealth
import net.ccbluex.liquidbounce.utils.extensions.lerpWith
import net.ccbluex.liquidbounce.utils.extensions.safeDiv
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils.withAlpha
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawGradientRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawHead
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorderRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.withClipping
import net.ccbluex.liquidbounce.utils.render.animation.AnimationUtil
import net.ccbluex.liquidbounce.utils.render.shader.shaders.RainbowShader
import net.minecraft.client.gui.GuiChat
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * A Target HUD
 */
@ElementInfo(name = "Target")
class Target : Element("Target") {
    private val targetHudStyle by choices("Style", arrayOf("LiquidBounce"), "LiquidBounce")

    // LiquidBounce Style Settings
    private val roundedRectRadius by float("Rounded-Radius", 3F, 0F..5F)
    private val borderStrength by float("Border-Strength", 3F, 1F..5F)
    private val backgroundMode by choices("Background-ColorMode", arrayOf("Custom", "Rainbow"), "Custom")
    private val backgroundColor by color("Background-Color", Color.BLACK.withAlpha(150)) { backgroundMode == "Custom" }
    private val healthBarColor1 by color("HealthBar-Gradient1", Color(3, 65, 252))
    private val healthBarColor2 by color("HealthBar-Gradient2", Color(3, 252, 236))
    private val roundHealthBarShape by boolean("RoundHealthBarShape", true)

    private val borderColor by color("Border-Color", Color.BLACK)

    private val textColor by color("TextColor", Color.WHITE)

    private val rainbowX by float("Rainbow-X", -1000F, -2000F..2000F) { backgroundMode == "Rainbow" }
    private val rainbowY by float("Rainbow-Y", -1000F, -2000F..2000F) { backgroundMode == "Rainbow" }

    private val titleFont by font("TitleFont", Fonts.fontSemibold40)
    private val healthFont by font("HealthFont", Fonts.fontRegular30)
    private val textShadow by boolean("TextShadow", false)

    private val fadeSpeed by float("FadeSpeed", 2F, 1F..9F)
    private val absorption by boolean("Absorption", true)
    private val healthFromScoreboard by boolean("HealthFromScoreboard", true)

    private val animation by choices("Animation", arrayOf("Smooth", "Fade"), "Fade")
    private val animationSpeed by float("AnimationSpeed", 0.2F, 0.05F..1F)
    private val vanishDelay by int("VanishDelay", 300, 0..500)

    private var easingHealth = 0F
    private var lastTarget: EntityLivingBase? = null

    private var width = 0f
    private var height = 0f

    private val isRendered
        get() = width > 0f || height > 0f

    private var alphaText = 0
    private var alphaBackground = 0
    private var alphaBorder = 0

    private val isAlpha
        get() = alphaBorder > 0 || alphaBackground > 0 || alphaText > 0

    private var delayCounter = 0
    private var easingHurtTime = 0F

    override fun drawElement(): Border {
        val smoothMode = animation == "Smooth"
        val fadeMode = animation == "Fade"

        val killAuraTarget = KillAura.target.takeIf { it is EntityPlayer }

        val shouldRender = KillAura.handleEvents() && killAuraTarget != null || mc.currentScreen is GuiChat
        val target = killAuraTarget ?: if (delayCounter >= vanishDelay && !isRendered) {
            mc.thePlayer
        } else {
            lastTarget ?: mc.thePlayer
        }

        val stringWidth = (40f + (target.name?.let(titleFont::getStringWidth) ?: 0)).coerceAtLeast(118F)

        assumeNonVolatile {
            // Initialize current style
            val style = when (targetHudStyle.lowercase()) {
                "liquidbounce" -> LiquidBounce(
                    roundedRectRadius,
                    borderStrength,
                    backgroundColor,
                    healthBarColor1,
                    healthBarColor2,
                    roundHealthBarShape,
                    borderColor,
                    textColor,
                    titleFont,
                    healthFont,
                    textShadow
                )
                else -> LiquidBounce(
                    roundedRectRadius,
                    borderStrength,
                    backgroundColor,
                    healthBarColor1,
                    healthBarColor2,
                    roundHealthBarShape,
                    borderColor,
                    textColor,
                    titleFont,
                    healthFont,
                    textShadow
                )
            }

            if (shouldRender) {
                delayCounter = 0
            } else if (isRendered || isAlpha) {
                delayCounter++
            }

            if (shouldRender || isRendered || isAlpha) {
                val targetHealth = getHealth(target!!, healthFromScoreboard, absorption)
                val maxHealth = target.maxHealth + if (absorption) target.absorptionAmount else 0F

                easingHealth += (targetHealth - easingHealth) / 2f.pow(10f - fadeSpeed) * deltaTime
                easingHealth = easingHealth.coerceIn(0f, maxHealth)
                val targetHurtTime = if (target.isEntityAlive()) target.hurtTime.toFloat() else 0F
                easingHurtTime = (easingHurtTime..targetHurtTime).lerpWith(RenderUtils.deltaTimeNormalized())

                if (target != lastTarget || abs(easingHealth - targetHealth) < 0.01) {
                    easingHealth = targetHealth
                }

                if (smoothMode) {
                    val targetWidth = if (shouldRender) stringWidth else if (delayCounter >= vanishDelay) 0f else width
                    width = AnimationUtil.base(width.toDouble(), targetWidth.toDouble(), animationSpeed.toDouble())
                        .toFloat().coerceAtLeast(0f)

                    val targetHeight = if (shouldRender) 36f else if (delayCounter >= vanishDelay) 0f else height
                    height = AnimationUtil.base(height.toDouble(), targetHeight.toDouble(), animationSpeed.toDouble())
                        .toFloat().coerceAtLeast(0f)
                } else {
                    width = stringWidth
                    height = 36f

                    val targetText =
                        if (shouldRender) textColor.alpha else if (delayCounter >= vanishDelay) 0f else alphaText
                    alphaText =
                        AnimationUtil.base(alphaText.toDouble(), targetText.toDouble(), animationSpeed.toDouble())
                            .toInt()

                    val targetBackground = if (shouldRender) {
                        backgroundColor.alpha
                    } else if (delayCounter >= vanishDelay) {
                        0f
                    } else alphaBackground

                    alphaBackground = AnimationUtil.base(
                        alphaBackground.toDouble(), targetBackground.toDouble(), animationSpeed.toDouble()
                    ).toInt()

                    val targetBorder = if (shouldRender) {
                        borderColor.alpha
                    } else if (delayCounter >= vanishDelay) {
                        0f
                    } else alphaBorder

                    alphaBorder =
                        AnimationUtil.base(alphaBorder.toDouble(), targetBorder.toDouble(), animationSpeed.toDouble())
                            .toInt()
                }

                val backgroundCustomColor = backgroundColor.withAlpha(
                    if (fadeMode) alphaBackground else backgroundColor.alpha
                ).rgb
                val borderCustomColor = borderColor.withAlpha(
                    if (fadeMode) alphaBorder else borderColor.alpha
                ).rgb
                val textCustomColor = textColor.withAlpha(
                    if (fadeMode) alphaText else textColor.alpha
                ).rgb

                val rainbowOffset = System.currentTimeMillis() % 10000 / 10000F
                val rainbowX = 1f safeDiv rainbowX
                val rainbowY = 1f safeDiv rainbowY

                glPushMatrix()

                glEnable(GL_BLEND)
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

                if (fadeMode && isAlpha || smoothMode && isRendered || delayCounter < vanishDelay) {
                    // Render current style
                    style.render(target, easingHealth, maxHealth, easingHurtTime, if (fadeMode) alphaBackground.toDouble() else 255.0)
                    val border = style.getBorder(target, easingHealth, maxHealth)
                    if (border != null) {
                        width = border.x2
                        height = border.y2
                    }
                }

                glPopMatrix()
            }
        }

        lastTarget = target
        return Border(0F, 0F, stringWidth, 36F)
    }
}