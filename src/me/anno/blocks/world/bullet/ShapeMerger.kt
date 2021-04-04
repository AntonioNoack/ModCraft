package me.anno.blocks.world.bullet

import me.anno.blocks.chunk.Chunk.Companion.dx
import me.anno.blocks.chunk.Chunk.Companion.dy
import me.anno.blocks.chunk.Chunk.Companion.dz
import me.anno.blocks.chunk.Chunk.Companion.getIndex
import org.joml.Vector3i

object ShapeMerger {

    fun getShape(x: Int, y: Int, z: Int): Int {
        if (x in 1..32 && y in 1..32 && z in 0..32)
            return (x.shl(6) + y).shl(6) + z
        else throw RuntimeException("Illegal shape")
    }

    fun shapeToVec(i: Int) = Vector3i(i.shr(12), i.shr(6).and(63), i.and(63))

    fun removeInside(shapes: IntArray) {
        val removed = getShape(32, 32, 32)
        for (x in 1 until 31) {
            for (y in 1 until 31) {
                for (z in 1 until 31) {
                    val index = getIndex(x, y, z)
                    if (shapes[index] != 0 &&
                        shapes[index + dx] != 0 && shapes[index + dx] != 0 &&
                        shapes[index + dy] != 0 && shapes[index + dy] != 0 &&
                        shapes[index + dz] != 0 && shapes[index + dz] != 0
                    ) {
                        shapes[index] = removed
                    }
                }
            }
        }
        for (i in shapes.indices) {
            if (shapes[i] == removed) shapes[i] = 0
        }
    }


    fun mergeZ(shapes: IntArray) {
        for (x in 0 until 32) {
            for (y in 0 until 32) {
                var z0 = -1
                while (++z0 < 31) {
                    val i0 = getIndex(x, y, z0)
                    val shape0 = shapes[i0]
                    if (shape0 == 0) continue
                    var z1 = z0 + 1
                    var i1 = i0 + dz
                    while (z1 < 32) {
                        if (shapes[i1] != shape0) break
                        else shapes[i1] = 0
                        z1++
                        i1 += dz
                    }
                    if (z1 > z0 + 1) {
                        // the side changed -> apply it
                        val vec = shapeToVec(shape0)
                        shapes[i0] = getShape(vec.x, vec.y, z1 - z0)
                        z0 = z1 - 1
                    }
                }
            }
        }
    }

    fun mergeX(shapes: IntArray) {
        for (x in 0 until 32) {
            for (y in 0 until 32) {
                var z0 = -1
                while (++z0 < 31) {
                    val i0 = getIndex(z0, x, y)
                    val shape0 = shapes[i0]
                    if (shape0 == 0) continue
                    var z1 = z0 + 1
                    var i1 = i0 + dx
                    while (z1 < 32) {
                        if (shapes[i1] != shape0) break
                        else shapes[i1] = 0
                        z1++
                        i1 += dx
                    }
                    if (z1 > z0 + 1) {
                        // the side changed -> apply it
                        val vec = shapeToVec(shape0)
                        shapes[i0] = getShape(z1 - z0, vec.y, vec.z)
                        z0 = z1 - 1
                    }
                }
            }
        }
    }

    fun mergeY(shapes: IntArray) {
        for (x in 0 until 32) {
            for (y in 0 until 32) {
                var z0 = -1
                while (++z0 < 31) {
                    val i0 = getIndex(x, z0, y)
                    val shape0 = shapes[i0]
                    if (shape0 == 0) continue
                    var z1 = z0 + 1
                    var i1 = i0 + dy
                    while (z1 < 32) {
                        if (shapes[i1] != shape0) break
                        else shapes[i1] = 0
                        z1++
                        i1 += dy
                    }
                    if (z1 > z0 + 1) {
                        // the side changed -> apply it
                        val vec = shapeToVec(shape0)
                        shapes[i0] = getShape(vec.x, z1 - z0, vec.z)
                        z0 = z1 - 1
                    }
                }
            }
        }
    }


    fun merge(
        shapes: IntArray,
        delta: Int,
        getIndex: (x: Int, y: Int, i: Int) -> Int,
        extrudeShape: (oldShape: Int, size: Int) -> Int
    ) {
        for (x in 0 until 32) {
            for (y in 0 until 32) {
                var z0 = -1
                while (++z0 < 31) {
                    val i0 = getIndex(x, y, z0)
                    val shape0 = shapes[i0]
                    if (shape0 == 0) continue
                    var z1 = z0 + 1
                    var i1 = i0 + delta
                    while (z1 < 32) {
                        if (shapes[i1] != shape0) break
                        else shapes[i1] = 0
                        z1++
                        i1 += delta
                    }
                    if (z1 > z0 + 1) {
                        // the side changed -> apply it
                        shapes[i0] = extrudeShape(shape0, z1 - z0)
                        z0 = z1 - 1
                    }
                }
            }
        }
    }

}