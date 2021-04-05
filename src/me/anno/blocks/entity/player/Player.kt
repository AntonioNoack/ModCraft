package me.anno.blocks.entity.player

import me.anno.blocks.entity.Entity
import me.anno.blocks.inventory.GridInventory
import me.anno.blocks.world.Dimension
import org.joml.Vector3d
import org.joml.Vector3f

class Player(dimension: Dimension, position: Vector3d) :
    Entity(dimension, position, Vector3f(0.3f, 1.8f, 0.3f), Vector3f(), "base.player") {

    val lookDirection = Vector3f()
    val mouseDirection = Vector3f()

    val inventory = GridInventory(9, 4)
    var selectedSlot = 1
    val hotBarStartIndex = (inventory.sizeY - 1) * inventory.sizeX

    fun selectSlot(slot: Int) {
        scrollSlot(slot - selectedSlot)
    }

    fun scrollSlot(di: Int) {
        // scroll by di slots
        var ss = (selectedSlot + di) % inventory.sizeX
        if (ss < 0) ss += inventory.sizeX
        selectedSlot = ss
    }

}