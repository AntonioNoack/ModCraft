package me.anno.blocks.world

import me.anno.blocks.block.BlockInfo
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.chunk.Chunk
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CSBits
import me.anno.blocks.chunk.Chunk.Companion.CSm1
import me.anno.blocks.chunk.lighting.BakeLight
import me.anno.blocks.entity.player.Player
import me.anno.blocks.rendering.RenderData
import me.anno.blocks.rendering.RenderPass
import me.anno.blocks.rendering.ShaderLib2.skyShader
import me.anno.blocks.rendering.SkyShader
import me.anno.blocks.rendering.SolidShader
import me.anno.blocks.utils.floorToJnt
import me.anno.blocks.utils.struct.Vector3j
import me.anno.blocks.world.generator.Generator
import me.anno.blocks.world.generator.RemoteGenerator
import me.anno.gpu.GFX
import me.anno.objects.models.CubemapModel
import me.anno.utils.Color.toVecRGBA
import me.anno.utils.ColorParsing.parseColor
import me.anno.utils.Maths.sq
import me.anno.utils.Sleep.sleepShortly
import me.anno.utils.hpc.ProcessingGroup
import org.joml.*
import kotlin.math.*

class Dimension(val world: World, val id: String, val generator: Generator) {

    val chunks = HashMap<Vector3j, Chunk>()

    val generatorWorker = ProcessingGroup("Generator-Terrain", 0.3f).apply { start() }
    val lightingWorker = ProcessingGroup("Generator-Light", 0.3f).apply { start() }

    val skyColor = parseColor("#bde0ec")!!.toVecRGBA()

    val hasBlocksBelowZero = generator.hasBlocksBelowZero

    private fun get(coordinates: Vector3j): Chunk? {
        return synchronized(chunks) { chunks[coordinates] }
    }

    private fun getOrPut(coordinates: Vector3j, callback: (Chunk) -> Unit): Chunk {
        return synchronized(chunks) {
            chunks.getOrPut(coordinates) {
                // if(coordinates.y > 9) throw IllegalArgumentException("Don't fly that high!")
                Chunk(this, coordinates).apply(callback)
            }
        }
    }

    fun getChunkAt(c: Vector3i, wantFinished: Boolean): Chunk? {
        return getChunk(
            Vector3j(
                c.x.and(CSm1).shr(CSBits),
                c.y.and(CSm1).shr(CSBits),
                c.z.and(CSm1).shr(CSBits)
            ), wantFinished
        )
    }

    fun getChunkAt(c: Vector3j, wantFinished: Boolean): Chunk? {
        return getChunk(
            Vector3j(c.x.and(CSm1).shr(CSBits), c.y.and(CSm1).shr(CSBits), c.z.and(CSm1).shr(CSBits)),
            wantFinished
        )
    }

    fun getChunkAt(c: Vector3d, wantFinished: Boolean): Chunk? {
        return getChunk(Vector3d(c).div(CS.toDouble()).floorToJnt(), wantFinished)
    }

    fun getChunk(c: Vector3i, wantFinished: Boolean): Chunk? = getChunk(Vector3j(c), wantFinished)

    fun getChunk(c: Vector3j, wantFinished: Boolean): Chunk? {
        return if (hasBlocksBelowZero || c.y >= 0) {
            val chunk = getOrPut(c) {
                // a worker could do this...
                generatorWorker.start()
                generatorWorker += {
                    val chunk = generator.getFinishedChunk(c)!!
                    if(generator !is RemoteGenerator && BakeLight.EnableLighting){
                        lightingWorker += { chunk.calculateLightMap() }
                    }
                }
            }
            if(wantFinished && !chunk.isFinished) GFX.checkIsNotGFXThread()
            while (wantFinished && !chunk.isFinished) sleepShortly()
            chunk
        } else null
    }

    fun getChunkMaybe(coordinates: Vector3j, wantFinished: Boolean): Chunk? {
        return if (hasBlocksBelowZero || coordinates.y >= 0) {
            val chunk = get(coordinates) ?: return null
            while (wantFinished && !chunk.isFinished) sleepShortly()
            chunk
        } else null
    }

    fun getBlocks(bounds: AABBd, finished: Boolean): List<BlockInfo>? {
        val minX = floor(bounds.minX).toInt()
        val minY = floor(bounds.minY).toInt()
        val minZ = floor(bounds.minZ).toInt()
        val maxX = ceil(bounds.maxX).toInt()
        val maxY = ceil(bounds.maxY).toInt()
        val maxZ = ceil(bounds.maxZ).toInt()
        val blockCount = max(0, maxX - minX) * max(0, maxY - minY) * max(0, maxZ - minZ)
        if (blockCount <= 0) return null
        val list = ArrayList<BlockInfo>(blockCount)
        for (y in minY until maxY) {
            for (x in minX until maxX) {
                for (z in minZ until maxZ) {
                    val chunk = getChunk(Vector3j(x shr CSBits, y shr CSBits, z shr CSBits), finished) ?: continue
                    val block = chunk.getBlockState(x, y, z)
                    if (block != Air) {
                        list.add(BlockInfo(world, this, chunk, Vector3j(x, y, z), block))
                    }
                }
            }
        }
        return list
    }

