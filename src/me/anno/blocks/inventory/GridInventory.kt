package me.anno.blocks.inventory

import me.anno.blocks.block.base.Stone
import me.anno.blocks.item.ItemStack

class GridInventory(val sizeX: Int, val sizeY: Int) {

    val slots = Array(sizeX * sizeY){ ItemStack(Stone) }

}