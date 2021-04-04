package me.anno.blocks.physics

import me.anno.blocks.entity.Entity
import org.joml.Vector3d

class EntityHit(distance: Double, position: Vector3d, val entity: Entity?) : RaycastHit(distance, position)
