package me.anno.blocks.physics

import me.anno.blocks.block.BlockState
import me.anno.blocks.utils.struct.Vector3j
import org.joml.Vector3d
import org.joml.Vector3i

class BlockHit(
    distance: Double, position: Vector3d,
    val previousPosition: Vector3j, val previousBlock: BlockState,
    val blockPosition: Vector3j, val block: BlockState
) : RaycastHit(distance, position) {

    constructor(
        distance: Double, position: Vector3d,
        previousPosition: Vector3i, previousBlock: BlockState,
        blockPosition: Vector3i, block: BlockState
    ) : this(distance, position, Vector3j(previousPosition), previousBlock, Vector3j(blockPosition), block)

}
