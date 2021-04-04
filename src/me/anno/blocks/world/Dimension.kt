package me.anno.blocks.world

import me.anno.blocks.block.BlockInfo
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.chunk.Chunk
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CSBits
import me.anno.blocks.chunk.Chunk.Companion.CSm1
import me.anno.blocks.entity.player.Player
import me.anno.blocks.rendering.RenderData
import me.anno.blocks.rendering.RenderPass
import me.anno.blocks.rendering.ShaderLib2.skyShader
import me.anno.blocks.utils.floorToJnt
import me.anno.blocks.utils.struct.Vector3j
import me.anno.blocks.world.ChunksByDistance.chunksByDistance
import me.anno.blocks.world.generator.Generator
import me.anno.gpu.GFX
import me.anno.gpu.shader.Shader
import me.anno.objects.models.CubemapModel
import me.anno.utils.Color.toVecRGBA
import me.anno.utils.ColorParsing.parseColor
import me.anno.utils.LOGGER
import me.anno.utils.Maths.sq
import me.anno.utils.Sleep.sleepShortly
import me.anno.utils.types.Vectors.toVec3f
import org.joml.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.*

class Dimension(val world: World, val id: String, val generator: Generator) {

    val chunks = HashMap<Vector3j, Chunk>()

    val skyColor = parseColor("#bde0ec")!!.toVecRGBA()

    val hasBlocksBelowZero = generator.hasBlocksBelowZero

    private fun get(coordinates: Vector3j): Chunk? {
        return synchronized(chunks) { chunks[coordinates] }
    }

    private fun getOrPut(coordinates: Vector3j, callback: (Chunk) -> Unit): Chunk {
        return synchronized(chunks) {
            chunks.getOrPut(coordinates) {
                if (coordinates in requestedChunks) throw RuntimeException()
                requestedChunks.add(coordinates)
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

    fun getChunkAt(coordinates: Vector3d, wantFinished: Boolean): Chunk? {
        return getChunk(Vector3d(coordinates).div(CS.toDouble()).floorToJnt(), wantFinished)
    }

    fun getChunk(c: Vector3i, wantFinished: Boolean): Chunk? = getChunk(Vector3j(c), wantFinished)

    fun getChunk(coordinates: Vector3j, wantFinished: Boolean): Chunk? {
        return if (hasBlocksBelowZero || coordinates.y >= 0) {
            val chunk = getOrPut(coordinates) {
                thread { generator.getFinishedChunk(coordinates) }
            }
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

    fun setBlock(coordinates: Vector3i, finished: Boolean, block: BlockState) {
        getChunkAt(coordinates, finished)?.setBlock(coordinates, block)
    }

    fun setBlock(coordinates: Vector3j, finished: Boolean, block: BlockState) {
        getChunkAt(coordinates, finished)?.setBlock(coordinates, block)
    }

    fun setBlock(x: Int, y: Int, z: Int, finished: Boolean, block: BlockState) {
        getChunkAt(x, y, z, finished)?.setBlock(x, y, z, block)
    }

    val sunDir = Vector3f(0.4f, -0.7f, 0.2f).normalize()
    val sunLight = Vector3f(0.5f)
    val baseLight = Vector3f(0.2f + 0.5f)

    val chunkList = arrayOfNulls<Chunk>(chunksByDistance.size)

    val centerChunkCoordinates = Vector3i()

    fun prepareChunks(data: RenderData, player: Player) {

        var index = 0

        val position = player.position
        val centerChunkCoordinates = centerChunkCoordinates
        centerChunkCoordinates.x = (position.x / CS).roundToInt()
        centerChunkCoordinates.y = (position.y / CS).roundToInt()
        centerChunkCoordinates.z = (position.z / CS).roundToInt()

        val chunkCoordinates = Vector3i()

        var updates = 1
        val time = GFX.gameTime
        for (delta in chunksByDistance) {
            chunkCoordinates.set(centerChunkCoordinates).add(delta)
            if (hasBlocksBelowZero || chunkCoordinates.y >= 0) {
                var chunk = synchronized(chunks) { chunks[Vector3j(chunkCoordinates)] }
                if (chunk == null) {
                    updates--
                    if (updates >= 0) {
                        chunk = getChunk(chunkCoordinates, false)
                    }
                }
                if (chunk != null) {
                    chunk.lastRendered = time
                    if (chunk.shouldBeDrawn(data)) {
                        chunkList[index++] = chunk
                    }
                }
            }
        }

        if (index < chunkList.size) chunkList[index] = null

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

    fun prepareShader(data: RenderData, shader: Shader) {

        shader.use()

        val tintColor = if (isUnderWater) waterColor else if (isInsideRocks) rockColor else white
        shader.v4(
            "tint",
            tintColor.x,
            tintColor.y,
            tintColor.z,
            if (isUnderWater) 0.5f else if (isInsideRocks) 0.75f else 0f
        )

        shader.v3("sunDir", sunDir)
        shader.v3("sunLight", sunLight)
        shader.v3("baseLight", baseLight)
        shader.v1("fogFactor", if (isUnderWater || isInsideRocks) 1f / 20f else 1 / 100f)

        shader.v3("topColor", 0f / 255f, 41f / 255f, 178f / 255f)
        shader.v3("midColor", 176 / 255f, 200 / 255f, 206f / 25f)
        shader.v3("bottomColor", 255f / 255f, 249f / 255f, 199f / 255f)

    }

    val requestedChunks = HashSet<Vector3j>()

    /**
     * unload all chunks, where we no longer are
     * */
    fun unloadChunks(data: RenderData) {
        val time = GFX.gameTime
        val seconds = 1_000_000_000L
        val timeout = 5 * seconds
        val maxDistance: Double = CS * (ChunksByDistance.maxDistance + 3) * sqrt(3.0)
        val maxDistanceSq: Double = sq(maxDistance)
        synchronized(chunks) {
            val shallBeRemoved = chunks.values.filter { chunk ->
                abs(chunk.lastRendered - time) > timeout &&
                        chunk.center.distanceSquared(data.cameraPosition) > maxDistanceSq
            }.toList()
            for (entry in shallBeRemoved) {
                chunks.remove(entry.coordinates)
            }
            shallBeRemoved.forEach { chunk ->
                LOGGER.info("Destroyed ${chunk.coordinates}")
                requestedChunks.remove(chunk.coordinates)
                chunk.destroy()
            }
        }
    }

    val waterColor = parseColor("#9ee5ff")!!.toVecRGBA().toVec3f().mul(0.5f)
    val white = Vector3f(1f)
    val rockColor = Vector3f(0f)

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

        val shader = pass.shader
        shader.use()

        for (chunk in chunkList) {
            if (chunk == null) break
            chunk.draw(data, pass)
        }

    }

    fun requestSlot() {
        while (requestSlots.getAndDecrement() <= 0) {
            requestSlots.incrementAndGet()
            Thread.sleep(1)
        }
    }

    val requestSlots = AtomicInteger(16)

    fun unlockRequestSlot() {
        requestSlots.incrementAndGet()
    }

    // todo ray tracer always go one step

}