package me.anno.blocks.chunk

import com.bulletphysics.dynamics.RigidBody
import me.anno.blocks.block.BlockInfo
import me.anno.blocks.block.BlockSide
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.base.AirBlock
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.block.visual.MaterialType.Companion.MaterialTypeCount
import me.anno.blocks.chunk.mesh.BakeMesh.bakeMesh
import me.anno.blocks.chunk.mesh.BakeMesh.hasStatesOnSide
import me.anno.blocks.chunk.lighting.BakeLight
import me.anno.blocks.chunk.lighting.LightInfo
import me.anno.blocks.chunk.mesh.MeshInfo
import me.anno.blocks.chunk.ramless.ChunkContent
import me.anno.blocks.chunk.ramless.SimplestChunkContent
import me.anno.blocks.entity.Entity
import me.anno.blocks.entity.ItemStackEntity
import me.anno.blocks.item.ItemStack
import me.anno.blocks.rendering.RenderData
import me.anno.blocks.rendering.RenderPass
import me.anno.blocks.rendering.SolidShader
import me.anno.blocks.utils.struct.Vector3j
import me.anno.blocks.world.Dimension
import me.anno.gpu.GFX
import me.anno.gpu.buffer.DrawLinesBuffer.drawLines
import me.anno.utils.Maths.clamp
import me.anno.utils.Maths.sq
import org.apache.logging.log4j.LogManager
import org.joml.*
import kotlin.concurrent.thread

class Chunk(val dimension: Dimension, val coordinates: Vector3j) {

    val center: Vector3dc = coordinates.getBlockCenter().mul(CS.toDouble())
    val innerCorner = Vector3j(coordinates.x * CS, coordinates.y * CS, coordinates.z * CS)

    var wasChanged = false

    var content: ChunkContent = SimplestChunkContent(Air)
    var lightMap = LightInfo()

    val culling = ChunkCulling(this)

    fun optimizeMaybe() {
        content = content.optimize()
    }

    fun calculateLightMap() {
        if (lightMap.isValid) return
        lightMap.isCalculating = true
        BakeLight.calculateLightValues(dimension, coordinates, 1, lightMap)
        lightMap.isValid = true
        lightMap.isCalculating = false
        // LOGGER.info("Calculated lightmap for $coordinates")
    }

    val isVisuallyAir: Boolean
        get() {
            val content = content
            return content is SimplestChunkContent && content.state.block == AirBlock
        }

    fun getAllBlocks() = content.getAllBlocks()

    fun getBlockState(index: Int) = content.getBlockState(index)
    fun getBlockState(coordinates: Vector3i): BlockState = content.getBlockState(getIndex(coordinates))
    fun getBlockState(coordinates: Vector3j): BlockState = content.getBlockState(getIndex(coordinates))
    fun getBlockState(x: Int, y: Int, z: Int): BlockState = getBlockState(getIndex(x, y, z))

    fun getLight(index: Int) = lightMap.getLightLevel(index)
    fun getLight(x: Int, y: Int, z: Int) = getLight(getIndex(x, y, z))

    fun getBlockInfo(index: Int): BlockInfo {
        val world = dimension.world
        val c = coordinates
        val position = Vector3j(
            c.x.shl(CSBits) + getX(index),
            c.y.shl(CSBits) + getY(index),
            c.z.shl(CSBits) + getZ(index)
        )
        return BlockInfo(world, dimension, this, position, getBlockState(index))
    }

    fun getBlockInfo(x: Int, y: Int, z: Int): BlockInfo = getBlockInfo(getIndex(x, y, z))

    fun getBlockInfo(coordinates: Vector3j): BlockInfo =
        getBlockInfo(getIndex(coordinates.x, coordinates.y, coordinates.z))

    fun setBlock(index: Int, state: BlockState) {
        val oldContent = content
        content = content.setBlock(index, state)
        wasChanged = oldContent != content || content.wasChanged
        content.wasChanged = false
    }

    fun fill(newState: BlockState) {
        val oldContent = content
        content = content.fill(newState)
        wasChanged = oldContent != content || content.wasChanged
        content.wasChanged = false
    }

