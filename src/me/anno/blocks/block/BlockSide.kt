package me.anno.blocks.block

import me.anno.blocks.chunk.Chunk.Companion.getIndex
import me.anno.blocks.utils.struct.Vector3j
import org.joml.*

enum class BlockSide(
    val id: Int,
    val normal: Vector3fc,
    val normalD: Vector3d = Vector3d(normal),
    val normalI: Vector3j = Vector3j(normal.x().toInt(), normal.y().toInt(), normal.z().toInt()),
    val offset: Int = normalI.x * getIndex(1, 0, 0) +
            normalI.y * getIndex(0, 1, 0) +
            normalI.z * getIndex(0, 0, 1)
) {
    NX(0, Vector3f(-1f, 0f, 0f)),
    PX(1, Vector3f(+1f, 0f, 0f)),
    NY(2, Vector3f(0f, -1f, 0f)),
    PY(3, Vector3f(0f, +1f, 0f)),
    NZ(4, Vector3f(0f, 0f, -1f)),
    PZ(5, Vector3f(0f, 0f, +1f));
    companion object {
        val values2 = values()
    }
}