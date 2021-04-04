package me.anno.blocks.block.physical

import com.bulletphysics.collision.shapes.BoxShape
import com.bulletphysics.linearmath.Transform
import org.joml.AABBf
import org.joml.Vector3f

open class BlockPhysical(
    val collisionShapes: List<TransformedCollisionShape>?,
    val type: PhysicsType,
    val friction: Float,
    val fluidDensity: Float = 1f
) {

    fun getShapes(delta: Vector3f) = collisionShapes?.map {
        val shape = it.shape
        val min = javax.vecmath.Vector3f()
        val max = javax.vecmath.Vector3f()
        val transform = Transform()
        transform.origin.set(delta.x + it.offset.x, delta.y + it.offset.y, delta.z + it.offset.z)
        shape.getAabb(transform, min, max)
        AABBf(min.x, min.y, min.z, max.x, max.y, max.z)
    }

    companion object {

        val boxShape = BoxShape(javax.vecmath.Vector3f(0.5f, 0.5f, 0.5f))
        val boxTriple = TransformedCollisionShape(boxShape, Vector3f(), Vector3f())
        val boxShapeList = listOf(boxTriple)

    }

}