    fun fillY(newState: BlockState, y: Int) {
        val oldContent = content
        content = content.fillY(newState, y)
        wasChanged = oldContent != content || content.wasChanged
        content.wasChanged = false
    }

    fun setBlock(x: Int, y: Int, z: Int, state: BlockState) {
        setBlock(getIndex(x, y, z), state)
    }

    fun setBlock(coordinates: Vector3i, state: BlockState) {
        setBlock(getIndex(coordinates), state)
    }

    fun setBlock(coordinates: Vector3j, state: BlockState) {
        setBlock(getIndex(coordinates), state)
    }

    var hasValidMesh = BooleanArray(6 * MaterialTypeCount) { false }
    val meshes = arrayOfNulls<MeshInfo>(6 * MaterialTypeCount)

    fun createMesh(side: BlockSide, materialType: MaterialType, index: Int) {

        hasValidMesh[index] = true

        val allBlockStates = content.getAllBlockTypes()
        if (allBlockStates.any { it != Air && it.block.visuals.materialType == materialType }) {
            val blockStates = getAllBlocks()
            thread(name = "CreateMesh $side $coordinates $materialType") {
                // LOGGER.info("$side $coordinates $materialType")
                var neighborChunk: Chunk? = null
                // performance improvement: for 1331 chunks, 430k vs 550k -> 20% improvement :)
                if (materialType == MaterialType.SOLID_BLOCK) {
                    neighborChunk = dimension.getChunk(coordinates + side.normalI, true)
                }
                // visual improvement and performance improvement
                if (materialType == MaterialType.TRANSPARENT_MASS) {
                    if (hasStatesOnSide(blockStates, materialType, side)) {
                        neighborChunk = dimension.getChunk(coordinates + side.normalI, true)
                    }
                }
                meshes[index] = bakeMesh(side, this, neighborChunk, blockStates, materialType, meshes[index])
            }
        } else {
            meshes[index] = null
        }

        // todo first create the simple mesh, and later optimize
        // todo use the code from ModCraft1 for collisions

    }

    fun shouldBeDrawn(data: RenderData): Boolean {

        if (isVisuallyAir) return false

        val matrix = data.matrix
        if (!culling.isVisible(matrix, data.cameraPosition, data.cameraRotation)) return false

        if (!isFinished) return false

        if (wasChanged) {
            invalidate()
            wasChanged = false
        }

        return true

    }

    var hasCorrectTransform = false
    fun draw(data: RenderData, pass: RenderPass) {

        GFX.check()

        // drawDebugCube(data, center)

        val materialType = pass.materialType
        val baseId = materialType.id * 6

        val shader = pass.shader
        shader.use()

        hasCorrectTransform = false
        for (side in BlockSide.values2) {

            val sideId = side.id
            // check which sides are needed of this chunk to save memory and calculation time
            // efficiency ~ 2x, more like 1.7x less triangles
            if (culling.sideIsActive[sideId]) {

                val index = baseId + sideId
                if (!hasValidMesh[index]) createMesh(side, materialType, index)

                val meshInfo = meshes[index]
                if (meshInfo != null) drawMeshInfo(data, shader, meshInfo)

            }

        }

        GFX.check()

    }

    var startTime = 0L

    fun getTimeAlive(): Float {
        if(startTime == 0L) startTime = GFX.gameTime
        return (GFX.gameTime - startTime) / 1e9f
    }

    fun drawMeshInfo(data: RenderData, shader: SolidShader, meshInfo: MeshInfo) {
        var alpha = clamp(getTimeAlive(), 0f, 1f)
        alpha = 1f-sq(1f-alpha)
        if(alpha < 1f/255f) return
        shader.v1(shader.alpha, alpha)
        for (index in 0 until meshInfo.length) {

            val buffer = meshInfo.getBuffer(index)
            meshInfo.bindPath(data, index)

            if (!hasCorrectTransform) {
                updateTransform(data, shader)
                hasCorrectTransform = true
            }

            GFX.check()

            if (data.renderLines) {
                buffer.bind(shader)
                drawLines(buffer.drawLength)
                GFX.check()
            } else {
                buffer.draw(shader)
                GFX.check()
            }

            data.chunkTriangles += buffer.drawLength
            data.chunkBuffers++
        }
    }

