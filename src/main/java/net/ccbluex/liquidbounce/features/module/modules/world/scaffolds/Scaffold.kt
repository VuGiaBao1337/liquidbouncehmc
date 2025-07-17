/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.features.module.modules.world.scaffolds

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.event.async.loopSequence
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.attack.CPSCounter
import net.ccbluex.liquidbounce.utils.block.*
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.blocksAmount
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.hotBarSlot
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils
import net.ccbluex.liquidbounce.utils.movement.MovementUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.rotation.PlaceRotation
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationSettingsWithRotationModes
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.canUpdateRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.getFixedAngleDelta
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.getVectorForRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.rotationDifference
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.toRotation
import net.ccbluex.liquidbounce.utils.simulation.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.timing.*
import net.minecraft.block.BlockBush
import net.minecraft.client.settings.GameSettings
import net.minecraft.init.Blocks.air
import net.minecraft.util.BlockPos
import net.minecraft.init.Blocks
import kotlin.math.roundToInt
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.util.*
import net.minecraft.util.MovingObjectPosition.MovingObjectType
import net.minecraft.world.WorldSettings
import net.minecraftforge.event.ForgeEventFactory
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.*

object Scaffold : Module("Scaffold", Category.WORLD, Keyboard.KEY_I) {

    /**
     * TOWER MODES & SETTINGS
     */

    // -->

    private val towerMode by Tower.towerModeValues

    init {
        addValues(Tower.values)
    }

    // <--

    /**
     * SCAFFOLD MODES & SETTINGS
     */

    // -->

    val scaffoldMode by choices(
        "ScaffoldMode", arrayOf("Normal", "Rewinside", "Expand", "Telly", "GodBridge", "Breezily"), "Normal"
    )

    // GodBridge specific settings
    private val godBridgeMode by choices("GodBridgeMode", arrayOf("Advanced", "Safe", "Fast"), "Advanced") { scaffoldMode == "GodBridge" }
    private val safetyCheck by boolean("SafetyCheck", true) { scaffoldMode == "GodBridge" }
    private val autoCenter by boolean("AutoCenter", true) { scaffoldMode == "GodBridge" }
    private val adaptiveSpeed by boolean("AdaptiveSpeed", true) { scaffoldMode == "GodBridge" }
    private val smartPitch by boolean("SmartPitch", true) { scaffoldMode == "GodBridge" }

    // HMCBlinkFly
    private val hmcBlinkFlyEnabled by boolean("HMCBlinkFly", false)
    private val hmcBlinkVisibleLimit by int("HMCBlinkVisibleBlocks", 4, 0..10) { hmcBlinkFlyEnabled }
    private var hmcBlinkPlacedCount = 0

    // Expand
    private val omniDirectionalExpand by boolean("OmniDirectionalExpand", false) { scaffoldMode == "Expand" }
    private val expandLength by int("ExpandLength", 1, 1..6) { scaffoldMode == "Expand" }

    // Placeable delay
    private val placeDelayValue = boolean("PlaceDelay", true) { scaffoldMode != "GodBridge" }
    private val delay by intRange("Delay", 0..0, 0..1000) { placeDelayValue.isActive() }

    // Extra clicks
    private val extraClicks by boolean("DoExtraClicks", false)
    private val simulateDoubleClicking by boolean("SimulateDoubleClicking", false) { extraClicks }
    private val extraClickCPS by intRange("ExtraClickCPS", 3..7, 0..50) { extraClicks }
    private val placementAttempt by choices(
        "PlacementAttempt", arrayOf("Fail", "Independent"), "Fail"
    ) { extraClicks }

    // Autoblock
    private val autoBlock by choices("AutoBlock", arrayOf("Off", "Pick", "Spoof", "Switch"), "Spoof")
    val sprint by boolean("Sprint", false)
    private val sortByHighestAmount by boolean("SortByHighestAmount", false) { autoBlock != "Off" }
    private val earlySwitch by boolean("EarlySwitch", false) { autoBlock != "Off" && !sortByHighestAmount }
    private val amountBeforeSwitch by int(
        "SlotAmountBeforeSwitch", 3, 1..10
    ) { earlySwitch && !sortByHighestAmount }

    // Settings
    private val autoF5 by boolean("AutoF5", false).subjective()
    private var breezilyState = false

    // Basic stuff
    private val swing by boolean("Swing", true).subjective()
    private val down by boolean("Down", true) { !sameY && scaffoldMode !in arrayOf("GodBridge", "Telly") }
    private val autoJump by boolean("AutoJump", false)
    private val jumpYWhenUserInput by boolean("JumpYWhenUserInput", false) { sameY }

    private val ticksUntilRotation by intRange("TicksUntilRotation", 3..3, 1..8) {
        scaffoldMode == "Telly"
    }

    // GodBridge mode sub-values
    private val waitForRots by boolean("WaitForRotations", false) { isGodBridgeEnabled }
    private val useOptimizedPitch by boolean("UseOptimizedPitch", false) { isGodBridgeEnabled }
    private val dynamicPitch by boolean("DynamicPitch", false) { isGodBridgeEnabled }
    private val customGodPitch by float(
        "GodBridgePitch", 73.5f, 0f..90f
    ) { isGodBridgeEnabled && !useOptimizedPitch }
    private val stabilizeSpeed by boolean("StabilizeSpeed", true) { isGodBridgeEnabled }
    private val autoAdjust by boolean("AutoAdjust", true) { isGodBridgeEnabled }

    val jumpAutomatically by boolean("JumpAutomatically", true) { scaffoldMode == "GodBridge" }
    private val blocksToJumpRange by intRange("BlocksToJumpRange", 4..4, 1..8) {  scaffoldMode == "GodBridge" && !jumpAutomatically }

    // Telly mode sub-values
    private val startHorizontally by boolean("StartHorizontally", true) { scaffoldMode == "Telly" }
    private val horizontalPlacementsRange by intRange("HorizontalPlacementsRange", 1..1, 1..10) { scaffoldMode == "Telly" }
    private val verticalPlacementsRange by intRange("VerticalPlacementsRange", 1..1, 1..10) { scaffoldMode == "Telly" }

    private val jumpTicksRange by intRange("JumpTicksRange", 0..0, 0..10) { scaffoldMode == "Telly" }

    private val allowClutching by boolean("AllowClutching", true) { scaffoldMode !in arrayOf("Telly", "Expand") }
    private val horizontalClutchBlocks by int("HorizontalClutchBlocks", 3, 1..5) {
        allowClutching && scaffoldMode !in arrayOf("Telly", "Expand")
    }
    private val verticalClutchBlocks by int("VerticalClutchBlocks", 2, 1..3) {
        allowClutching && scaffoldMode !in arrayOf("Telly", "Expand")
    }
    private val blockSafe by boolean("BlockSafe", false) { !isGodBridgeEnabled }

