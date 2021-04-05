package me.anno.blocks.chunk

import me.anno.blocks.block.BlockSide
import me.anno.gpu.GFX
import me.anno.utils.Clipping
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector4f

class ChunkCulling(val chunk: Chunk) {

    companion object {
        private val updateDistance = 0.05
        private val ud2 = updateDistance * updateDistance
        private val updateRadians = 0.05f
        private val ur2 = updateRadians * updateRadians
    }

    private val x0 = chunk.coordinates.x * Chunk.CS
    private val x1 = x0 + Chunk.CS
    private val y0 = chunk.coordinates.y * Chunk.CS
    private val y1 = y0 + Chunk.CS
    private val z0 = chunk.coordinates.z * Chunk.CS
    private val z1 = z0 + Chunk.CS

    fun contains(x: Int, y: Int, z: Int) = x in x0 until x1 && y in y0 until y1 && z in z0 until z1

    private val lastPosition = Vector3d()
    private val lastRotation = Vector3f()
    private var lastWasVisible = true

    val sideIsActive = BooleanArray(6) { true }

    private val center = chunk.center
    private val centerDelta = Vector3d()

    fun isVisible(matrix: Matrix4f, position: Vector3d, rotation: Vector3f): Boolean {

        if (lastPosition.distanceSquared(position) < ud2 &&
            lastRotation.distance(rotation) < ur2
        ) {
            return lastWasVisible
        }

        val result = isVisible2(matrix, position)
        lastWasVisible = result
        lastPosition.set(position)
        lastRotation.set(rotation)

        if (result) {
            val centerDelta = centerDelta.set(center).sub(position)
            for (side in BlockSide.values2) {
                // check which sides are needed of this chunk to save memory and calculation time
                // efficiency ~ 2x, more like 1.7x less triangles
                val isVisible = side.normalD.dot(centerDelta) < Chunk.CS * 0.5f
                sideIsActive[side.id] = isVisible
            }
        }

        return result

    }

    fun isVisible2(matrix: Matrix4f, position: Vector3d): Boolean {

        GFX.checkIsGFXThread()

        fun getPoint(x: Int, y: Int, z: Int, i: Int): Vector4f? {
            val vec = Chunk.vs[i]
            vec.set(x - position.x, y - position.y, z - position.z, 1.0)
            matrix.transform(vec, vec)
            val w = vec.w
            return if (vec.x in -w..w && vec.y in -w..w && vec.z in -w..w) null
            else vec.div(w)
        }

        getPoint(x0, y0, z0, 0) ?: return true
        getPoint(x0, y0, z1, 1) ?: return true
        getPoint(x0, y1, z0, 2) ?: return true
        getPoint(x0, y1, z1, 3) ?: return true
        getPoint(x1, y0, z0, 4) ?: return true
        getPoint(x1, y0, z1, 5) ?: return true
        getPoint(x1, y1, z0, 6) ?: return true
        getPoint(x1, y1, z1, 7) ?: return true

        return Clipping.isRoughlyVisible(Chunk.vs)

    }

}