package me.anno.blocks.block.logical

import me.anno.blocks.block.BlockInfo
import me.anno.blocks.utils.struct.Vector3j
import me.anno.utils.Maths.clamp

open class BlockLogic(val lightLevel: Vector3j, val isLightSolid: Boolean) {

    // light is a property that has influence on monster spawns, so it needs to be
    // part of the logic
    val lightState = (clamp(lightLevel.x, 0, 15).shl(12) or
            clamp(lightLevel.y, 0, 15).shl(8) or
            clamp(lightLevel.z, 0, 15).shl(4)).toShort()
    val isLight = lightState > 0

    open fun onRandomTick(info: BlockInfo) {

    }

    open fun onBeingMined(info: BlockInfo) {
        info.chunk.drop(info)
    }

}