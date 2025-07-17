/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPackets
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition

object Criticals : Module("Criticals", Category.COMBAT) {

    val mode by choices(
        "Mode",
        arrayOf(
            "Packet",
            "NoGround",
            "Jump",
            "LowJump",
            "Grim",
            "BlocksMC",
            "Visual"
        ),
        "Packet"
    )

    val delay by int("Delay", 0, 0..500)
    private val hurtTime by int("HurtTime", 10, 0..10)
    private val customMotionY by float("Custom-Y", 0.2f, 0.01f..0.42f) { mode == "CustomMotion" }

    val msTimer = MSTimer()

    override fun onEnable() {
        if (mode == "NoGround")
            mc.thePlayer.tryJump()
    }

    val onAttack = handler<AttackEvent> { event ->
        if (event.targetEntity is EntityLivingBase) {
            val thePlayer = mc.thePlayer ?: return@handler
            val entity = event.targetEntity

            if (!thePlayer.onGround || thePlayer.isOnLadder || thePlayer.isInWeb || thePlayer.isInLiquid ||
                thePlayer.ridingEntity != null || entity.hurtTime > hurtTime ||
                Fly.handleEvents() || !msTimer.hasTimePassed(delay)
            )
                return@handler

            val (x, y, z) = thePlayer

            when (mode.lowercase()) {
                "packet" -> {
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.0625, z, true),
                        C04PacketPlayerPosition(x, y, z, false)
                    )
                    thePlayer.onCriticalHit(entity)
                }

                "grim" -> {
                    if (!thePlayer.onGround) {
                        // If player is in air, go down a little bit
                        // Small enough to bypass simulation checks
                        sendPackets(
                            C04PacketPlayerPosition(x, y - 0.000001, z, false)
                        )
                        thePlayer.onCriticalHit(entity)
                    }
                }

                "blocksmc" -> {
                    if (thePlayer.ticksExisted % 4 == 0) {
                        sendPackets(
                            C04PacketPlayerPosition(x, y + 0.0011, z, true),
                            C04PacketPlayerPosition(x, y, z, false)
                        )
                        thePlayer.onCriticalHit(entity)
                    }
                }

                "jump" -> {
                    // Regular jump motion (0.42) copied from Minecraft vanilla
                    thePlayer.motionY = 0.42
                    thePlayer.onCriticalHit(entity)
                }

                "lowjump" -> {
                    // Smaller jump height to stay close to ground
                    thePlayer.motionY = 0.1
                    thePlayer.fallDistance = 0.1f
                    thePlayer.onGround = false
                    thePlayer.onCriticalHit(entity)
                }

                "visual" -> thePlayer.onCriticalHit(entity)
            }

            msTimer.reset()
        }
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is C03PacketPlayer && mode == "NoGround")
            packet.onGround = false
    }

    override val tag
        get() = mode
}
