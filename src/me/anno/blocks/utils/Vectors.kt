package me.anno.blocks.utils

import me.anno.blocks.utils.struct.Vector3j
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector3ic
import kotlin.math.roundToInt

fun Vector3i.cross(v: Vector3ic): Vector3i {
    val rx = (this.y* v.z() -this.z * v.y())
    val ry = (this.z* v.x() -this.x * v.z())
    val rz = (this.x* v.y() -this.y * v.x())
    this.x = rx
    this.y = ry
    this.z = rz
    return this
}

fun Vector3i.normalize(): Vector3i {
    val length = length()
    return Vector3i((x/length).roundToInt(), (y/length).roundToInt(), (z/length).roundToInt())
}

operator fun Vector3i.plus(second: Vector3f): Vector3f {
    return Vector3f(this).add(second)
}


fun Vector3f.roundToInt() = Vector3i(x.roundToInt(), y.roundToInt(), z.roundToInt())
fun Vector3d.roundToInt() = Vector3i(x.roundToInt(), y.roundToInt(), z.roundToInt())
fun Vector3d.floorToInt() = Vector3i(kotlin.math.floor(x).toInt(), kotlin.math.floor(y).toInt(), kotlin.math.floor(z).toInt())

fun Vector3f.roundToJnt() = Vector3j(x.roundToInt(), y.roundToInt(), z.roundToInt())
fun Vector3d.roundToJnt() = Vector3j(x.roundToInt(), y.roundToInt(), z.roundToInt())
fun Vector3d.floorToJnt() = Vector3j(kotlin.math.floor(x).toInt(), kotlin.math.floor(y).toInt(), kotlin.math.floor(z).toInt())

fun Vector3i.add(v: Vector3j) = add(v.x, v.y, v.z)