    // Eagle
    private val eagleValue =
        choices("Eagle", arrayOf("Normal", "Silent", "Legit", "Off"), "Normal") { scaffoldMode != "GodBridge" }
    val eagle by eagleValue
    private val eagleMode by choices("EagleMode", arrayOf("Both", "OnGround", "InAir", "Smart"), "Smart")
    { eagle != "Off" && scaffoldMode != "GodBridge" }
    private val eagleSneakTiming by floatRange("EagleSneakTiming", 0.1f..0.3f, 0.1f..1.0f)
    { eagle == "Legit" && scaffoldMode != "GodBridge" }
    private val eagleSmartPrediction by int("EagleSmartPrediction", 2, 1..5)
    { eagleMode == "Smart" && eagle != "Off" && scaffoldMode != "GodBridge" }
    private val eagleRandomization by boolean("EagleRandomization", true)
    { eagle == "Legit" && scaffoldMode != "GodBridge" }
    private val adjustedSneakSpeed by boolean("AdjustedSneakSpeed", true)
    { eagle == "Silent" && scaffoldMode != "GodBridge" }
    private val eagleSpeed by float("EagleSpeed", 0.3f, 0.3f..1.0f) { eagle != "Off" && scaffoldMode != "GodBridge" }
    val eagleSprint by boolean("EagleSprint", false) { eagle == "Normal" && scaffoldMode != "GodBridge" }
    private val blocksToEagle by intRange("BlocksToEagle", 0..0, 0..10) { eagle != "Off" && scaffoldMode != "GodBridge" }
    private val edgeDistance by float("EagleEdgeDistance", 0f, 0f..0.5f)
    { eagle != "Off" && scaffoldMode != "GodBridge" }
    private val useMaxSneakTime by boolean("UseMaxSneakTime", true) { eagle != "Off" && scaffoldMode != "GodBridge" }
    private val maxSneakTicks by intRange("MaxSneakTicks", 3..3, 0..10) { useMaxSneakTime }
    private val blockSneakingAgainUntilOnGround by boolean("BlockSneakingAgainUntilOnGround", true)
    { useMaxSneakTime && eagleMode != "OnGround" }

    // Rotation Options
    private val rotationMode =
        choices("Rotations", arrayOf("Off", "Normal", "Stabilized", "ReverseYaw", "GodBridge"), "Normal")

    private val options = RotationSettingsWithRotationModes(this, rotationMode).apply {
        strictValue.excludeWithState()
        resetTicksValue.setSupport { it && scaffoldMode != "Telly" }
    }

    // Search options
    val searchMode by choices("SearchMode", arrayOf("Area", "Center"), "Area") { scaffoldMode != "GodBridge" }
    private val minDist by float("MinDist", 0f, 0f..0.2f) { scaffoldMode !in arrayOf("GodBridge", "Telly") }

    // Zitter
    private val zitterMode by choices("Zitter", arrayOf("Off", "Teleport", "Smooth"), "Off")
    private val zitterSpeed by float("ZitterSpeed", 0.13f, 0.1f..0.3f) { zitterMode == "Teleport" }
    private val zitterStrength by float("ZitterStrength", 0.05f, 0f..0.2f) { zitterMode == "Teleport" }
    private val zitterTicks by intRange("ZitterTicks", 2..3, 0..6) { zitterMode == "Smooth" }

    private val useSneakMidAir by boolean("UseSneakMidAir", false) { zitterMode == "Smooth" }

    // Game
    val timer by float("Timer", 1f, 0.1f..10f)
    private val speedModifier by float("SpeedModifier", 1f, 0f..2f)
    private val speedLimiter by boolean("SpeedLimiter", false) { !slow }
    private val speedLimit by float("SpeedLimit", 0.11f, 0.01f..0.12f) { !slow && speedLimiter }
    private val slow by boolean("Slow", false)
    private val slowGround by boolean("SlowOnlyGround", false) { slow }
    private val slowSpeed by float("SlowSpeed", 0.6f, 0.2f..0.8f) { slow }

    // Jump Strafe
    private val jumpStrafe by boolean("JumpStrafe", false)
    private val jumpStraightStrafe by floatRange("JumpStraightStrafe", 0.4f..0.45f, 0.1f..1f) { jumpStrafe }
    private val jumpDiagonalStrafe by floatRange("JumpDiagonalStrafe", 0.4f..0.45f, 0.1f..1f) { jumpStrafe }

    // Safety
    private val sameY by boolean("SameY", false) { scaffoldMode != "GodBridge" }
    private val jumpOnUserInput by boolean("JumpOnUserInput", true) { sameY && scaffoldMode != "GodBridge" }

    private val safeWalkValue = boolean("SafeWalk", true) { scaffoldMode != "GodBridge" }
    private val airSafe by boolean("AirSafe", false) { safeWalkValue.isActive() }

    // Visuals
    private val mark by boolean("Mark", false).subjective()
    private val trackCPS by boolean("TrackCPS", false).subjective()

    // Target placement
    var placeRotation: PlaceRotation? = null

    // Launch position
    private var launchY = -999

    val shouldJumpOnInput
        get() = !jumpOnUserInput || !mc.gameSettings.keyBindJump.isKeyDown && mc.thePlayer.posY >= launchY && !mc.thePlayer.onGround

    private val shouldKeepLaunchPosition
        get() = sameY && shouldJumpOnInput && scaffoldMode != "GodBridge"

    // Zitter
    private var zitterDirection = false

    // Delay
    private val delayTimer = object : DelayTimer(delay.first, delay.last, MSTimer()) {
        override fun hasTimePassed() = !placeDelayValue.isActive() || super.hasTimePassed()
    }

    private val zitterTickTimer = TickDelayTimer(zitterTicks.first, zitterTicks.last)

    // Eagle
    private var placedBlocksWithoutEagle = 0
    private var lastEagleTime = 0L
    private var eagleSneakStartTime = 0L
    private var eagleRandomDelay = 0f

    var eagleSneaking = false
    private var requestedStopSneak = false
    private var predictedEdgeDanger = false

    private val isEagleEnabled
        get() = eagle != "Off" && !shouldGoDown && scaffoldMode != "GodBridge"

    private fun checkSmartEdge(): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false

        val yaw = player.rotationYaw
        val x = -sin(yaw.toRadians()).toDouble()
        val z = cos(yaw.toRadians()).toDouble()

