package me.anno.blocks.entity

import me.anno.blocks.world.Dimension
import org.joml.AABBf
import org.joml.Vector3d
import org.joml.Vector3f
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.atomic.AtomicInteger

open class Entity(
    var dimension: Dimension,
    val position: Vector3d,
    val size: Vector3f,
    val rotation: Vector3f,
    val type: String
) {

    val id = nextId.incrementAndGet()

    var headRotX = 0f

    var rotationY
        get() = rotation.y
        set(value) {
            rotation.y = value
        }

    val velocity = Vector3f()
    val ownForces = Vector3f()
    var hasGravity = true
    var shallFloat = 1f // -1f = shall sink, 0f = shall glide
    var mass = 50f

    val AABB = AABBf(Vector3f(size).mul(-0.5f), Vector3f(size).mul(0.5f))

    open fun calculateForces() {
        ownForces.zero()
    }

    open fun sendState(output: DataOutputStream){

    }

    open fun readState(input: DataInputStream){

    }

    companion object {
        val nextId = AtomicInteger(1)
    }

}