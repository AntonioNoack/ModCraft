package me.anno.blocks.ui

import me.anno.blocks.ClientInstance
import me.anno.blocks.block.BlockInfo
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.base.AirBlock
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.entity.player.Player
import me.anno.blocks.item.ItemStack
import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.packets.block.BlockChangePacket
import me.anno.blocks.multiplayer.packets.motion.ClientMovePacket
import me.anno.blocks.physics.BlockHit
import me.anno.blocks.physics.Physics
import me.anno.blocks.physics.RaycastHit
import me.anno.blocks.rendering.DebugCubes.drawDebugCube
import me.anno.blocks.rendering.RenderData
import me.anno.blocks.rendering.RenderPass
import me.anno.blocks.rendering.SelectVisuals
import me.anno.blocks.rendering.ShaderLib2.skyShader
import me.anno.blocks.rendering.ShaderLib2.solidShader
import me.anno.blocks.utils.struct.Vector3j
import me.anno.blocks.world.Dimension
import me.anno.config.DefaultConfig.style
import me.anno.config.DefaultStyle.black
import me.anno.gpu.GFX
import me.anno.gpu.GFXx2D
import me.anno.gpu.blending.BlendDepth
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.buffer.Buffer
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.Frame
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.Texture2D
import me.anno.input.Input
import me.anno.input.MouseButton
import me.anno.ui.base.Font
import me.anno.ui.base.Panel
import me.anno.utils.Maths.clamp
import me.anno.utils.Maths.length
import me.anno.utils.files.Files.formatFileSize
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.system.exitProcess

