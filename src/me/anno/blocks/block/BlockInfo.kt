package me.anno.blocks.block

import me.anno.blocks.chunk.Chunk
import me.anno.blocks.utils.struct.Vector3j
import me.anno.blocks.world.Dimension
import me.anno.blocks.world.World

class BlockInfo(
    val world: World, val dimension: Dimension,
    val chunk: Chunk, val coordinates: Vector3j,
    val state: BlockState
) {
    fun set(newState: BlockState) {
        chunk.setBlock(coordinates, newState)
    }
}