    fun updateTransform(data: RenderData, shader: SolidShader) {
        val delta = coordinates.set(data.delta).mul(CS.toDouble()).sub(data.cameraPosition)
        val dx = delta.x.toFloat()
        val dy = delta.y.toFloat()
        val dz = delta.z.toFloat()
        shader.v3(shader.offset, dx, dy, dz)
    }

    val entities = HashSet<Entity>()
    var stage = 0

    var isFinished = false
        private set

    fun finish() {
        isFinished = true
        for (i in hasValidMesh.indices) hasValidMesh[i] = false
        validate()
    }

    fun validate() {
        wasChanged = false
    }

    fun invalidate() {
        isFinished = true
        wasChanged = true
        for (i in hasValidMesh.indices) hasValidMesh[i] = false
    }

    var rigidBody: RigidBody? = null

    var lastRendered = GFX.gameTime

    fun drop(info: BlockInfo) {
        entities += ItemStackEntity(ItemStack(info.state), dimension, info.coordinates.getBlockCenter())
    }

    fun isSolid(index: Int) = content.isSolid(index)

    fun isSolid(x: Int, y: Int, z: Int) = isSolid(getIndex(x, y, z))

    fun destroy() {
        meshes.forEach { meshInfo -> meshInfo?.destroy() }
    }

    companion object {

        val vs = Array(8) { Vector4f() }.toList()

        const val FIRST_SOLID = 0
        const val uFIRST_SOLID: UByte = 0u

        const val FIRST_TRANS = 128
        const val uFIRST_TRANS: UByte = 128u

        const val AIR = 254
        const val uAIR: UByte = 254u

        const val OTHER = 255
        const val uOTHER: UByte = 255u

        const val SOLID_SIZE = FIRST_TRANS - FIRST_SOLID
        const val TRANS_SIZE = AIR - FIRST_TRANS

        const val CSBits = 5
        const val CS = 1 shl CSBits
        const val CSm1 = CS - 1
        const val CS2 = CS * CS
        const val CS2m1 = CS2 - 1
        const val CS3 = CS2 * CS

        const val dx = CS
        const val dy = CS2
        const val dz = 1

        fun getIndex(x: Int, y: Int, z: Int): Int {
            val xs = x.and(CSm1)
            val ys = y.and(CSm1)
            val xy = xs + (ys shl CSBits)
            return (xy shl CSBits) + z.and(CSm1)
        }

        const val CSx2Bits = CSBits + 1
        const val CSx2 = CS + CS
        const val CSx2m1 = CSx2 - 1

        fun getIndex(coordinates: Vector3i): Int {
            return getIndex(coordinates.x, coordinates.y, coordinates.z)
        }

        fun getIndex(coordinates: Vector3j): Int {
            return getIndex(coordinates.x, coordinates.y, coordinates.z)
        }

        fun getSizeInfo(x: Int, y: Int, z: Int): Int {
            val xy = x + (y shl CSx2Bits)
            return (xy shl CSx2Bits) + z
        }

        fun getX(i: Int) = (i shr CSBits) and CSm1
        fun getY(i: Int) = (i shr (CSBits * 2)) and CSm1
        fun getZ(i: Int) = i and CSm1

        fun getXf(i: Int) = ((i shr CSBits) and CSm1).toFloat()
        fun getYf(i: Int) = ((i shr (CSBits * 2)) and CSm1).toFloat()
        fun getZf(i: Int) = (i and CSm1).toFloat()

        fun getXd(i: Int) = ((i shr CSBits) and CSm1).toDouble()
        fun getYd(i: Int) = ((i shr (CSBits * 2)) and CSm1).toDouble()
        fun getZd(i: Int) = (i and CSm1).toDouble()

        fun getVec3i(i: Int) = Vector3i(getX(i), getY(i), getZ(i))
        fun getVec3f(i: Int) = Vector3f(getXf(i), getYf(i), getZf(i))
        fun getVec3d(i: Int) = Vector3d(getXd(i), getYd(i), getZd(i))

        val half = Vector3d(0.5)

        private val LOGGER = LogManager.getLogger(Chunk::class)

    }

}