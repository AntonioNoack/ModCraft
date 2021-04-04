package me.anno.blocks.rendering

import me.anno.blocks.block.BlockSide
import me.anno.blocks.utils.cross
import me.anno.blocks.utils.normalize
import me.anno.gpu.buffer.StaticBuffer
import org.apache.logging.log4j.LogManager
import org.joml.*

class BlockBuffer(

    val base: StaticBuffer,
    val xyzTransform: Matrix4x3fc,
    val uv0: Vector2fc,
    val uv1: Vector2fc,
    val getLight: (Vector3f) -> Vector4f

) {

    fun addTriangle(
        a: Vector3fc, b: Vector3fc, c: Vector3fc,
        uvA: Vector2fc, uvB: Vector2fc, uvC: Vector2fc
    ) {
        val db = Vector3f(b).sub(a)
        val dc = Vector3f(c).sub(a)
        val normal = db.cross(dc).normalize()
        addTriangle(a, b, c, normal, normal, normal, uvA, uvB, uvC)
    }

    fun addTriangle(
        a: Vector3ic, b: Vector3ic, c: Vector3ic,
        uvA: Vector2ic, uvB: Vector2ic, uvC: Vector2ic,
        mirror: Boolean
    ) {
        val db = Vector3i(b).sub(a)
        val dc = Vector3i(c).sub(a)
        val normal = db.cross(dc).normalize()
        if (normal.x == 0 && normal.y == 0 && normal.z == 0) LOGGER.warn("Degenerate triangle $normal, $a $b $c")
        addTriangle(a, b, c, normal, normal, normal, uvA, uvB, uvC, mirror)
    }

    fun addTriangle(
        a: Vector3fc, b: Vector3fc, c: Vector3fc,
        norA: Vector3fc, norB: Vector3fc, norC: Vector3fc,
        uvA: Vector2fc, uvB: Vector2fc, uvC: Vector2fc
    ) {
        addPoint(a, norA, uvA)
        addPoint(b, norB, uvB)
        addPoint(c, norC, uvC)
    }

    fun addTriangle(
        a: Vector3ic, b: Vector3ic, c: Vector3ic,
        norA: Vector3ic, norB: Vector3ic, norC: Vector3ic,
        uvA: Vector2ic, uvB: Vector2ic, uvC: Vector2ic,
        mirror: Boolean
    ) {
        if (mirror) {
            addPoint(a, norA, uvA)
            addPoint(c, norC, uvC)
            addPoint(b, norB, uvB)
        } else {
            addPoint(a, norA, uvA)
            addPoint(b, norB, uvB)
            addPoint(c, norC, uvC)
        }
    }

    private fun addPoint(
        xyz: Vector3fc,
        normal: Vector3fc,
        uv: Vector2fc
    ) {
        val transformed = xyzTransform.transformPosition(Vector3f(xyz))
        base.put(transformed)
        base.put(xyzTransform.transformDirection(Vector3f(normal)))
        base.put(uv)
        base.put(uv0)
        base.put(uv1)
        base.put(getLight(transformed))
    }

    private fun addPoint(
        xyz: Vector3ic,
        normal: Vector3ic,
        uv: Vector2ic
    ) {
        val transformed = xyzTransform.transformPosition(Vector3f(xyz))
        base.put(transformed)
        base.put(xyzTransform.transformDirection(Vector3f(normal)))
        base.put(uv.x().toFloat(), uv.y().toFloat())
        base.put(uv0)
        base.put(uv1)
        base.put(getLight(transformed))
    }

    fun addQuad(
        a: Vector3fc,
        b: Vector3fc,
        c: Vector3fc,
        d: Vector3fc,
        uvA: Vector2fc,
        uvC: Vector2fc,
        abcd: Boolean
    ) {
        val uv1 = Vector2f(uvA.x(), uvC.y())
        val uv2 = Vector2f(uvC.x(), uvA.y())
        val uvB = if (abcd) uv1 else uv2
        val uvD = if (abcd) uv2 else uv1
        addTriangle(a, b, c, uvA, uvB, uvC)
        addTriangle(a, c, d, uvA, uvC, uvD)
    }

    fun addQuad(
        a: Vector3ic,
        b: Vector3ic,
        c: Vector3ic,
        d: Vector3ic,
        uvA: Vector2ic,
        uvC: Vector2ic,
        mirror: Boolean
    ) {
        if (mirror) {
            addQuad(b, a, d, c, uvA, uvC, false)
        } else {
            val uv1 = Vector2i(uvA.x(), uvC.y())
            val uv2 = Vector2i(uvC.x(), uvA.y())
            addTriangle(a, b, c, uvA, uv1, uvC, false)
            addTriangle(a, c, d, uvA, uvC, uv2, false)
        }
    }

    fun addQuad(side: BlockSide, dx: Int, dy: Int, dz: Int) {
        when (side) {
            BlockSide.NX -> {
                addQuad(
                    Vector3i(0, 0, 0),
                    Vector3i(0, dy, 0),
                    Vector3i(0, dy, dz),
                    Vector3i(0, 0, dz),
                    Vector2i(0, dy),
                    Vector2i(dz, 0),
                    false
                )
            }
            BlockSide.PX -> {
                addQuad(
                    Vector3i(dx, 0, 0),
                    Vector3i(dx, dy, 0),
                    Vector3i(dx, dy, dz),
                    Vector3i(dx, 0, dz),
                    Vector2i(0, 0),
                    Vector2i(dz, dy),
                    true
                )
            }
            BlockSide.NY -> {
                addQuad(
                    Vector3i(0, 0, 0),
                    Vector3i(0, 0, dz),
                    Vector3i(dx, 0, dz),
                    Vector3i(dx, 0, 0),
                    Vector2i(0, 0),
                    Vector2i(dx, dz),
                    false
                )
            }
            BlockSide.PY -> {
                addQuad(
                    Vector3i(0, dy, 0),
                    Vector3i(0, dy, dz),
                    Vector3i(dx, dy, dz),
                    Vector3i(dx, dy, 0),
                    Vector2i(0, 0),
                    Vector2i(dx, dz),
                    true
                )
            }
            BlockSide.NZ -> {
                addQuad(
                    Vector3i(0, 0, 0),
                    Vector3i(0, dy, 0),
                    Vector3i(dx, dy, 0),
                    Vector3i(dx, 0, 0),
                    Vector2i(0, 0),
                    Vector2i(dx, dy),
                    true
                )
            }
            BlockSide.PZ -> {
                addQuad(
                    Vector3i(0, 0, dz),
                    Vector3i(0, dy, dz),
                    Vector3i(dx, dy, dz),
                    Vector3i(dx, 0, dz),
                    Vector2i(0, dy),
                    Vector2i(dx, 0),
                    false
                )
            }
        }
    }

    companion object {
        val VERTEX_COUNT_TRI = 3
        val VERTEX_COUNT_QUAD = 2 * VERTEX_COUNT_TRI
        private val LOGGER = LogManager.getLogger(BlockBuffer::class)
    }


}