class DimensionView(
    val instance: ClientInstance,
    val getClient: () -> Client?,
    val getDimension: () -> Dimension?,
    val getPlayer: () -> Player?
) : Panel(style) {

    override fun getVisualState() = GFX.gameTime

    val data = RenderData()

    val fovDegrees = 90f

    val solidPass = RenderPass(solidShader, MaterialType.SOLID_BLOCK)
    val solidPassComplex = RenderPass(solidShader, MaterialType.SOLID_COMPLEX)
    val transPassMass = RenderPass(solidShader, MaterialType.TRANSPARENT_MASS)
    val transPassComplex = RenderPass(solidShader, MaterialType.TRANSPARENT_COMPLEX)

    var lastPositionUpdate = 0L

    val isInFocus2 get() = GFX.trapMousePanel === this

    fun checkInputs() {

        if (!isInFocus2) return

        GFX.trapMouseRadius = StrictMath.min(w, h) * 0.333f

        val player = getPlayer() ?: return
        val velocity = player.velocity

        val newVelocity = Vector3f()
        if (Input.isKeyDown('w')) newVelocity.z--
        if (Input.isKeyDown('s')) newVelocity.z++
        if (Input.isKeyDown('a')) newVelocity.x--
        if (Input.isKeyDown('d')) newVelocity.x++
        if (Input.isShiftDown || Input.isKeyDown(' ') || Input.isKeyDown('e')) newVelocity.y++
        if (Input.isControlDown || Input.isKeyDown('q')) newVelocity.y--
        val dt = GFX.deltaTime
        val f = 3f * dt
        velocity.mul(1f - f)
        newVelocity.mul(f * 30f)
        newVelocity.rotateY(-player.rotationY)
        velocity.add(newVelocity)
        player.position.add(Vector3f(velocity).mul(dt))

    }

    fun sendInputs() {
        val time = GFX.gameTime
        val dt = abs(time - lastPositionUpdate)
        if (dt > 100_000_000) {
            lastPositionUpdate = time
            val player = getPlayer() ?: return
            val client = getClient() ?: return
            client.sendAsync(ClientMovePacket(player))
        }
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (isInFocus2) {
            val player = getPlayer() ?: return
            val f = 3f / h
            val trapRadius = GFX.trapMouseRadius
            if (length(dx, dy) < trapRadius * 0.75f) {
                player.rotationY += dx * f
                player.headRotX = clamp(player.headRotX + dy * f, -1.57f, 1.57f)
            }
        }
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {

        val data = data
        data.clearStats()

        checkInputs()

        sendInputs()

        drawBackground()

        val dimension = getDimension()
        val player = getPlayer()

        if (player == null || dimension == null) {
            // LOGGER.info("$player/$dimension")
            showLoadingScreen()
        } else {
            try {
                drawScene(dimension, player)
            } catch (e: Exception) {
                e.printStackTrace()
                exitProcess(-1)
            }
        }

    }

    fun showLoadingScreen() {
        GFXx2D.drawText(0, 0, font, "Loading...", -1, black, -1)
    }

    private var hovered: RaycastHit? = null
    private val p0 = Vector3f()
    private val p1 = Vector3f()
    private fun findHoveredBlock(dimension: Dimension, player: Player, inverse: Matrix4f): BlockInfo? {

        val p0 = inverse.transformProject(p0.set(0f, 0f, 0f))
        val p1 = inverse.transformProject(p1.set(0f, 0f, 1f))
        val dir = p1.sub(p0).normalize()
        player.mouseDirection.set(dir)

        val hit = Physics.raytrace(
            data.cameraPosition, dir, 10f,
            dimension.hasBlocksBelowZero, false, null
        ) { x, y, z ->
            val chunk = dimension.getChunkAt(x, y, z, false)
            if (chunk != null && chunk.isFinished) {
                chunk.getBlockState(x, y, z)
            } else null
        }
        hovered = hit

        if (hit is BlockHit) return dimension.getBlockInfo(hit.blockPosition, false)
        return null

    }

    fun clear() {
        Frame.bind()
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_DEPTH_BUFFER_BIT or GL_COLOR_BUFFER_BIT)
    }

    fun copy(colors: Framebuffer, alpha: Boolean) {
        colors.bindTexture0(0, GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
        if (alpha) GFX.copy()
        else GFX.copyNoAlpha()
    }

    fun drawDebug() {
        // todo draw debug stuff
        val hover = hovered
        if (hover != null && hover.distance > 1.0) {
            // println(hover.distance)
            /*for(i in 10 until 100){
                val f = i * 0.01
                drawDebugCube(hover.position * f + data.cameraPosition * (1-f))
            }*/
            if (hover is BlockHit) {
                drawDebugCube(data, hover.blockPosition)
            } else {
                drawDebugCube(data, hover.position)
            }
        }
        // val player = getPlayer()!!
        // for(angle in 10 until 100) drawDebugCube(data.cameraPosition + Vector3d(player.mouseDirection) * (angle.toDouble()))
    }

    fun drawScene(dimension: Dimension, player: Player) {

        // at max 1000 fps ^^
        // Thread.sleep(1)

        val w = w
        val h = h

        val data = data
        val matrix = data.matrix
        matrix.identity()
        matrix.perspective(Math.toRadians(fovDegrees.toDouble()).toFloat(), w.toFloat() / h, 0.1f, 500f)
        matrix.rotateX(player.headRotX)
        matrix.rotateY(player.rotationY)
        matrix.invert(data.inverse)

        data.cameraPosition.set(player.position)
        data.cameraRotation.set(player.headRotX, player.rotationY, 0f)
        dimension.findSpecialRenderingConditions(data)

        findHoveredBlock(dimension, player, data.inverse)

        // val t0 = Clock()
        dimension.prepareChunks(data, player)
        dimension.unloadChunks(data)
        // t0.stop("preDraw")

        val showStacking = Input.isKeyDown('o')
        data.renderLines = Input.isKeyDown('l')

        val blendMode = if (showStacking) BlendMode.ADD else null
        val samples = if (Input.isKeyDown('u')) 8 else 1

        val colors = FBStack["Solid", w, h, samples, false]

        val enableDistanceFog = !Input.isKeyDown('i') && !showStacking
        if (enableDistanceFog) {

            Frame(colors) {

                clear()

                BlendDepth(blendMode, false, false) {
                    glDisable(GL_CULL_FACE)
                    dimension.prepareShader(data, skyShader)
                    dimension.drawSky(data)
                }

            }
            copy(colors, false)

        }

        val solidShader = solidShader
        solidShader.use()
        solidShader.v1("showLight", if (Input.isKeyDown('j')) 1f else 0f)
        dimension.prepareShader(data, solidShader)

        Frame(colors) {

            if (!enableDistanceFog) clear()

            glEnable(GL_CULL_FACE)
            glCullFace(GL_FRONT)

            glEnable(GL_DEPTH_TEST)
            glDepthFunc(GL_LESS)

            BlendDepth(blendMode, true) {

                GFX.check()

                dimension.draw(data, solidPass)
                dimension.draw(data, solidPassComplex)

                GFX.check()

                // drawDebug()

                GFX.check()

                glDisable(GL_CULL_FACE)

                if (!enableDistanceFog) {
                    dimension.prepareShader(data, skyShader)
                    dimension.drawSky(data)
                }

                GFX.check()

            }

        }

        // t0.stop("colors")
        copy(colors, enableDistanceFog)

        Frame(colors) {

            Frame.bind()
            BlendDepth(blendMode, true) {
                dimension.draw(data, transPassMass)
                dimension.draw(data, transPassComplex)
            }

            BlendDepth(null, true) {
                draw3DCursor(data, player)
                drawItemBar(data, player)
            }

        }

        // t0.stop("transparency")

        colors.bindTexture0(0, GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
        GFX.copy()

        drawCrosshair()

        /*val runtime = Runtime.getRuntime()
        // /${runtime.maxMemory().formatFileSize()}
        val position = player.position
        val mouse = player.mouseDirection
        GFXx2D.drawTextCharByChar(
            0, 0, font, "" +
                    "Chunk: ${data.chunkTriangles}/${data.chunkBuffers}/${dimension.chunksByDistance.renderingIndex}\n" +
                    "Memory: ${runtime.freeMemory().formatFileSize()}/${runtime.totalMemory().formatFileSize()}\n" +
                    "Position: ${position.x.f2()} ${position.y.f2()} ${position.z.f2()}\n" +
                    "Mouse: ${mouse.x.f2()} ${mouse.y.f2()} ${mouse.z.f2()}",
            -1, black, -1, false, true
        )*/

    }

    fun draw3DCursor(data: RenderData, player: Player) {

        val hit = hovered as? BlockHit ?: return
        if (hit.distance < 1.0) return

        val block = SelectVisuals
        val shader = solidShader

        shader.v1(shader.alpha, 1f)
        shader.v4(shader.tint, 0f)

        val scale = 17f / 16f

        val cam = data.cameraPosition
        val blo = hit.blockPosition
        shader.v3(shader.offset, (blo.x - cam.x).toFloat(), (blo.y - cam.y).toFloat(), (blo.z - cam.z).toFloat())

        data.lastTexture = null

        val matrix = data.matrix
        matrix.next {
            matrix.scale(1f / scale)
            shader.m4x4(shader.matrix, matrix)
            block.draw(data, shader)
        }

    }

    fun drawItemBar(data: RenderData, player: Player) {

        val inventory = player.inventory
        val ratio = h.toFloat() / w
        val bottom = -1f
        val range = inventory.sizeX * 1f

        val matrix = data.matrix
        matrix.identity()
        matrix.ortho(-range, range, bottom * ratio, (bottom + 2f * range) * ratio, -1f, 1000f)

        val shader = solidShader
        shader.use()
        data.lastTexture = null

        shader.v1(shader.alpha, 1f)
        shader.v4(shader.tint, 0f)

        for (i in 0 until inventory.sizeX) {

            val x = i - (inventory.sizeX - 1) * 0.5f
            val slot = i + (inventory.sizeY - 1) * inventory.sizeX
            val block = inventory.slots[slot]

            // draw block
            matrix.next {

                matrix.translate(x, 0f, 0f)
                matrix.rotateX(0.3f)
                matrix.rotateY(1.57f / 2f)

                val scale = if (player.selectedSlot == i) 0.6f else 0.5f
                matrix.scale(scale)

                shader.m4x4(shader.matrix, matrix)

            }

            shader.v3(shader.offset, -0.5f)
            block.state.block.apply {
                draw(data, shader)
            }

            // todo draw number
            // todo draw outline

        }
    }

    private fun drawCrosshair() {
        val color = 0x777777 or black
        val x0 = x + w / 2
        val y0 = y + h / 2
        val sx = 4
        val sy = 20
        GFXx2D.drawRect(x0 - sx / 2, y0 - sy / 2, sx, sy, color)
        GFXx2D.drawRect(x0 - sy / 2, y0 - sx / 2, sy, sx, color)
    }

    val font = Font("Consolas", 24f, false, false)

    fun setBlock(position: Vector3j, newBlock: BlockState, oldBlock: BlockState) {
        getClient()?.apply { sendAsync(BlockChangePacket(position, newBlock, oldBlock)) }
        getDimension()?.setBlock(position, true, newBlock)
    }

    private var remainingScrollSum = 0f
    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float) {
        if (!isInFocus2) return

        val player = getPlayer() ?: return
        remainingScrollSum += dy
        val yi = remainingScrollSum.roundToInt()
        if (yi != 0) {
            remainingScrollSum -= yi
            player.scrollSlot(yi)
        }

    }

    override fun onMouseDown(x: Float, y: Float, button: MouseButton) {

        GFX.trapMousePanel = this
        instance.console.hide()

        val hovered = hovered
        if (hovered != null && isInFocus2) {
            when {
                button.isLeft -> {
                    // todo mine / hit something
                    when (hovered) {
                        is BlockHit -> {
                            setBlock(hovered.blockPosition, Air, hovered.block)
                        }
                    }
                }
                button.isMiddle -> {
                    // copy position?...
                    val player = getPlayer() ?: return
                    val blockHit = hovered as? BlockHit ?: return
                    val inventory = player.inventory
                    val slot = player.selectedSlot + player.hotBarStartIndex
                    inventory.slots[slot] = ItemStack(blockHit.block)
                }
                button.isRight -> {
                    // todo set the block / open something
                    when (hovered) {
                        is BlockHit -> {
                            // todo get the previous block...
                            // getClient()?.send(BlockChangePacket())
                            val player = getPlayer() ?: return
                            val inventory = player.inventory
                            val slot = player.selectedSlot + player.hotBarStartIndex
                            val state = inventory.slots[slot].state
                            if (state.block != AirBlock) {
                                setBlock(hovered.previousPosition, state, hovered.previousBlock)
                            }
                        }
                    }
                }
            }
        }

    }

    override fun onEscapeKey(x: Float, y: Float) {
        GFX.trapMousePanel = null
    }

    override fun onCharTyped(x: Float, y: Float, key: Int) {
        when (val char = key.toChar()) {
            't', 'T' -> {
                // open console
                val console = instance.console
                console.show()
                console.requestFocus()
            }
            'm', 'M' -> {
                LOGGER.info(
                    "" +
                            "Geometry: ${Buffer.allocated.formatFileSize()}, " +
                            "Textures: ${Texture2D.allocated.formatFileSize()}"
                )
            }
            in '1'..'9' -> getPlayer()?.selectSlot(char - '1')
            else -> super.onCharTyped(x, y, key)
        }
    }

    companion object {
        private val LOGGER = LogManager.getLogger(DimensionView::class)
    }

}