        for (i in 1..eagleSmartPrediction) {
            val pos = BlockPos(
                player.posX + x * i,
                player.posY - 1,
                player.posZ + z * i
            )
            if (world.isAirBlock(pos)) return true
        }
        return false
    }

    private fun shouldLegitSneak(): Boolean {
        val currentTime = System.currentTimeMillis()

        if (eagleSneaking) {
            val sneakDuration = currentTime - eagleSneakStartTime
            if (sneakDuration >= (eagleSneakTiming.random() * 1000)) {
                eagleRandomDelay = eagleSneakTiming.random()
                return false
            }
            return true
        }

        if (currentTime - lastEagleTime <= (eagleRandomDelay * 1000)) {
            return false
        }

        eagleSneakStartTime = currentTime
        lastEagleTime = currentTime
        return true
    }

    // Downwards
    val shouldGoDown
        get() = down && !sameY && GameSettings.isKeyDown(mc.gameSettings.keyBindSneak) && scaffoldMode !in arrayOf(
            "GodBridge", "Telly"
        ) && blocksAmount() > 1

    // Current rotation
    private val currRotation
        get() = RotationUtils.currentRotation ?: mc.thePlayer.rotation

    // Extra clicks
    private var extraClick = ExtraClickInfo(TimeUtils.randomClickDelay(extraClickCPS.first, extraClickCPS.last), 0L, 0)

    // GodBridge
    private var blocksPlacedUntilJump = 0

    private val isManualJumpOptionActive
        get() = scaffoldMode == "GodBridge" && !jumpAutomatically

    private var blocksToJump = blocksToJumpRange.random()

    private val isGodBridgeEnabled
        get() = scaffoldMode == "GodBridge" || scaffoldMode == "Normal" && options.rotationMode == "GodBridge"

    private fun checkVoidDanger(): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false

        // Check blocks below in the movement direction
        val yaw = player.rotationYaw
        val x = -sin(yaw.toRadians()).toDouble()
        val z = cos(yaw.toRadians()).toDouble()

        var dangerBlocks = 0
        for (i in 1..5) {
            val checkPos = BlockPos(
                player.posX + x * i,
                player.posY - 1,
                player.posZ + z * i
            )
            if (world.isAirBlock(checkPos)) dangerBlocks++
            if (dangerBlocks > 2) return true
        }
        return false
    }

    private fun checkRotationSafety(rotation: Rotation): Boolean {
        val player = mc.thePlayer ?: return false
        val world = mc.theWorld ?: return false

        val eyePos = player.getPositionEyes(1f)
        val lookVec = getVectorForRotation(rotation)
        val reachVec = eyePos.addVector(
            lookVec.xCoord * 4.5,
            lookVec.yCoord * 4.5,
            lookVec.zCoord * 4.5
        )

        val raytrace = world.rayTraceBlocks(eyePos, reachVec, false, true, false)
        return raytrace != null && raytrace.typeOfHit == MovingObjectType.BLOCK // MovingObjectType.BLOCK represents hitting a block
    }



    private var godBridgeTargetRotation: Rotation? = null
    private val isLookingDiagonally: Boolean
        get() {
            val player = mc.thePlayer ?: return false

            val directionDegree = MovementUtils.direction.toDegreesF()

            // Round the direction rotation to the nearest multiple of 45 degrees
            val yaw = round(abs(MathHelper.wrapAngleTo180_float(directionDegree)) / 45f) * 45f

            val isYawDiagonal = yaw % 90 != 0f
            val isMovingDiagonal = player.movementInput.moveForward != 0f && player.movementInput.moveStrafe == 0f
            val isStrafing = mc.gameSettings.keyBindRight.isKeyDown || mc.gameSettings.keyBindLeft.isKeyDown

            return isYawDiagonal && (isMovingDiagonal || isStrafing)
        }

    /**
     * Checks if the player is in a safe position for god bridging
     */
    private fun isPlayerSafeForGodBridge(): Boolean {
        val player = mc.thePlayer ?: return false
        
        if (blocksAmount() < 2) return false
        
        if (!player.onGround) return false
        
        val pos = player.position
        val world = mc.theWorld ?: return false
        
        val blockBelow = world.getBlockState(pos.down())
        if (blockBelow.block == air) return false
        
        return true
    }

    /**
     * Calculate optimal side for god bridging based on position and movement
     */
    private fun calculateGodBridgeSide(movingYaw: Float): Float {
        val player = mc.thePlayer ?: return 0f
        
        if (!player.onGround) return if (isOnRightSide) 45f else -45f
        
        val posX = -sin(movingYaw.toRadians()).toDouble()
        val posZ = cos(movingYaw.toRadians()).toDouble()
        
        val shouldSwitchSide = run {
            val nextPos = BlockPos(
                player.posX + posX * 0.6,
                player.posY - 1.0,
                player.posZ + posZ * 0.6
            )
            
            val currentBlock = mc.theWorld.getBlockState(player.position.down()).block == air
            val nextBlock = mc.theWorld.getBlockState(nextPos).block == air
            
            currentBlock && nextBlock
        }
        
        if (shouldSwitchSide) isOnRightSide = !isOnRightSide
        
        return if (isOnRightSide) 45f else -45f
    }

    // Telly
    private var ticksUntilJump = 0
    private var blocksUntilAxisChange = 0
    private var jumpTicks = jumpTicksRange.random()
    private var horizontalPlacements = horizontalPlacementsRange.random()
    private var verticalPlacements = verticalPlacementsRange.random()
    private val shouldPlaceHorizontally
        get() = scaffoldMode == "Telly" && mc.thePlayer.isMoving && (startHorizontally && blocksUntilAxisChange <= horizontalPlacements || !startHorizontally && blocksUntilAxisChange > verticalPlacements)

    // <--

    // Enabling module
    override fun onEnable() {
        hmcBlinkPlacedCount = 0
        val player = mc.thePlayer ?: return

        launchY = player.posY.roundToInt()
        blocksUntilAxisChange = 0
    }

    // Events
    val onUpdate = loopSequence {
        val player = mc.thePlayer ?: return@loopSequence

        if (mc.playerController.currentGameType == WorldSettings.GameType.SPECTATOR) return@loopSequence

        mc.timer.timerSpeed = timer

        // Telly
        if (player.onGround) ticksUntilJump++

        if (shouldGoDown) {
            mc.gameSettings.keyBindSneak.pressed = false
        }

        if (autoJump && player.onGround && player.isMoving) {
            player.jump()
        }

        if (sprint && player.isMoving && !player.isSprinting) {
            player.isSprinting = true
        }

        if (slow) {
            if (!slowGround || slowGround && player.onGround) {
                player.motionX *= slowSpeed
                player.motionZ *= slowSpeed
            }
        }

        // Eagle
        if (isEagleEnabled) {
            var dif = 0.5
            val blockPos = BlockPos(player).down()

            for (side in EnumFacing.entries) {
                if (side.axis == EnumFacing.Axis.Y) {
                    continue
                }

                val neighbor = blockPos.offset(side)

                if (neighbor.isReplaceable) {
                    val calcDif = (if (side.axis == EnumFacing.Axis.Z) {
                        abs(neighbor.z + 0.5 - player.posZ)
                    } else {
                        abs(neighbor.x + 0.5 - player.posX)
                    }) - 0.5

                    if (calcDif < dif) {
                        dif = calcDif
                    }
                }
            }

            val blockSneaking = WaitTickUtils.hasScheduled("block")
            val alreadySneaking = WaitTickUtils.hasScheduled("sneak")

            val options = mc.gameSettings

            run {
                if (placedBlocksWithoutEagle < blocksToEagle.random() && !alreadySneaking && !blockSneaking && !eagleSneaking && !requestedStopSneak) {
                    return@run
                }

                val eagleCondition = when (eagleMode) {
                    "OnGround" -> player.onGround
                    "InAir" -> !player.onGround
                    "Smart" -> {
                        val shouldCheck = player.onGround || (!player.onGround && player.motionY < 0)
                        if (shouldCheck) {
                            predictedEdgeDanger = checkSmartEdge()
                        }
                        predictedEdgeDanger
                    }
                    else -> true
                }

                // For better sneak support we could move this to MovementInputEvent
                val pressedOnKeyboard = Keyboard.isKeyDown(options.keyBindSneak.keyCode)

                val isLegitMode = eagle == "Legit"

                var shouldEagle = if (isLegitMode) {
                    if (eagleCondition && (blockPos.isReplaceable || dif < edgeDistance)) {
                        shouldLegitSneak()
                    } else false
                } else {
                    eagleCondition && (blockPos.isReplaceable || dif < edgeDistance) || pressedOnKeyboard
                }

                if (eagleRandomization && isLegitMode && shouldEagle) {
                    shouldEagle = shouldEagle && Math.random() > 0.1 // 10% chance to skip sneaking for more natural feel
                }

                val shouldSchedule = !requestedStopSneak

                if (requestedStopSneak) {
                    requestedStopSneak = false

                    if (!player.onGround) {
                        shouldEagle = pressedOnKeyboard
                    }
                } else if (blockSneaking || alreadySneaking) {
                    return@run
                }

                if (eagle == "Silent") {
                    if (eagleSneaking != shouldEagle) {
                        sendPacket(
                            C0BPacketEntityAction(
                                player, if (shouldEagle) {
                                    C0BPacketEntityAction.Action.START_SNEAKING
                                } else {
                                    C0BPacketEntityAction.Action.STOP_SNEAKING
                                }
                            )
                        )

                        // Adjust speed when silent sneaking
                        if (adjustedSneakSpeed && shouldEagle) {
                            player.motionX *= eagleSpeed
                            player.motionZ *= eagleSpeed
                        }
                    }

                    eagleSneaking = shouldEagle
                } else {
                    options.keyBindSneak.pressed = shouldEagle
                    eagleSneaking = shouldEagle
                }

                if (eagleSneaking && shouldSchedule) {
                    if (useMaxSneakTime) {
                        WaitTickUtils.conditionalSchedule("sneak") { elapsed ->
                            (elapsed >= maxSneakTicks.random() + 1).also { requestedStopSneak = it }
                        }
                    }

                    if (blockSneakingAgainUntilOnGround && !player.onGround) {
                        WaitTickUtils.conditionalSchedule("block") {
                            mc.thePlayer?.onGround.also { if (it != false) requestedStopSneak = true } ?: true
                        }
                    }
                }

                placedBlocksWithoutEagle = 0
            }
        }

        if (player.onGround) {
            // Still a thing?
            if (scaffoldMode == "Rewinside") {
                MovementUtils.strafe(0.2F)
                player.motionY = 0.0
            }
        }
    }

    val onHMCBlinkPacket = handler<PacketEvent> { event ->
        if (!hmcBlinkFlyEnabled) return@handler

        val packet = event.packet
        val player = mc.thePlayer ?: return@handler

        if (player.isDead) return@handler

        if (event.eventType == EventState.SEND) {
            when (packet) {
                is C03PacketPlayer -> {
                    net.ccbluex.liquidbounce.utils.client.BlinkUtils.blink(packet, event, true, false)
                }
                is C08PacketPlayerBlockPlacement -> {
                    if (hmcBlinkPlacedCount < hmcBlinkVisibleLimit) {
                        hmcBlinkPlacedCount++
                    } else {
                        net.ccbluex.liquidbounce.utils.client.BlinkUtils.blink(packet, event, true, false)
                    }
                }
            }
        }

        if (event.eventType == EventState.RECEIVE) {
            if (isServerPacket(packet) && !isEntityMovementPacket(packet)) {
                net.ccbluex.liquidbounce.utils.client.BlinkUtils.blink(packet, event, false, true)
            }
        }
    }

    val onHMCBlinkMotion = handler<MotionEvent> { event ->
        if (!hmcBlinkFlyEnabled) return@handler
        val player = mc.thePlayer ?: return@handler
        if (event.eventState == EventState.POST) {
            if (player.isDead || player.ticksExisted <= 10) {
                net.ccbluex.liquidbounce.utils.client.BlinkUtils.unblink()
            } else {
                net.ccbluex.liquidbounce.utils.client.BlinkUtils.syncReceived()
            }
        }
    }

    val onHMCBlinkRender3D = handler<Render3DEvent> {
        if (!hmcBlinkFlyEnabled) return@handler

        val positions = net.ccbluex.liquidbounce.utils.client.BlinkUtils.positions
        val color = Color(150, 200, 255, 180)

        synchronized(positions) {
            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            mc.entityRenderer.disableLightmap()
            GL11.glBegin(GL11.GL_LINE_STRIP)
            RenderUtils.glColor(color)

            val renderPosX = mc.renderManager.renderPosX
            val renderPosY = mc.renderManager.renderPosY
            val renderPosZ = mc.renderManager.renderPosZ

            for (vec in positions) {
                GL11.glVertex3d(
                    vec.xCoord - renderPosX,
                    vec.yCoord - renderPosY,
                    vec.zCoord - renderPosZ
                )
            }

            GL11.glEnd()
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)
            GL11.glDisable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glPopMatrix()
        }
    }

    val onStrafe = handler<StrafeEvent> {
        val player = mc.thePlayer ?: return@handler

        // Jumping needs to be done here, so it doesn't get detected by movement-sensitive anti-cheats.
        if (scaffoldMode == "Telly" && player.onGround && player.isMoving && currRotation == player.rotation && ticksUntilJump >= jumpTicks) {
            player.tryJump()

            ticksUntilJump = 0
            jumpTicks = jumpTicksRange.random()
        }
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        val player = mc.thePlayer ?: return@handler
    // breezily
        if (scaffoldMode == "Breezily") {
             val camYaw = player.rotationYaw
             val camPitch = player.rotationPitch
            val oldPlayerRot = Rotation(camYaw, camPitch)
            val rpitch = if (((camYaw / 45).roundToInt()) % 2 == 0) 79.6f else 76.3f
            val playerRot = Rotation(camYaw + 180f, rpitch)
            val settings = RotationSettingsWithRotationModes(this@Scaffold, rotationMode).apply {
                horizontalAngleChangeValue.set(60f..60f)
                verticalAngleChangeValue.set(60f..60f)
            }
            val lockRotation = RotationUtils.limitAngleChange(oldPlayerRot, playerRot, settings)
            setTargetRotation(lockRotation, settings, 1)

            val breezilySettings = RotationSettingsWithRotationModes(this@Scaffold, rotationMode).apply {
                horizontalAngleChangeValue.set(60f..60f)
                verticalAngleChangeValue.set(60f..60f)
            }

            val blockBelow = mc.theWorld.getBlockState(BlockPos(player.posX, player.posY - 1.0, player.posZ)).block
            if (blockBelow == net.minecraft.init.Blocks.air && ((camYaw / 45).roundToInt()) % 2 == 0) {
                breezilyState = !breezilyState
                mc.gameSettings.keyBindRight.pressed = breezilyState
                mc.gameSettings.keyBindLeft.pressed = !breezilyState
            } else {
                mc.gameSettings.keyBindRight.pressed = false
                mc.gameSettings.keyBindLeft.pressed = false
            }
            return@handler
        }

        if (player.ticksExisted == 1) {
            launchY = player.posY.roundToInt()
        }

        val rotation = RotationUtils.currentRotation

        update()

        val ticks = if (options.keepRotation) {
            if (scaffoldMode == "Telly") 1 else options.resetTicks
        } else {
            if (isGodBridgeEnabled) options.resetTicks else RotationUtils.resetTicks
        }

        if (!Tower.isTowering && isGodBridgeEnabled && options.rotationsActive) {
            generateGodBridgeRotations(ticks)
            return@handler
        }

        if (options.rotationsActive && rotation != null) {
            val placeRotation = this.placeRotation?.rotation ?: rotation

            if (RotationUtils.resetTicks != 0 || options.keepRotation) {
                setRotation(placeRotation, ticks)
            }
        }
    }

    val onTick = handler<GameTickEvent> {
        val target = placeRotation?.placeInfo

        val raycastProperly = !(scaffoldMode == "Expand" && expandLength > 1 || shouldGoDown) && options.rotationsActive

        /**
         * Calculate block raytracing process once to simulate proper vanilla ray-cast update logic.
         *
         * @see net.minecraft.client.Minecraft.runTick Line 1345
         */
        val raycast = performBlockRaytrace(currRotation, mc.playerController.blockReachDistance)

        var alreadyPlaced = false

        if (extraClicks) {
            val doubleClick = if (simulateDoubleClicking) RandomUtils.nextInt(-1, 1) else 0

            val clicks = extraClick.clicks + doubleClick

            repeat(clicks) {
                extraClick.clicks--

                doPlaceAttempt(raycast, it + 1 == clicks) { alreadyPlaced = true }
            }
        }

        if (target == null) {
            if (placeDelayValue.isActive()) {
                delayTimer.reset()
            }
            return@handler
        }

        // Change/Schedule slot once per tick according to vanilla-logic
        if (alreadyPlaced || SilentHotbar.modifiedThisTick) {
            return@handler
        }

        raycast.let {
            if (!options.rotationsActive || it != null && it.blockPos == target.blockPos && (!raycastProperly || it.sideHit == target.enumFacing)) {
                val result = if (raycastProperly && it != null) {
                    PlaceInfo(it.blockPos, it.sideHit, it.hitVec)
                } else {
                    target
                }

                place(result)
            }
        }
    }

    val onSneakSlowDown = handler<SneakSlowDownEvent> { event ->
        if (!isEagleEnabled || eagle != "Normal") {
            return@handler
        }

        event.forward *= eagleSpeed / 0.3f
        event.strafe *= eagleSpeed / 0.3f
    }

    val onMovementInput = handler<MovementInputEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (!isGodBridgeEnabled || !player.onGround) return@handler

        if (waitForRots) {
            godBridgeTargetRotation?.run {
                event.originalInput.sneak =
                    event.originalInput.sneak || rotationDifference(this, currRotation) > getFixedAngleDelta()
            }
        }

        val simPlayer = SimulatedPlayer.fromClientPlayer(RotationUtils.modifiedInput)

        simPlayer.rotationYaw = currRotation.yaw

        simPlayer.tick()

        if (!simPlayer.onGround && !isManualJumpOptionActive || blocksPlacedUntilJump > blocksToJump) {
            event.originalInput.jump = true

            blocksPlacedUntilJump = 0

            blocksToJump = blocksToJumpRange.random()
        }
    }

    fun update() {
        val player = mc.thePlayer ?: return
        val holdingItem = player.heldItem?.item is ItemBlock

        if (!holdingItem && (autoBlock == "Off" || InventoryUtils.findBlockInHotbar() == null)) {
            return
        }

        findBlock(scaffoldMode == "Expand" && expandLength > 1, searchMode == "Area")
    }

    private fun setRotation(rotation: Rotation, ticks: Int) {
        val player = mc.thePlayer ?: return

        if (scaffoldMode == "Telly" && player.isMoving) {
            if (player.airTicks < ticksUntilRotation.random() && ticksUntilJump >= jumpTicks) {
                return
            }
        }

        setTargetRotation(rotation, options, ticks)
    }

    // Search for new target block
    private fun findBlock(expand: Boolean, area: Boolean) {
        val player = mc.thePlayer ?: return

        if (!shouldKeepLaunchPosition) launchY = player.posY.roundToInt()

        val blockPosition = if (shouldGoDown) {
            if (player.posY == player.posY.roundToInt() + 0.5) {
                BlockPos(player.posX, player.posY - 0.6, player.posZ)
            } else {
                BlockPos(player.posX, player.posY - 0.6, player.posZ).down()
            }
        } else if (shouldKeepLaunchPosition && launchY <= player.posY) {
            val baseY = launchY - 1.0
            val finalY = if (jumpYWhenUserInput && mc.gameSettings.keyBindJump.isKeyDown) baseY + 1 else baseY
            BlockPos(player.posX, finalY, player.posZ)
        } else if (player.posY == player.posY.roundToInt() + 0.5) {
            BlockPos(player)
        } else {
            BlockPos(player).down()
        }

        if (!expand && (!blockPosition.isReplaceable || search(
                blockPosition, !shouldGoDown, area, shouldPlaceHorizontally
            ))
        ) {
            return
        }

        if (expand) {
            val yaw = player.rotationYaw.toRadiansD()
            val x = if (omniDirectionalExpand) -sin(yaw).roundToInt() else player.horizontalFacing.directionVec.x
            val z = if (omniDirectionalExpand) cos(yaw).roundToInt() else player.horizontalFacing.directionVec.z

            repeat(expandLength) {
                if (search(blockPosition.add(x * it, 0, z * it), false, area)) return
            }
            return
        }

        val (horizontal, vertical) = if (scaffoldMode == "Telly") {
            5 to 3
        } else if (allowClutching) {
            horizontalClutchBlocks to verticalClutchBlocks
        } else {
            1 to 1
        }

        BlockPos.getAllInBox(
            blockPosition.add(-horizontal, 0, -horizontal), blockPosition.add(horizontal, -vertical, horizontal)
        ).sortedBy {
            BlockUtils.getCenterDistance(it)
        }.forEach {
            if (it.canBeClicked() || search(it, !shouldGoDown, area, shouldPlaceHorizontally)) {
                return
            }
        }
    }

    private fun place(placeInfo: PlaceInfo) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        if (!delayTimer.hasTimePassed() || shouldKeepLaunchPosition && launchY - 1 != placeInfo.vec3.yCoord.toInt() && scaffoldMode != "Expand") return

        val currentSlot = SilentHotbar.currentSlot

        var stack = player.hotBarSlot(currentSlot).stack

        //TODO: blacklist more blocks than only bushes
        if (stack == null || stack.item !is ItemBlock || (stack.item as ItemBlock).block is BlockBush || stack.stackSize <= 0 || sortByHighestAmount || earlySwitch) {
            val blockSlot = if (sortByHighestAmount) {
                InventoryUtils.findLargestBlockStackInHotbar() ?: return
            } else if (earlySwitch) {
                InventoryUtils.findBlockStackInHotbarGreaterThan(amountBeforeSwitch)
                    ?: InventoryUtils.findBlockInHotbar() ?: return
            } else {
                InventoryUtils.findBlockInHotbar() ?: return
            }

            stack = player.hotBarSlot(blockSlot).stack

            // Check if block is placeable on target side before switching slots
            if ((stack.item as? ItemBlock)?.canPlaceBlockOnSide(
                    world, placeInfo.blockPos, placeInfo.enumFacing, player, stack
                ) == false
            ) {
                return
            }

            if (autoBlock != "Off") {
                SilentHotbar.selectSlotSilently(this, blockSlot, render = autoBlock == "Pick", resetManually = true)
            }
        }

        tryToPlaceBlock(stack, placeInfo.blockPos, placeInfo.enumFacing, placeInfo.vec3)

        if (autoBlock == "Switch") SilentHotbar.resetSlot(this, true)

        // Since we violate vanilla slot switch logic if we send the packets now, we arrange them for the next tick
        findBlockToSwitchNextTick(stack)

        if (trackCPS) {
            CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
        }
    }

    private fun doPlaceAttempt(raytrace: MovingObjectPosition?, lastClick: Boolean, onSuccess: () -> Unit = { }) {
        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        val stack = player.hotBarSlot(SilentHotbar.currentSlot).stack ?: return

        if (stack.item !is ItemBlock || InventoryUtils.BLOCK_BLACKLIST.contains((stack.item as ItemBlock).block)) {
            return
        }

        raytrace ?: return

        val block = stack.item as ItemBlock

        val canPlaceOnUpperFace = block.canPlaceBlockOnSide(
            world, raytrace.blockPos, EnumFacing.UP, player, stack
        )

        val shouldPlace = if (placementAttempt == "Fail") {
            !block.canPlaceBlockOnSide(world, raytrace.blockPos, raytrace.sideHit, player, stack)
        } else {
            if (shouldKeepLaunchPosition) {
                raytrace.blockPos.y == launchY - 1 && !canPlaceOnUpperFace
            } else if (shouldPlaceHorizontally) {
                !canPlaceOnUpperFace
            } else {
                raytrace.blockPos.y <= player.posY.toInt() - 1 && !(raytrace.blockPos.y == player.posY.toInt() - 1 && canPlaceOnUpperFace && raytrace.sideHit == EnumFacing.UP)
            }
        }

        if (!raytrace.typeOfHit.isBlock || !shouldPlace) {
            return
        }

        tryToPlaceBlock(stack, raytrace.blockPos, raytrace.sideHit, raytrace.hitVec, attempt = true) { onSuccess() }

        // Since we violate vanilla slot switch logic if we send the packets now, we arrange them for the next tick
        if (lastClick) {
            findBlockToSwitchNextTick(stack)
        }

        if (trackCPS) {
            CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
        }
    }

    // Disabling module
    override fun onDisable() {
        net.ccbluex.liquidbounce.utils.client.BlinkUtils.unblink()
        val player = mc.thePlayer ?: return

        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
            mc.gameSettings.keyBindSneak.pressed = false
            if (eagleSneaking && player.isSneaking) {
                //sendPacket(C0BPacketEntityAction(player, C0BPacketEntityAction.Action.STOP_SNEAKING))

                /**
                 * Should prevent false flag by some AntiCheat (Ex: Verus)
                 */
                player.isSneaking = false
            }
        }

        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindRight)) {
            mc.gameSettings.keyBindRight.pressed = false
        }
        if (!GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)) {
            mc.gameSettings.keyBindLeft.pressed = false
        }

        if (autoF5) {
            mc.gameSettings.thirdPersonView = 0
        }

        placeRotation = null
        mc.timer.timerSpeed = 1f

        SilentHotbar.resetSlot(this)

        options.instant = false

        mc.gameSettings.keyBindRight.pressed = false
        mc.gameSettings.keyBindLeft.pressed = false
    }

    // Entity movement event
    val onMove = handler<MoveEvent> { event ->
        val player = mc.thePlayer ?: return@handler

        if (!safeWalkValue.isActive() || shouldGoDown) {
            return@handler
        }

        if (airSafe || player.onGround) {
            event.isSafeWalk = true
        }
    }

    val jumpHandler = handler<JumpEvent> { event ->
        if (!jumpStrafe) return@handler

        if (event.eventState == EventState.POST) {
            MovementUtils.strafe(
                (if (!isLookingDiagonally) jumpStraightStrafe else jumpDiagonalStrafe).random()
            )
        }
    }

    // Visuals
    val onRender3D = handler<Render3DEvent> {
        val player = mc.thePlayer ?: return@handler

        val shouldBother =
            !(shouldGoDown || scaffoldMode == "Expand" && expandLength > 1) && extraClicks && (player.isMoving || MovementUtils.speed > 0.03)

        if (shouldBother) {
            currRotation.let {
                performBlockRaytrace(it, mc.playerController.blockReachDistance)?.let { raytrace ->
                    val timePassed = System.currentTimeMillis() - extraClick.lastClick >= extraClick.delay

                    if (raytrace.typeOfHit.isBlock && timePassed) {
                        extraClick = ExtraClickInfo(
                            TimeUtils.randomClickDelay(extraClickCPS.first, extraClickCPS.last),
                            System.currentTimeMillis(),
                            extraClick.clicks + 1
                        )
                    }
                }
            }
        }

        if (!mark) {
            return@handler
        }

        repeat(if (scaffoldMode == "Expand") expandLength + 1 else 2) {
            val yaw = player.rotationYaw.toRadiansD()
            val x = if (omniDirectionalExpand) -sin(yaw).roundToInt() else player.horizontalFacing.directionVec.x
            val z = if (omniDirectionalExpand) cos(yaw).roundToInt() else player.horizontalFacing.directionVec.z
            val blockPos = BlockPos(
                player.posX + x * it,
                if (shouldKeepLaunchPosition && launchY <= player.posY) launchY - 1.0 else player.posY - (if (player.posY == player.posY + 0.5) 0.0 else 1.0) - if (shouldGoDown) 1.0 else 0.0,
                player.posZ + z * it
            )
            val placeInfo = PlaceInfo.get(blockPos)

            if (blockPos.isReplaceable && placeInfo != null) {
                RenderUtils.drawBlockBox(blockPos, Color(68, 117, 255, 100), false)
                return@handler
            }
        }
    }

    /**
     * Search for placeable block
     *
     * @param blockPosition pos
     * @param raycast visible
     * @param area spot
     * @return
     */

    fun search(
        blockPosition: BlockPos,
        raycast: Boolean,
        area: Boolean,
        horizontalOnly: Boolean = false,
    ): Boolean {
        val player = mc.thePlayer ?: return false

        options.instant = false

        if (!blockPosition.isReplaceable) {
            if (autoF5) mc.gameSettings.thirdPersonView = 0
            return false
        } else {
            if (autoF5 && mc.gameSettings.thirdPersonView != 1) mc.gameSettings.thirdPersonView = 1
        }

        val maxReach = mc.playerController.blockReachDistance

        val eyes = player.eyes
        var placeRotation: PlaceRotation? = null

        var currPlaceRotation: PlaceRotation?

        for (side in EnumFacing.entries) {
            if (horizontalOnly && side.axis == EnumFacing.Axis.Y) {
                continue
            }

            val neighbor = blockPosition.offset(side)

            if (!neighbor.canBeClicked()) {
                continue
            }

            if (!area || isGodBridgeEnabled) {
                currPlaceRotation =
                    findTargetPlace(blockPosition, neighbor, Vec3(0.5, 0.5, 0.5), side, eyes, maxReach, raycast)
                        ?: continue

                placeRotation = compareDifferences(currPlaceRotation, placeRotation)
            } else {
                for (x in 0.1..0.9) {
                    for (y in 0.1..0.9) {
                        for (z in 0.1..0.9) {
                            currPlaceRotation =
                                findTargetPlace(blockPosition, neighbor, Vec3(x, y, z), side, eyes, maxReach, raycast)
                                    ?: continue

                            placeRotation = compareDifferences(currPlaceRotation, placeRotation)
                        }
                    }
                }
            }
        }

        placeRotation ?: return false

        if (options.rotationsActive && !isGodBridgeEnabled) {
            val rotationDifference = rotationDifference(placeRotation.rotation, currRotation)
            val rotationDifference2 = rotationDifference(
                Rotation(placeRotation.rotation.yaw / 90F, placeRotation.rotation.pitch / 90F),
                Rotation(currRotation.yaw / 90F, currRotation.pitch / 90F)
            )

            val simPlayer = SimulatedPlayer.fromClientPlayer(player.movementInput)
            simPlayer.tick()

            // We don't want to use block safe all the time, so we check if it's not needed.
            options.instant =
                blockSafe && simPlayer.fallDistance > player.fallDistance + 0.05 && rotationDifference > rotationDifference2 / 2

            setRotation(placeRotation.rotation, if (scaffoldMode == "Telly") 1 else options.resetTicks)
        }

        this.placeRotation = placeRotation
        return true
    }

    /**
     * For expand scaffold, fixes vector values that should match according to direction vector
     */
    private fun modifyVec(original: Vec3, direction: EnumFacing, pos: Vec3, shouldModify: Boolean): Vec3 {
        if (!shouldModify) {
            return original
        }

        val x = original.xCoord
        val y = original.yCoord
        val z = original.zCoord

        val side = direction.opposite

        return when (side.axis ?: return original) {
            EnumFacing.Axis.Y -> Vec3(x, pos.yCoord + side.directionVec.y.coerceAtLeast(0), z)
            EnumFacing.Axis.X -> Vec3(pos.xCoord + side.directionVec.x.coerceAtLeast(0), y, z)
            EnumFacing.Axis.Z -> Vec3(x, y, pos.zCoord + side.directionVec.z.coerceAtLeast(0))
        }

    }

    private fun findTargetPlace(
        pos: BlockPos, offsetPos: BlockPos, vec3: Vec3, side: EnumFacing, eyes: Vec3, maxReach: Float, raycast: Boolean,
    ): PlaceRotation? {
        val world = mc.theWorld ?: return null

        val vec = (Vec3(pos) + vec3).addVector(
            side.directionVec.x * vec3.xCoord, side.directionVec.y * vec3.yCoord, side.directionVec.z * vec3.zCoord
        )

        val distance = eyes.distanceTo(vec)

        if (raycast && (distance > maxReach || world.rayTraceBlocks(eyes, vec, false, true, false) != null)) {
            return null
        }

        val diff = vec - eyes

        if (side.axis != EnumFacing.Axis.Y) {
            val dist = abs(if (side.axis == EnumFacing.Axis.Z) diff.zCoord else diff.xCoord)

            if (dist < minDist && scaffoldMode != "Telly") {
                return null
            }
        }

        var rotation = toRotation(vec, false)

        val roundYaw90 = round(rotation.yaw / 90f) * 90f
        val roundYaw45 = round(rotation.yaw / 45f) * 45f

        rotation = when (options.rotationMode) {
            "Stabilized" -> Rotation(roundYaw45, rotation.pitch)
            "ReverseYaw" -> Rotation(if (!isLookingDiagonally) roundYaw90 else roundYaw45, rotation.pitch)
            else -> rotation
        }.fixedSensitivity()

        // If the current rotation already looks at the target block and side, then return right here
        performBlockRaytrace(currRotation, maxReach)?.let { raytrace ->
            if (raytrace.blockPos == offsetPos && (!raycast || raytrace.sideHit == side.opposite)) {
                return PlaceRotation(
                    PlaceInfo(
                        raytrace.blockPos, side.opposite, modifyVec(raytrace.hitVec, side, Vec3(offsetPos), !raycast)
                    ), currRotation
                )
            }
        }

        val raytrace = performBlockRaytrace(rotation, maxReach) ?: return null

        val multiplier = if (options.legitimize) 3 else 1

        if (raytrace.blockPos == offsetPos && (!raycast || raytrace.sideHit == side.opposite) && canUpdateRotation(
                currRotation, rotation, multiplier
            )
        ) {
            return PlaceRotation(
                PlaceInfo(
                    raytrace.blockPos, side.opposite, modifyVec(raytrace.hitVec, side, Vec3(offsetPos), !raycast)
                ), rotation
            )
        }

        return null
    }

    private fun performBlockRaytrace(rotation: Rotation, maxReach: Float): MovingObjectPosition? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null

        val eyes = player.eyes
        val rotationVec = getVectorForRotation(rotation)

        val reach = eyes + (rotationVec * maxReach.toDouble())

        return world.rayTraceBlocks(eyes, reach, false, false, true)
    }

    private fun compareDifferences(
        new: PlaceRotation, old: PlaceRotation?, rotation: Rotation = currRotation,
    ): PlaceRotation {
        if (old == null || rotationDifference(new.rotation, rotation) < rotationDifference(old.rotation, rotation)) {
            return new
        }

        return old
    }

    private fun findBlockToSwitchNextTick(stack: ItemStack) {
        if (autoBlock in arrayOf("Off", "Switch")) return

        val switchAmount = if (earlySwitch) amountBeforeSwitch else 0

        if (stack.stackSize > switchAmount) return

        val switchSlot = if (earlySwitch) {
            InventoryUtils.findBlockStackInHotbarGreaterThan(amountBeforeSwitch) ?: InventoryUtils.findBlockInHotbar()
            ?: return
        } else {
            InventoryUtils.findBlockInHotbar()
        } ?: return

        SilentHotbar.selectSlotSilently(this, switchSlot, render = autoBlock == "Pick", resetManually = true)
    }

    private fun updatePlacedBlocksForTelly() {
        if (blocksUntilAxisChange > horizontalPlacements + verticalPlacements) {
            blocksUntilAxisChange = 0

            horizontalPlacements = horizontalPlacementsRange.random()
            verticalPlacements = verticalPlacementsRange.random()
            return
        }

        blocksUntilAxisChange++
    }

    private fun tryToPlaceBlock(
        stack: ItemStack, clickPos: BlockPos, side: EnumFacing, hitVec: Vec3, attempt: Boolean = false,
        onSuccess: () -> Unit = { }
    ): Boolean {
        val thePlayer = mc.thePlayer ?: return false

        val prevSize = stack.stackSize

        val clickedSuccessfully = thePlayer.onPlayerRightClick(clickPos, side, hitVec, stack)

        if (clickedSuccessfully) {
            if (!attempt) {
                delayTimer.reset()

                if (thePlayer.onGround) {
                    thePlayer.motionX *= speedModifier
                    thePlayer.motionZ *= speedModifier
                }
            }

            if (swing) thePlayer.swingItem()
            else sendPacket(C0APacketAnimation())

            if (isManualJumpOptionActive) blocksPlacedUntilJump++

            updatePlacedBlocksForTelly()

            if (stack.stackSize <= 0) {
                thePlayer.inventory.mainInventory[SilentHotbar.currentSlot] = null
                ForgeEventFactory.onPlayerDestroyItem(thePlayer, stack)
            } else if (stack.stackSize != prevSize || mc.playerController.isInCreativeMode) mc.entityRenderer.itemRenderer.resetEquippedProgress()

            placeRotation = null

            placedBlocksWithoutEagle++

            onSuccess()
        } else {
            if (thePlayer.sendUseItem(stack)) mc.entityRenderer.itemRenderer.resetEquippedProgress2()
        }

        return clickedSuccessfully
    }

    fun handleMovementOptions(input: MovementInput) {
        val player = mc.thePlayer ?: return

        if (!state) {
            return
        }

        if (!slow && speedLimiter && MovementUtils.speed > speedLimit) {
            input.moveStrafe = 0f
            input.moveForward = 0f
            return
        }

        when (zitterMode.lowercase()) {
            "off" -> {
                return
            }

            "smooth" -> {
                val notOnGround = !player.onGround || !player.isCollidedVertically

                if (player.onGround) {
                    input.sneak = eagleSneaking || GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)
                }

                if (input.jump || mc.gameSettings.keyBindJump.isKeyDown || notOnGround) {
                    zitterTickTimer.reset()

                    if (useSneakMidAir) {
                        input.sneak = true
                    }

                    if (!notOnGround && !input.jump) {
                        // Attempt to move against the direction
                        input.moveStrafe = if (zitterDirection) 1f else -1f
                    } else {
                        input.moveStrafe = 0f
                    }

                    zitterDirection = !zitterDirection

                    // Recreate input in case the user was indeed pressing inputs
                    if (mc.gameSettings.keyBindLeft.isKeyDown) {
                        input.moveStrafe++
                    }

                    if (mc.gameSettings.keyBindRight.isKeyDown) {
                        input.moveStrafe--
                    }
                    return
                }

                if (zitterTickTimer.hasTimePassed()) {
                    zitterDirection = !zitterDirection
                    zitterTickTimer.reset()
                } else {
                    zitterTickTimer.update()
                }

                if (zitterDirection) {
                    input.moveStrafe = -1f
                } else {
                    input.moveStrafe = 1f
                }
            }

            "teleport" -> {
                MovementUtils.strafe(zitterSpeed)
                val yaw = (player.rotationYaw + if (zitterDirection) 90.0 else -90.0).toRadians()
                player.motionX -= sin(yaw) * zitterStrength
                player.motionZ += cos(yaw) * zitterStrength
                zitterDirection = !zitterDirection
            }
        }
    }

    private var isOnRightSide = false

    /**
     * God-bridge rotation generation method from Nextgen
     *
     * Credits to @Ell1ott
     */
    private fun generateGodBridgeRotations(ticks: Int) {
        val player = mc.thePlayer ?: return

        val direction = if (options.applyServerSide) {
            MovementUtils.direction.toDegreesF() + 180f
        } else MathHelper.wrapAngleTo180_float(player.rotationYaw)

        val movingYaw = round(direction / 45) * 45

        val steps45 = arrayListOf(-135f, -45f, 45f, 135f)

        val isMovingStraight = if (options.applyServerSide) {
            movingYaw % 90 == 0f
        } else movingYaw in steps45 && player.movementInput.isSideways

        if (!player.isNearEdge(2.5f)) return

        if (!player.isMoving) {
            placeRotation?.run {
                val axisMovement = floor(this.rotation.yaw / 90) * 90
                val yaw = axisMovement + 45f
                val pitch = calculateOptimalPitch()

                setRotation(Rotation(yaw, pitch), ticks)
                return
            }

            if (!options.keepRotation) return
        }

        val rotation = if (isMovingStraight) {
            if (player.onGround) {
                isOnRightSide = floor(player.posX + cos(movingYaw.toRadians()) * 0.5) != floor(player.posX) || floor(
                    player.posZ + sin(movingYaw.toRadians()) * 0.5
                ) != floor(player.posZ)

                val posInDirection =
                    BlockPos(player.positionVector.offset(EnumFacing.fromAngle(movingYaw.toDouble()), if(autoAdjust) 0.7 else 0.6))

                val isLeaningOffBlock = player.position.down().block == air
                val nextBlockIsAir = posInDirection.down().block == air

                if (isLeaningOffBlock && nextBlockIsAir) {
                    isOnRightSide = !isOnRightSide
                }
            }

            val side = if (options.applyServerSide) {
                if (isOnRightSide) 45f else -45f
            } else 0f

            val pitch = calculateOptimalPitch()

            Rotation(movingYaw + side, pitch)
        } else {
            val pitch = if (dynamicPitch) 75.6f + (if(player.fallDistance > 0) player.fallDistance * 0.5f else 0f) else 75.6f
            Rotation(movingYaw, pitch)
        }.fixedSensitivity()

        if (stabilizeSpeed && player.onGround) {
            val currentSpeed = sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ)
            if (currentSpeed > 0.12) {
                player.motionX *= 0.9
                player.motionZ *= 0.9
            }
        }

        godBridgeTargetRotation = rotation
        setRotation(rotation, ticks)
    }

    private fun isServerPacket(packet: Any): Boolean {
        return packet.javaClass.simpleName.startsWith("S")
    }
    private fun isEntityMovementPacket(packet: Any): Boolean {
        return when (packet) {
            is net.minecraft.network.play.server.S14PacketEntity,
            is net.minecraft.network.play.server.S18PacketEntityTeleport,
            is net.minecraft.network.play.server.S19PacketEntityHeadLook,
            is net.minecraft.network.play.server.S0BPacketAnimation,
            is net.minecraft.network.play.server.S0CPacketSpawnPlayer,
            is net.minecraft.network.play.server.S1CPacketEntityMetadata -> true
            else -> {
                val name = packet.javaClass.simpleName
                name == "S15PacketEntityRelMove" ||
                        name == "S17PacketEntityLookMove" ||
                        name == "S16PacketEntityLook"
            }
        }
    }

    private fun calculateOptimalPitch(): Float {
        val player = mc.thePlayer ?: return if (useOptimizedPitch) 73.5f else customGodPitch

        return when {
            useOptimizedPitch -> {
                // Calculate optimal pitch based on player's state
                val basePitch = 73.5f
                when {
                    player.fallDistance > 0 -> basePitch + (player.fallDistance * 0.6f).coerceAtMost(8f)
                    player.motionY < -0.1 -> basePitch + 2f
                    player.isSprinting -> basePitch - 0.5f
                    else -> basePitch
                }
            }
            dynamicPitch -> {
                // Dynamic pitch adjustment based on player's motion
                val targetPitch = customGodPitch + when {
                    player.fallDistance > 0 -> player.fallDistance * 0.8f
                    player.motionY < -0.1 -> 2f
                    player.isSprinting -> -0.5f
                    else -> 0f
                }
                targetPitch.coerceIn(65f, 85f)
            }
            else -> customGodPitch
        }
    }

    override val tag
        get() = if (towerMode != "None") ("$scaffoldMode | $towerMode") else scaffoldMode

    data class ExtraClickInfo(val delay: Int, val lastClick: Long, var clicks: Int)
}