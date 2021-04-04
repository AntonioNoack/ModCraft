package me.anno.blocks.block.logical

import me.anno.blocks.block.BlockInfo

abstract class BlockLogic {

    open fun onRandomTick(info: BlockInfo) {

    }

    open fun onBeingMined(info: BlockInfo){
        info.chunk.drop(info)
    }

}