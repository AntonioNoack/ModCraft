package me.anno.blocks.block.logical

import me.anno.blocks.block.BlockInfo
import me.anno.blocks.block.BlockState

class DecayLogic(val decayProcess: (BlockInfo) -> BlockState): BlockLogic() {

    override fun onRandomTick(info: BlockInfo) {
        info.set(decayProcess(info))
    }

}