/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other

import net.ccbluex.liquidbounce.features.module.modules.movement.Fly.vanillaSpeed
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.strafe
import net.minecraft.network.play.client.C00PacketKeepAlive

object KeepAlive : FlyMode("KeepAlive") {
    override fun onUpdate() {
        sendPacket(C00PacketKeepAlive())
        mc.thePlayer.capabilities.isFlying = false

        mc.thePlayer.motionY = when {
            mc.gameSettings.keyBindJump.isKeyDown -> vanillaSpeed.toDouble()
            mc.gameSettings.keyBindSneak.isKeyDown -> -vanillaSpeed.toDouble()
            else -> 0.0
        }

        strafe(vanillaSpeed, true)
    }
}
