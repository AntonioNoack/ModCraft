package me.anno.blocks.entity.player

import me.anno.blocks.entity.Entity
import me.anno.blocks.world.Dimension
import org.joml.Vector3d
import org.joml.Vector3f

class Player(dimension: Dimension, position: Vector3d) :
    Entity(dimension, position, Vector3f(0.3f, 1.8f, 0.3f), Vector3f(), "base.player") {
    val lookDirection = Vector3f()
    val mouseDirection = Vector3f()
}