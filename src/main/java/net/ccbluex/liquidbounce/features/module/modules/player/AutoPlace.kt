/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.config.*
import net.minecraft.item.ItemBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockLiquid
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import org.lwjgl.input.Mouse

object AutoPlace : Module("AutoPlace", Category.PLAYER, gameDetecting = false) {

    private val frameDelay by float("FrameDelay", 8f, 0f..30f)
    private val minPlaceDelay by int("MinPlaceDelay", 60, 25..500)
    private val disableLeft by boolean("DisableLeft", false)
    private val holdRight by boolean("HoldRight", true)
    private val fastPlaceOnJump by boolean("FastPlaceOnJump", true)
    private val pitchCheck by boolean("PitchCheck", false)
    private val silentSwing by boolean("SilentSwing", false)

    private var cachedFrameDelay = 0f
    private var lastPlace = 0L
    private var frameCount = 0
    private var lastRayTrace: MovingObjectPosition? = null
    private var lastBlockPos: BlockPos? = null

    override fun onDisable() {
        if (holdRight) {
            mc.rightClickDelayTimer = 4
        }
        resetVariables()
    }

    val onUpdate = handler<UpdateEvent> {
        val thePlayer = mc.thePlayer ?: return@handler

        if (mc.currentScreen != null || thePlayer.capabilities.isFlying)
            return@handler

        val heldItem = thePlayer.heldItem ?: return@handler
        if (heldItem.item !is ItemBlock)
            return@handler

        if (fastPlaceOnJump && holdRight && !ModuleManager["FastPlace"]!!.state && Mouse.isButtonDown(1)) {
            if (thePlayer.motionY > 0.0) {
                mc.rightClickDelayTimer = 1
            } else if (!pitchCheck || thePlayer.rotationPitch >= 70f) {
                mc.rightClickDelayTimer = 1000
            }
        }

        // Handle drawing block highlight
        if (mc.currentScreen != null || thePlayer.capabilities.isFlying)
            return@handler

        if (heldItem.item !is ItemBlock)
            return@handler

        if (disableLeft && Mouse.isButtonDown(0))
            return@handler

        val mouseOverResult = mc.objectMouseOver ?: return@handler
        if (mouseOverResult.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || 
            mouseOverResult.sideHit == EnumFacing.UP || mouseOverResult.sideHit == EnumFacing.DOWN)
            return@handler

        if (lastRayTrace != null && frameCount < frameDelay) {
            frameCount++
            return@handler
        }
        lastRayTrace = mouseOverResult

        val currentBlockPosition = mouseOverResult.blockPos

        if (lastBlockPos != null && currentBlockPosition.x == lastBlockPos!!.x && 
            currentBlockPosition.y == lastBlockPos!!.y && currentBlockPosition.z == lastBlockPos!!.z)
            return@handler

        val targetBlock = mc.theWorld.getBlockState(currentBlockPosition).block
        if (targetBlock == null || targetBlock == Blocks.air || targetBlock is BlockLiquid)
            return@handler

        if (holdRight && !Mouse.isButtonDown(1))
            return@handler

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPlace < minPlaceDelay)
            return@handler

        lastPlace = currentTime

        if (mc.playerController.onPlayerRightClick(thePlayer, mc.theWorld, heldItem, currentBlockPosition, 
            mouseOverResult.sideHit, mouseOverResult.hitVec)) {
            
            if (silentSwing) {
                sendPacket(C0APacketAnimation())
            } else {
                thePlayer.swingItem()
                mc.itemRenderer.resetEquippedProgress()
            }

            lastBlockPos = currentBlockPosition
            frameCount = 0
        }
    }

    private fun resetVariables() {
        lastBlockPos = null
        lastRayTrace = null
        frameCount = 0
    }
}
