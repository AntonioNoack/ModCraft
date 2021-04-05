package me.anno.blocks.chunk.mesh

import me.anno.blocks.block.BlockSide
import me.anno.blocks.chunk.lighting.BakeLight
import me.anno.blocks.utils.cross
import me.anno.blocks.utils.normalize
import me.anno.blocks.utils.struct.Vector3j
import me.anno.gpu.buffer.StaticBuffer
import me.anno.utils.Maths.fract
import org.apache.logging.log4j.LogManager
import org.joml.*

class BlockBuffer(

    val base: StaticBuffer,
    val xyzTransform: Matrix4x3fc,
    val uv0: Vector2fc,
    val uv1: Vector2fc,
    val fetchLight: (x: Int, y: Int, z: Int) -> Short

) {

    val lightCache = HashMap<Vector3j, Short>()
    fun getLight(x: Int, y: Int, z: Int): Short {
        return lightCache.getOrPut(Vector3j(x, y, z)) { fetchLight(x, y, z) }
    }

    val light = Vector4f()

    fun getLight(v: Vector3f): Vector4f {
        return getLight2(v.x + 0.5f, v.y + 0.5f, v.z + 0.5f)
    }

    fun getLight(x: Float, y: Float, z: Float): Vector4f {
        return getLight2(x + 0.5f, y + 0.5f, z + 0.5f)
    }

    val i = IntArray(8)

    fun mix(a: Float, b: Float, f: Float): Float {
        return (1 - f) * a + f * b
    }

    fun get(shr: Int, offset: Int): Float {
        return i[offset].shr(shr).and(15) / 15f
    }

    fun getX(shr: Int, xFract: Float, offset: Int): Float {
        return mix(
            get(shr, offset),
            get(shr, offset + 1),
            xFract
        )
    }

    fun getXY(shr: Int, xFract: Float, yFract: Float, offset: Int): Float {
        return mix(
            getX(shr, xFract, offset),
            getX(shr, xFract, offset + 2),
            yFract
        )
    }

    fun getXYZ(shr: Int, xFract: Float, yFract: Float, zFract: Float): Float {
        return mix(
            getXY(shr, xFract, yFract, 0),
            getXY(shr, xFract, yFract, 4),
            zFract
        )
    }

    fun getLight2(x: Float, y: Float, z: Float): Vector4f {
        // todo x,y,z are incorrect by 0.5
        val xFract = fract(x)
        val yFract = fract(y)
        val zFract = fract(z)
        val light = light
        val xi = x.toInt()
        val yi = y.toInt()
        val zi = z.toInt()
        if (xFract < 0.01f && yFract < 0.01f && zFract < 0.01f) {
            // only fetch one
            val value = getLight(xi, yi, zi).toInt()
            light.set(
                value.shr(12).and(15) / 15f,
                value.shr(8).and(15) / 15f,
                value.shr(4).and(15) / 15f,
                value.and(15) / 15f
            )
        } else {
            i[0] = getLight(xi, yi, zi).toInt()
            i[1] = getLight(xi + 1, yi, zi).toInt()
            i[2] = getLight(xi, yi + 1, zi).toInt()
            i[3] = getLight(xi + 1, yi + 1, zi).toInt()
            i[4] = getLight(xi, yi, zi + 1).toInt()
            i[5] = getLight(xi + 1, yi, zi + 1).toInt()
            i[6] = getLight(xi, yi + 1, zi + 1).toInt()
            i[7] = getLight(xi + 1, yi + 1, zi + 1).toInt()
            light.set(
                getXYZ(12, xFract, yFract, zFract),
                getXYZ(8, xFract, yFract, zFract),
                getXYZ(4, xFract, yFract, zFract),
                getXYZ(0, xFract, yFract, zFract)
            )
        }
        return light
    }

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
        if(BakeLight.EnableLighting){
            base.put(getLight(transformed))
        } else {
            base.put(0f, 0f, 0f, 1f)
        }
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
        if(BakeLight.EnableLighting){
            base.put(getLight(transformed))
        } else {
            base.put(0f, 0f, 0f, 1f)
        }
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