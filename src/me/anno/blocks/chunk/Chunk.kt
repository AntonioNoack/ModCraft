package me.anno.blocks.chunk

import com.bulletphysics.dynamics.RigidBody
import me.anno.blocks.block.BlockInfo
import me.anno.blocks.block.BlockSide
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.base.AirBlock
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.block.visual.MaterialType.Companion.MaterialTypeCount
import me.anno.blocks.chunk.BakeMesh.bakeMesh
import me.anno.blocks.chunk.BakeMesh.hasStatesOnSide
import me.anno.blocks.chunk.mesh.MeshInfo
import me.anno.blocks.chunk.ramless.ChunkContent
import me.anno.blocks.chunk.ramless.SimplestChunkContent
import me.anno.blocks.entity.Entity
import me.anno.blocks.entity.ItemStackEntity
import me.anno.blocks.item.ItemStack
import me.anno.blocks.rendering.RenderData
import me.anno.blocks.rendering.RenderPass
import me.anno.blocks.utils.add
import me.anno.blocks.utils.struct.Vector3j
import me.anno.blocks.world.Dimension
import me.anno.cache.instances.ImageCache
import me.anno.gpu.GFX
import me.anno.gpu.GFX.checkIsGFXThread
import me.anno.gpu.TextureLib.whiteTexture
import me.anno.gpu.buffer.DrawLinesBuffer.drawLines
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.utils.Clipping
import org.joml.*
import kotlin.concurrent.thread

class Chunk(val dimension: Dimension, val coordinates: Vector3j) {

    val x0 = coordinates.x * CS
    val x1 = x0 + CS
    val y0 = coordinates.y * CS
    val y1 = y0 + CS
    val z0 = coordinates.z * CS
    val z1 = z0 + CS

    val center: Vector3dc = coordinates.getBlockCenter().mul(CS.toDouble())

    var wasChanged = false

    var content: ChunkContent = SimplestChunkContent(Air)

    fun optimizeMaybe() {
        content = content.optimize()
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
            thread {
                var neighborChunk: Chunk? = null
                // performance improvement: for 1331 chunks, 430k vs 550k -> 20% improvement :)
                if (materialType == MaterialType.SOLID_BLOCK) {
                    neighborChunk = dimension.getChunk(coordinates.mutable().add(side.normalI), true)
                }
                // visual improvement and performance improvement
                if (materialType == MaterialType.TRANSPARENT_MASS) {
                    if (hasStatesOnSide(blockStates, materialType, side)) {
                        neighborChunk = dimension.getChunk(coordinates.mutable().add(side.normalI), true)
                    }
                }
                meshes[index] = bakeMesh(side, neighborChunk, blockStates, materialType, meshes[index])
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
        if (!isVisible(matrix, data.cameraPosition)) return false

        if (!isFinished) return false

        if (wasChanged) {
            invalidate()
            wasChanged = false
        }

        return true

    }

    fun draw(data: RenderData, pass: RenderPass) {

        val matrix = data.matrix
        if (!isVisible(matrix, data.cameraPosition)) return

        if (!isFinished) return

        if (wasChanged) {
            invalidate()
            wasChanged = false
        }

        GFX.check()

        // drawDebugCube(data, center)

        val materialType = pass.materialType
        val baseId = materialType.id * 6

        val shader = pass.shader
        shader.use()

        GFX.check()

        matrix.pushMatrix()

        val delta = coordinates.set(data.delta).mul(CS.toDouble()).sub(data.cameraPosition)
        val centerDelta = data.centerDelta.set(center).sub(data.cameraPosition)
        matrix.translate(delta.x.toFloat(), delta.y.toFloat(), delta.z.toFloat())

        GFX.check()

        shader.m4x4("matrix", matrix)
        shader.v3("camPosition", delta.x.toFloat(), delta.y.toFloat(), delta.z.toFloat())

        GFX.check()

        for (side in BlockSide.values2) {
            val sideId = side.id
            // check which sides are needed of this chunk to save memory and calculation time
            // efficiency ~ 2x, more like 1.7x less triangles
            if (side.normalD.dot(centerDelta) > CS * 0.5f) {
                continue
            }
            val index = baseId + sideId
            if (!hasValidMesh[index]) createMesh(side, materialType, index)
            val meshInfo = meshes[index]
            if (meshInfo != null) {
                for (i in 0 until meshInfo.length) {
                    val path = meshInfo.getPath(i)
                    val buffer = meshInfo.getBuffer(i)
                    if (path != data.lastTexture) {

                        GFX.check()

                        val texture = ImageCache.getInternalTexture(path, true) ?: whiteTexture
                        texture.bind(GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
                        data.lastTexture = path

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
        }

        GFX.check()

        matrix.popMatrix()

    }

    val entities = HashSet<Entity>()
    var stage = 0

    var isFinished = false
        private set(value) {
            field = value
        }

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

    var lastRendered = 0L

    fun drop(info: BlockInfo) {
        entities += ItemStackEntity(ItemStack(info.state), dimension, info.coordinates.getBlockCenter())
    }

    fun Vector4f(x: Double, y: Double, z: Double, w: Double) =
        Vector4f(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())

    fun Vector4f(x: Double, y: Double, z: Double) = Vector4f(x.toFloat(), y.toFloat(), z.toFloat(), 1f)

    fun isVisible(matrix: Matrix4f, position: Vector3d): Boolean {

        checkIsGFXThread()

        fun getPoint(x: Int, y: Int, z: Int, i: Int): Vector4f? {
            val vec = vs[i]
            vec.set(x - position.x, y - position.y, z - position.z, 1.0)
            matrix.transformProject(vec, vec)
            return if (vec.x in -1f..1f && vec.y in -1f..1f && vec.z in -1f..1f) null
            else vec
        }

        getPoint(x0, y0, z0, 0) ?: return true
        getPoint(x0, y0, z1, 1) ?: return true
        getPoint(x0, y1, z0, 2) ?: return true
        getPoint(x0, y1, z1, 3) ?: return true
        getPoint(x1, y0, z0, 4) ?: return true
        getPoint(x1, y0, z1, 5) ?: return true
        getPoint(x1, y1, z0, 6) ?: return true
        getPoint(x1, y1, z1, 7) ?: return true

        return Clipping.isRoughlyVisible(vs)

    }

    fun isSolid(index: Int) = content.isSolid(index)

    fun isSolid(x: Int, y: Int, z: Int) = isSolid(getIndex(x, y, z))

    fun destroy() {
        meshes.forEach { meshInfo -> meshInfo?.destroy() }
    }

    companion object {

        val vs = Array(8) { Vector4f() }.toList()

        const val FIRST_SOLID = 0
        const val FIRST_TRANS = 128

        const val AIR = 254
        const val OTHER = 255

        val uAIR = AIR.toUByte()
        val uOTHER = OTHER.toUByte()

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

    }

}