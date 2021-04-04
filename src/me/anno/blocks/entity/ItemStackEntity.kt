package me.anno.blocks.entity

import me.anno.blocks.item.ItemStack
import me.anno.blocks.world.Dimension
import org.joml.Vector3d
import org.joml.Vector3f

class ItemStackEntity(val itemStack: ItemStack, dimension: Dimension, position: Vector3d) :
    Entity(dimension, position, Vector3f(0.5f), Vector3f(), "base.itemStack") {
}