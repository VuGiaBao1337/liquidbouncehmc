/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 * @author RtxOP
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.LiquidBounce.hud
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.network.play.server.S01PacketJoinGame
import net.minecraft.network.play.server.S32PacketConfirmTransaction
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.ccbluex.liquidbounce.utils.client.ServerUtils.remoteIp

object AnticheatDetector : Module("AnticheatDetector", Category.MISC) {
    private val debug by boolean("Debug", true)
    private val autoDisable by boolean("AutoDisableBypass", false)
    private val notifyRecommendations by boolean("ShowRecommendations", true)
    
    private val actionNumbers = mutableListOf<Int>()
    private val flagPatterns = mutableMapOf<String, Int>()
    private var check = false
    private var ticksPassed = 0
    private var detectedAnticheat: String? = null
    
    // Track position and velocity packets for additional detection
    private var positionPackets = 0
    private var velocityPackets = 0
    private var lastPositionTime = 0L
    
    val onPacket = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            is S32PacketConfirmTransaction -> {
                if (check) handleTransaction(packet.actionNumber.toInt())
            }
            is S01PacketJoinGame -> {
                reset().also { check = true }
                chat("§8[§b§lAnticheatDetector§8] §7Analyzing server anticheat...")
            }
            is S08PacketPlayerPosLook -> {
                if (check) {
                    positionPackets++
                    val now = System.currentTimeMillis()
                    if (lastPositionTime > 0 && now - lastPositionTime < 100) {
                        flagPatterns["rapid_position"] = (flagPatterns["rapid_position"] ?: 0) + 1
                    }
                    lastPositionTime = now
                }
            }
            is S12PacketEntityVelocity -> {
                if (check && packet.entityID == mc.thePlayer?.entityId) {
                    velocityPackets++
                }
            }
        }
    }
    
    val onTick = handler<GameTickEvent> {
        if (check && ticksPassed++ > 60) {
            // If we haven't detected anything after 3 seconds (60 ticks)
            if (detectedAnticheat == null) {
                analyzeSecondaryPatterns()
            } else {
                reset()
            }
        }
    }
    
    private fun handleTransaction(action: Int) {
        actionNumbers.add(action).also { if (debug) chat("§7ID: $action") }
        ticksPassed = 0
        if (actionNumbers.size >= 5) analyzeActionNumbers()
    }
    
    private fun analyzeActionNumbers() {
        val diffs = actionNumbers.windowed(2) { it[1] - it[0] }
        val first = actionNumbers.first()
        
        val anticheat = when {
            remoteIp.lowercase().contains("hypixel") -> "Watchdog"
            
            diffs.all { it == diffs.first() } -> when (diffs.first()) {
                1 -> when (first) {
                    in -23772..-23762 -> "Vulcan"
                    in 95..105, in -20005..-19995 -> "Matrix"
                    in -32773..-32762 -> "Grizzly"
                    else -> "Verus"
                }
                -1 -> when {
                    first in -8287..-8280 -> "Errata"
                    first < -3000 -> "Intave"
                    first in -5..0 -> "Grim"
                    first in -3000..-2995 -> "Karhu"
                    else -> "Polar"
                }
                else -> null
            }
            
            actionNumbers.take(2).let { it[0] == it[1] } 
                && actionNumbers.drop(2).windowed(2).all { it[1] - it[0] == 1 } 
                -> "Verus"
            
            diffs.take(2).let { it[0] >= 100 && it[1] == -1 } 
                && diffs.drop(2).all { it == -1 } 
                -> "Polar"
            
            actionNumbers.first() < -3000 && actionNumbers.any { it == 0 } 
                -> "Intave"
            
            actionNumbers.take(3) == listOf(-30767, -30766, -25767) 
                && actionNumbers.drop(3).windowed(2).all { it[1] - it[0] == 1 } 
                -> "Old Vulcan"
            
            else -> null
        }
        
        if (anticheat != null) {
            detectedAnticheat = anticheat
            notify(anticheat)
            if (notifyRecommendations) showRecommendations(anticheat)
        } else {
            if (debug) logNumbers()
        }
    }
    
    private fun analyzeSecondaryPatterns() {
        val anticheat = when {
            positionPackets > 15 && flagPatterns["rapid_position"] ?: 0 > 3 -> "NCP/AAC"
            velocityPackets > 10 && positionPackets > 8 -> "AAC"
            remoteIp.lowercase().contains("mineplex") -> "GWEN"
            remoteIp.lowercase().contains("cubecraft") -> "Sentinel"
            else -> "Unknown/Vanilla"
        }
        
        detectedAnticheat = anticheat
        notify(anticheat)
        if (notifyRecommendations) showRecommendations(anticheat)
        reset()
    }
    
    private fun showRecommendations(anticheat: String) {
        val recommendations = when (anticheat) {
            "Watchdog" -> "KillAura: Range 3.2-3.5, NoSwing: Off, Criticals: Packet"
            "Verus" -> "KillAura: Range 3.0-3.2, Velocity: 90%H 100%V, NoFall: Edit"
            "Matrix" -> "KillAura: Range 3.0, Velocity: 0%H 0%V, Speed: Matrix"
            "Vulcan" -> "KillAura: Range 3.0, Velocity: Reverse, NoFall: Packet"
            "Intave" -> "KillAura: Range 3.0, Velocity: 0%H 0%V, Speed: Custom"
            "Grim" -> "Use minimal modules, very strict detection"
            "NCP/AAC" -> "KillAura: Range 3.5-4.0, Velocity: Simple, Speed: NCP"
            else -> null
        }
        
        if (recommendations != null) {
            chat("§8[§b§lAnticheatDetector§8] §7Recommended settings for §b$anticheat§7:")
            chat("§8[§b§lAnticheatDetector§8] §7$recommendations")
            
            if (autoDisable && anticheat != "Unknown/Vanilla") {
                chat("§8[§b§lAnticheatDetector§8] §7Auto-configuring bypass settings...")
                // Here you would implement auto-configuration of other modules
            }
        }
    }

    private fun notify(message: String) {
        hud.addNotification(
            Notification.informative(this, "Anticheat detected: $message", 5000L)
        )
        chat("§8[§b§lAnticheatDetector§8] §7Detected anticheat: §b$message")
    }

    private fun logNumbers() {
        chat("§8[§b§lAnticheatDetector§8] §7Action Numbers: ${actionNumbers.joinToString()}")
        chat("§8[§b§lAnticheatDetector§8] §7Differences: ${actionNumbers.windowed(2) { it[1] - it[0] }.joinToString()}")
    }

    private fun reset() {
        actionNumbers.clear()
        flagPatterns.clear()
        positionPackets = 0
        velocityPackets = 0
        lastPositionTime = 0L
        ticksPassed = 0
        check = false
        detectedAnticheat = null
    }

    override fun onEnable() = reset()
}