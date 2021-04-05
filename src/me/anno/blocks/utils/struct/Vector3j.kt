package me.anno.blocks.utils.struct

import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector3i

class Vector3j(val x: Int, val y: Int, val z: Int) {

    constructor() : this(0, 0, 0)
    constructor(i: Int) : this(i, i, i)
    constructor(v: Vector3i) : this(v.x, v.y, v.z)
    constructor(v: Vector3i, dx: Int, dy: Int, dz: Int) : this(v.x + dx, v.y + dy, v.z + dz)
    constructor(v: Vector3j, dx: Int, dy: Int, dz: Int) : this(v.x + dx, v.y + dy, v.z + dz)

    override fun toString(): String {
        return "$x $y $z"
    }

    override fun equals(other: Any?): Boolean {
        return other is Vector3j && other.x == x && other.y == y && other.z == z
    }

    override fun hashCode(): Int {
        return (x.hashCode() + y.hashCode() * 31) * 31 + z.hashCode()
    }

    operator fun plus(v: Vector3j) = Vector3j(x + v.x, y + v.y, z + v.z)

    fun set(dst: Vector3d): Vector3d {
        dst.x = x.toDouble()
        dst.y = y.toDouble()
        dst.z = z.toDouble()
        return dst
    }

    fun mutable() = Vector3i(x, y, z)
    fun mutableFloat() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
    fun mutableDouble() = Vector3d(x.toDouble(), y.toDouble(), z.toDouble())
    fun getBlockCenter() = Vector3d(x + 0.5, y + 0.5, z + 0.5)

}