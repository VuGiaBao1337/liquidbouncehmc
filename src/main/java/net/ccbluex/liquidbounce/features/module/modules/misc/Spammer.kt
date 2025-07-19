/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.LiquidBounce.CLIENT_NAME
import net.ccbluex.liquidbounce.event.async.loopSequence
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextFloat
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextInt
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.randomString

object Spammer : Module("Spammer", Category.MISC, subjective = true) {

    private val delay by intRange("Delay", 500..1000, 0..5000)

    private val message by text("Message", "RINBOUNCE ClIENT ON TOP")

    private val custom by boolean("Custom", false)

    // Enhanced spam options
    private val randomizeDelay by boolean("RandomizeDelay", true)
    private val delayVariation by int("DelayVariation", 200, 0..1000) { randomizeDelay }
    private val enableSpamProtection by boolean("SpamProtection", true)
    private val maxMessagesPerMinute by int("MaxMessagesPerMinute", 30, 1..100) { enableSpamProtection }

    // Message tracking for spam protection
    private var messageCount = 0
    private var lastMinuteReset = System.currentTimeMillis()

    val onUpdate = loopSequence {
        val player = mc.thePlayer
        val world = mc.theWorld

        // Enhanced null safety checks
        if (player == null || world == null || !player.sendQueue.networkManager.isChannelOpen) {
            delay(1000) // Wait longer if not connected
            return@loopSequence
        }

        // Spam protection check
        if (enableSpamProtection) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastMinuteReset > 60000) {
                messageCount = 0
                lastMinuteReset = currentTime
            }

            if (messageCount >= maxMessagesPerMinute) {
                delay(5000) // Wait 5 seconds if spam limit reached
                return@loopSequence
            }
        }

        try {
            val finalMessage = if (custom) replace(message) else message + " >" + randomString(nextInt(5, 11)) + "<"
            player.sendChatMessage(finalMessage)

            if (enableSpamProtection) {
                messageCount++
            }
        } catch (e: Exception) {
            // Handle any chat sending errors gracefully
            delay(2000)
            return@loopSequence
        }

        // Enhanced delay calculation
        val baseDelay = delay.random().toLong()
        val finalDelay = if (randomizeDelay) {
            val variation = nextInt(-delayVariation, delayVariation + 1)
            (baseDelay + variation).coerceAtLeast(100) // Minimum 100ms delay
        } else {
            baseDelay
        }

        delay(finalDelay)
    }

    private fun replace(text: String): String {
        var replacedStr = text

        replaceMap.forEach { (key, valueFunc) ->
            replacedStr = replacedStr.replace(key, valueFunc)
        }

        return replacedStr
    }

    private inline fun String.replace(oldValue: String, newValueProvider: () -> Any): String {
        var index = 0
        val newString = StringBuilder(this)
        while (true) {
            index = newString.indexOf(oldValue, startIndex = index)
            if (index == -1) {
                break
            }

            // You have to replace them one by one, otherwise all parameters like %s would be set to the same random string.
            val newValue = newValueProvider().toString()
            newString.replace(index, index + oldValue.length, newValue)

            index += newValue.length
        }
        return newString.toString()
    }

    private fun randomPlayer() =
        mc.netHandler.playerInfoMap
            .map { playerInfo -> playerInfo.gameProfile.name }
            .filter { name -> name != mc.thePlayer.name }
            .randomOrNull() ?: "none"

    private val replaceMap = mapOf(
        "%f" to { nextFloat().toString() },
        "%i" to { nextInt(0, 10000).toString() },
        "%ss" to { randomString(nextInt(1, 6)) },
        "%s" to { randomString(nextInt(1, 10)) },
        "%ls" to { randomString(nextInt(1, 17)) },
        "%p" to { randomPlayer() }
    )
}
