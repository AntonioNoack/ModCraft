package me.anno.blocks.block.physical

import com.bulletphysics.collision.shapes.CollisionShape
import org.joml.Vector3f

data class TransformedCollisionShape(val shape: CollisionShape, val offset: Vector3f, val rotation: Vector3f) {
}