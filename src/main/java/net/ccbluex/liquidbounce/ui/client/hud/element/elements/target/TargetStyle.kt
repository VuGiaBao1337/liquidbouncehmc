/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements.target

import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.entity.EntityLivingBase
import net.minecraft.client.Minecraft
import kotlin.math.abs

abstract class TargetStyle(val name: String) {
    protected val mc = Minecraft.getMinecraft()

    abstract fun drawTarget(entity: EntityLivingBase, easingHealth: Float, maxHealth: Float, easingHurtTime: Float)
    
    abstract fun getBorder(entity: EntityLivingBase, easingHealth: Float, maxHealth: Float): Border?

    /**
     * Method called when target renderer is created
     */
    open fun render(entity: EntityLivingBase?, easingHealth: Float, maxHealth: Float, easingHurtTime: Float, alpha: Double) {
        if (entity != null) {
            drawTarget(entity, easingHealth, maxHealth, easingHurtTime)
        }
    }

    /**
     * Method called when target renderer is destroyed
     */
    open fun clean() {}
}
