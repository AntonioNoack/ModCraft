package me.anno.blocks.block.logical

import me.anno.blocks.block.BlockInfo
import me.anno.blocks.block.BlockState
import me.anno.blocks.utils.struct.Vector3j

class DecayLogic(
    lightLevel: Vector3j,
    lightSolid: Boolean,
    val decayProcess: (BlockInfo) -> BlockState): BlockLogic(lightLevel, lightSolid) {

    override fun onRandomTick(info: BlockInfo) {
        info.set(decayProcess(info))
    }

}