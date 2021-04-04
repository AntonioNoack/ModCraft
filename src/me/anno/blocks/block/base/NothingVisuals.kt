package me.anno.blocks.block.base

import me.anno.blocks.block.BlockSide
import me.anno.blocks.block.visual.BlockVisuals
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.block.visual.TextureCoordinates
import me.anno.blocks.rendering.BlockBuffer

object NothingVisuals: BlockVisuals(MaterialType.TRANSPARENT_MASS, false, null) {

    override fun createMesh(
        dx: Int,
        dy: Int,
        dz: Int,
        side: BlockSide,
        getBuffer: (TextureCoordinates) -> BlockBuffer
    ) {}

    override fun getVertexCount(dx: Int, dy: Int, dz: Int, side: BlockSide): Int = 0

}