    fun getLightAt(x: Int, y: Int, z: Int): Short {
        return getChunkAt(x, y, z, true)?.getLight(x, y, z) ?: 0
    }

    fun getBlockInfo(position: Vector3j, wantFinished: Boolean = true) =
        getChunkAt(position, wantFinished)?.getBlockInfo(position)

    fun getBlockInfo(position: Vector3i, wantFinished: Boolean = true) =
        getChunkAt(position, wantFinished)?.getBlockInfo(position.x, position.y, position.z)

    fun getBlock(position: Vector3d, finished: Boolean) =
        getBlock(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z()).toInt(), finished)

    fun getBlock(position: Vector3f, finished: Boolean) =
        getBlock(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z()).toInt(), finished)

    fun getBlock(position: Vector3j, finished: Boolean) =
        getBlock(position.x, position.y, position.z, finished)

    fun getBlock(x: Int, y: Int, z: Int, finished: Boolean): BlockState {
        return getChunkAt(x, y, z, finished)?.getBlockState(x, y, z) ?: Air
    }

    fun getChunkAt(x: Int, y: Int, z: Int, finished: Boolean) =
        getChunk(Vector3j(x shr CSBits, y shr CSBits, z shr CSBits), finished)

    fun getChunkAtMaybe(x: Int, y: Int, z: Int, finished: Boolean) =
        getChunkMaybe(Vector3j(x shr CSBits, y shr CSBits, z shr CSBits), finished)

    fun getChunkAtMaybe(c: Vector3j, finished: Boolean) =
        getChunkMaybe(Vector3j(c.x shr CSBits, c.y shr CSBits, c.z shr CSBits), finished)

    fun getChunkAtMaybe(c: Vector3i, finished: Boolean) =
        getChunkMaybe(Vector3j(c.x shr CSBits, c.y shr CSBits, c.z shr CSBits), finished)

    fun setBlock(position: Vector3i, finished: Boolean, block: BlockState) {
        setBlock(position.x, position.y, position.z, finished, block)
    }

    fun setBlock(position: Vector3j, finished: Boolean, block: BlockState) {
        setBlock(position.x, position.y, position.z, finished, block)
    }

    fun setBlock(x: Int, y: Int, z: Int, finished: Boolean, block: BlockState) {
        val chunk = getChunkAt(x, y, z, finished) ?: return
        if (finished) {
            val xMask = x.and(CSm1)
            val yMask = y.and(CSm1)
            val zMask = z.and(CSm1)
            // update neighbor chunks
            if (xMask == 0) getChunkAtMaybe(x - 1, y, z, false)?.invalidate()
            if (yMask == 0) getChunkAtMaybe(x, y - 1, z, false)?.invalidate()
            if (zMask == 0) getChunkAtMaybe(x, y, z - 1, false)?.invalidate()
            if (xMask == CSm1) getChunkAtMaybe(x + 1, y, z, false)?.invalidate()
            if (yMask == CSm1) getChunkAtMaybe(x, y + 1, z, false)?.invalidate()
            if (zMask == CSm1) getChunkAtMaybe(x, y, z + 1, false)?.invalidate()
        }
        chunk.setBlock(x, y, z, block)
    }

    val sunDir = Vector3f(0.4f, -0.7f, 0.2f).normalize()
    val sunLight = Vector3f(0.5f)
    val baseLight = Vector3f(0.2f + 0.5f)

    val chunksByDistance = ChunksByDistance(10)
    val centerChunkCoordinates = Vector3i()
    val positionWithOffset = Vector2d()

    fun prepareChunks(data: RenderData, player: Player) {

        val position = player.position
        val centerChunkCoordinates = centerChunkCoordinates
        centerChunkCoordinates.x = (position.x / CS).roundToInt()
        centerChunkCoordinates.y = (position.y / CS).roundToInt()
        centerChunkCoordinates.z = (position.z / CS).roundToInt()

        positionWithOffset.set(position.x, position.z)
        positionWithOffset.div(32.0)
        positionWithOffset.sub(0.5,0.5)

        val chunkCoordinates = Vector3i()

        var updates = 1
        val time = GFX.gameTime

        chunksByDistance.update(centerChunkCoordinates)
        chunksByDistance.resetForRendering()

        val maxDistanceSq = sq(chunksByDistance.maxDistance)

        val values = chunksByDistance.values
        for (index in values.indices) {
            val delta = values[index]
            chunkCoordinates.set(centerChunkCoordinates).add(delta)
            if (hasBlocksBelowZero || chunkCoordinates.y >= 0) {
                val distanceSq = positionWithOffset.distanceSquared(
                    chunkCoordinates.x.toDouble(),
                    chunkCoordinates.z.toDouble()
                )
                if(distanceSq < maxDistanceSq){
                    var chunk: Chunk? = chunksByDistance.getChunk(index)
                    if (chunk == null) chunk = synchronized(chunks) { chunks[Vector3j(chunkCoordinates)] }
                    if (chunk == null) {
                        updates--
                        if (updates >= 0) {
                            chunk = getChunk(chunkCoordinates, false)
                        }
                    }
                    if (chunk != null) {
                        chunksByDistance.setChunk(index, chunk)
                        chunk.lastRendered = time
                        if (chunk.shouldBeDrawn(data)) {
                            chunksByDistance.pushForRendering(chunk)
                        }
                    }
                }
            }
        }

    }

    var isInsideRocks = false
    var isUnderWater = false

    fun findSpecialRenderingConditions(data: RenderData) {
        val isInsideVoid = data.cameraPosition.y < 0f && !hasBlocksBelowZero
        if (isInsideVoid) {
            isInsideRocks = true
            isUnderWater = false
        } else {
            val blockAtPosition = getBlock(data.cameraPosition, false)
            isInsideRocks = isInsideVoid || blockAtPosition
                .run { this != Air && block.visuals.materialType == MaterialType.SOLID_BLOCK }
            isUnderWater = blockAtPosition
                .run { this != Air && block.visuals.materialType == MaterialType.TRANSPARENT_MASS }
        }
    }

    fun prepareShader(data: RenderData, shader: SkyShader) {

        shader.use()

        val tintColor = if (isUnderWater) waterColor else if (isInsideRocks) rockColor else white
        val tintAlpha = if (isUnderWater) 0.5f else if (isInsideRocks) 0.75f else 0f
        shader.v4(shader.tint, tintColor, tintAlpha)

        shader.v3(shader.sunDir, sunDir)
        shader.v3(shader.sunLight, sunLight)
        shader.v3(shader.baseLight, baseLight)

        shader.v3(shader.topColor, 0f / 255f, 41f / 255f, 178f / 255f)
        shader.v3(shader.midColor, 176 / 255f, 200 / 255f, 206f / 25f)
        shader.v3(shader.bottomColor, 255f / 255f, 249f / 255f, 199f / 255f)

    }

    fun prepareShader(data: RenderData, shader: SolidShader) {

        shader.use()

        val tintColor = if (isUnderWater) waterColor else if (isInsideRocks) rockColor else white
        val tintAlpha = if (isUnderWater) 0.5f else if (isInsideRocks) 0.75f else 0f
        shader.v4(shader.tint, tintColor, tintAlpha)

        shader.v3(shader.sunDir, sunDir)
        shader.v3(shader.sunLight, sunLight)
        shader.v3(shader.baseLight, baseLight)
        shader.v1(shader.fogFactor, if (isUnderWater || isInsideRocks) 1f / 20f else 1 / 100f)

        shader.m4x4(shader.matrix, data.matrix)

    }

    /**
     * unload all chunks, where we no longer are
     * */
    fun unloadChunks(data: RenderData) {
        val time = GFX.gameTime
        val seconds = 1_000_000_000L
        val timeout = 5 * seconds
        val maxDistance: Double = CS * (chunksByDistance.maxDistance + 3) * sqrt(3.0)
        val maxDistanceSq: Double = sq(maxDistance)
        synchronized(chunks) {
            val shallBeRemoved = chunks.values.filter { chunk ->
                abs(chunk.lastRendered - time) > timeout &&
                        chunk.center.distanceSquared(data.cameraPosition) > maxDistanceSq
            }.toList()
            for (chunk in shallBeRemoved) {
                chunks.remove(chunk.coordinates)
            }
            shallBeRemoved.forEach { chunk ->
                // if(chunks.containsKey(chunk.coordinates)) throw IllegalStateException()
                // LOGGER.info("Destroyed ${chunk.coordinates}")
                chunk.destroy()
            }
        }
    }

    val waterColor = 0x9ee5ff
    val white = -1
    val rockColor = 0

    fun drawSky(data: RenderData) {

        val shader = skyShader
        shader.use()

        val matrix = data.matrix
        matrix.pushMatrix()
        matrix.scale(1f)

        shader.m4x4("matrix", matrix)

        val cube = CubemapModel.cubemapModel
        cube.draw(shader)

        matrix.popMatrix()

    }

    /**
     * draw the dimension onto the screen
     * */
    fun draw(data: RenderData, pass: RenderPass) {

        GFX.check()

        val shader = pass.shader
        shader.use()

        GFX.check()

        data.lastTexture = null

        GFX.check()

        for (index in 0 until chunksByDistance.renderingIndex) {
            val chunk = chunksByDistance.getChunkForRendering(index)
            chunk.draw(data, pass)
        }

        GFX.check()

    }

    // todo ray tracer always go one step

}