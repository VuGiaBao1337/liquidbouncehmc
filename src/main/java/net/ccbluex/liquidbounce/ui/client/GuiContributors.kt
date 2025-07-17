/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.ui.client

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.injection.implementations.IMixinGuiSlot
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.ui.font.AWTFontRenderer.Companion.assumeNonVolatile
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.io.HttpClient
import net.ccbluex.liquidbounce.utils.io.get
import net.ccbluex.liquidbounce.utils.io.jsonBody
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.render.CustomTexture
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawLoadingCircle
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiSlot
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.sin

class GuiContributors(private val prevGui: GuiScreen) : AbstractScreen() {
    private lateinit var list: GuiList
    private var credits = emptyList<Credit>()
    private var failed = false
    private var animationTime = 0f

    companion object {
        private val DECIMAL_FORMAT = NumberFormat.getInstance(Locale.US) as DecimalFormat
    }

    override fun initGui() {
        list = GuiList(this)
        list.registerScrollButtons(7, 8)

        +GuiButton(1, width / 2 - 100, height - 30, "Back")

        failed = false

        SharedScopes.IO.launch { loadCredits() }
    }

    private fun drawGradientBackground() {
        animationTime += 0.02f
        
        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        
        disableTexture2D()
        enableBlend()
        disableAlpha()
        tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0)
        shadeModel(GL_SMOOTH)
        
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR)
        
        val time = animationTime
        val topR = (0.1f + 0.3f * kotlin.math.sin(time * 0.5f)).coerceIn(0f, 1f)
        val topG = (0.3f + 0.4f * kotlin.math.sin(time * 0.7f + 1f)).coerceIn(0f, 1f)
        val topB = (0.7f + 0.3f * kotlin.math.sin(time * 0.3f + 2f)).coerceIn(0f, 1f)
        
        worldRenderer.pos(width.toDouble(), 0.0, zLevel.toDouble()).color(topR, topG, topB, 1.0f).endVertex()
        worldRenderer.pos(0.0, 0.0, zLevel.toDouble()).color(topR, topG, topB, 1.0f).endVertex()
        worldRenderer.pos(0.0, height.toDouble(), zLevel.toDouble()).color(0.9f, 0.95f, 1.0f, 1.0f).endVertex()
        worldRenderer.pos(width.toDouble(), height.toDouble(), zLevel.toDouble()).color(0.9f, 0.95f, 1.0f, 1.0f).endVertex()
        
        tessellator.draw()
        
        shadeModel(GL_FLAT)
        disableBlend()
        enableAlpha()
        enableTexture2D()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        assumeNonVolatile {
            drawGradientBackground()

            list.drawScreen(mouseX, mouseY, partialTicks)

            // Enhanced glowing rect
            val glowIntensity = (0.4f + 0.6f * kotlin.math.sin(animationTime * 2f)).coerceIn(0f, 1f)
            val rectColor = (Integer.MIN_VALUE and 0x00FFFFFF) or ((80 * glowIntensity).toInt() shl 24)
            
            drawRect(width / 4f, 40f, width.toFloat(), height - 40f, rectColor)

            if (credits.isNotEmpty()) {
                val credit = credits[list.selectedSlot]

                var y = 45
                val x = width / 4 + 5
                var infoOffset = 0

                val avatar = credit.avatar
                val imageSize = fontRendererObj.FONT_HEIGHT * 4

                if (avatar != null) {
                    pushAttrib()

                    enableAlpha()
                    enableBlend()
                    tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
                    enableTexture2D()

                    glColor4f(1f, 1f, 1f, 1f)

                    bindTexture(avatar.textureId)

                    glBegin(GL_QUADS)

                    glTexCoord2f(0f, 0f)
                    glVertex2i(x, y)
                    glTexCoord2f(0f, 1f)
                    glVertex2i(x, y + imageSize)
                    glTexCoord2f(1f, 1f)
                    glVertex2i(x + imageSize, y + imageSize)
                    glTexCoord2f(1f, 0f)
                    glVertex2i(x + imageSize, y)

                    glEnd()

                    bindTexture(0)

                    disableBlend()

                    infoOffset = imageSize

                    popAttrib()
                }

                y += imageSize

                // Animated contributor info
                val infoGlow = (0.7f + 0.3f * kotlin.math.sin(animationTime * 2.5f)).coerceIn(0f, 1f)
                val infoColor = (Color.WHITE.rgb and 0x00FFFFFF) or ((255 * infoGlow).toInt() shl 24)

                Fonts.fontSemibold40.drawString("@" + credit.name, x + infoOffset + 5f, 48f, infoColor, true)
                Fonts.fontSemibold40.drawString(
                    "${credit.commits} commits §a${DECIMAL_FORMAT.format(credit.additions)}++ §4${
                        DECIMAL_FORMAT.format(credit.deletions)
                    }--", x + infoOffset + 5f, (y - Fonts.fontSemibold40.fontHeight).toFloat(), infoColor, true
                )

                for (s in credit.contributions) {
                    y += Fonts.fontSemibold40.fontHeight + 2

                    disableTexture2D()
                    glColor4f(1f, 1f, 1f, 1f)
                    glBegin(GL_LINES)

                    glVertex2f(x.toFloat(), y + Fonts.fontSemibold40.fontHeight / 2f - 1)
                    glVertex2f(x + 3f, y + Fonts.fontSemibold40.fontHeight / 2f - 1)

                    glEnd()

                    Fonts.fontSemibold40.drawString(s, (x + 5f), y.toFloat(), infoColor, true)
                }
            }

            // Animated title
            val titleTime = animationTime * 1.5f
            val titleR = (0.2f + 0.4f * kotlin.math.sin(titleTime)).coerceIn(0f, 1f)
            val titleG = (0.4f + 0.4f * kotlin.math.sin(titleTime + 1f)).coerceIn(0f, 1f)
            val titleB = (0.8f + 0.2f * kotlin.math.sin(titleTime + 2f)).coerceIn(0f, 1f)
            val titleColor = ((titleR * 255).toInt() shl 16) or ((titleG * 255).toInt() shl 8) or (titleB * 255).toInt()

            Fonts.fontSemibold40.drawCenteredString(translationMenu("contributors"), width / 2F, 6F, titleColor)

            if (credits.isEmpty()) {
                if (failed) {
                    val gb = ((kotlin.math.sin(System.currentTimeMillis() * (1 / 333.0)) + 1) * (0.5 * 255)).toInt()
                    Fonts.fontSemibold40.drawCenteredString("Failed to load", width / 8f, height / 2f, Color(255, gb, gb).rgb)
                } else {
                    val loadingGlow = (0.7f + 0.3f * kotlin.math.sin(animationTime * 3f)).coerceIn(0f, 1f)
                    val loadingColor = (Color.WHITE.rgb and 0x00FFFFFF) or ((255 * loadingGlow).toInt() shl 24)
                    
                    Fonts.fontSemibold40.drawCenteredString("Loading...", width / 8f, height / 2f, loadingColor)
                    drawLoadingCircle(width / 8f, height / 2f - 40)
                }
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 1) {
            mc.displayGuiScreen(prevGui)
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        when (keyCode) {
            Keyboard.KEY_ESCAPE -> mc.displayGuiScreen(prevGui)
            Keyboard.KEY_UP -> list.selectedSlot -= 1
            Keyboard.KEY_DOWN -> list.selectedSlot += 1
            Keyboard.KEY_TAB ->
                list.selectedSlot += if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) -1 else 1

            else -> super.keyTyped(typedChar, keyCode)
        }
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        list.handleMouseInput()
    }

    private fun loadCredits() {
        try {
            val gitHubContributors = HttpClient.get(
                "https://api.github.com/repos/CCBlueX/LiquidBounce/stats/contributors"
            ).jsonBody<Array<GitHubContributor>>() ?: run {
                failed = true
                return
            }

            val additionalInformation = HttpClient.get(
                "https://raw.githubusercontent.com/CCBlueX/LiquidCloud/master/LiquidBounce/contributors.json"
            ).jsonBody<Map<String, ContributorInformation>>() ?: emptyMap()

            val creditsList = ArrayList<Credit>(gitHubContributors.size)

            for (gitHubContributor in gitHubContributors) {
                val author = gitHubContributor.author ?: continue

                val contributorInformation = additionalInformation[author.id.toString()]

                var additions = 0
                var deletions = 0
                var commits = 0

                for (week in gitHubContributor.weeks) {
                    additions += week.additions
                    deletions += week.deletions
                    commits += week.commits
                }

                creditsList += Credit(
                    author.name, author.avatarUrl,
                    additions, deletions, commits,
                    contributorInformation?.teamMember ?: false,
                    contributorInformation?.contributions ?: emptyList()
                )
            }

            creditsList.sortWith { o1, o2 ->
                when {
                    o1.isTeamMember && o2.isTeamMember -> -o1.commits.compareTo(o2.commits)
                    o1.isTeamMember -> -1
                    o2.isTeamMember -> 1
                    else -> -o1.additions.compareTo(o2.additions)
                }
            }

            this.credits = creditsList
        } catch (e: Exception) {
            LOGGER.error("Failed to load credits.", e)
            failed = true
        }
    }

    private inner class GuiList(gui: GuiScreen) : GuiSlot(mc, gui.width / 4, gui.height, 40, gui.height - 40, 15) {

        init {
            val mixin = this as IMixinGuiSlot

            mixin.listWidth = gui.width * 3 / 13
            mixin.enableScissor = true
        }

        var selectedSlot = 0
            set(value) {
                field = (value + credits.size) % credits.size
            }

        override fun isSelected(id: Int) = selectedSlot == id

        override fun getSize() = credits.size

        public override fun elementClicked(index: Int, doubleClick: Boolean, var3: Int, var4: Int) {
            selectedSlot = index
        }

        override fun drawSlot(
            entryID: Int, p_180791_2_: Int, p_180791_3_: Int, p_180791_4_: Int, mouseXIn: Int,
            mouseYIn: Int,
        ) {
            val credit = credits[entryID]

            // Animated contributor names
            val nameGlow = (0.7f + 0.3f * kotlin.math.sin(animationTime * 2f + entryID * 0.3f)).coerceIn(0f, 1f)
            val nameColor = (Color.WHITE.rgb and 0x00FFFFFF) or ((255 * nameGlow).toInt() shl 24)

            Fonts.fontSemibold40.drawCenteredString(credit.name, width / 2F, p_180791_3_ + 2F, nameColor, true)
        }

        override fun drawBackground() {}
    }

    private class ContributorInformation(
        val name: String, val teamMember: Boolean,
        val contributions: List<String>,
    )

    private class GitHubContributor(
        @SerializedName("total") val totalContributions: Int,
        val weeks: List<GitHubWeek>, val author: GitHubAuthor?,
    )

    private class GitHubWeek(
        @SerializedName("w") val timestamp: Long, @SerializedName("a") val additions: Int,
        @SerializedName("d") val deletions: Int, @SerializedName("c") val commits: Int,
    )

    private class GitHubAuthor(
        @SerializedName("login") val name: String, val id: Int,
        @SerializedName("avatar_url") val avatarUrl: String,
    )

    private class Credit(
        val name: String, val avatarUrl: String, val additions: Int,
        val deletions: Int, val commits: Int, val isTeamMember: Boolean, val contributions: List<String>,
    ) {
        val avatar by lazy {
            runCatching {
                HttpClient.get(avatarUrl).use { response ->
                    response.body.byteStream().use(ImageIO::read).let(::CustomTexture)
                }
            }.onFailure {
                LOGGER.error("Failed to load avatar.", it)
            }.getOrNull()
        }
    }
}
