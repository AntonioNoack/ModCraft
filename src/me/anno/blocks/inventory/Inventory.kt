package me.anno.blocks.inventory

import me.anno.blocks.item.ItemStack

abstract class Inventory {

    abstract fun get(index: Int): ItemStack

}