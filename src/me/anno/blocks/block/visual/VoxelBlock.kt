package me.anno.blocks.block.visual

import me.anno.blocks.block.BlockSide
import me.anno.blocks.chunk.mesh.BlockBuffer

class VoxelBlock(
    voxels: IntArray, // N³ colors
    materialType: MaterialType
): BlockVisuals(materialType, false, null) {

    override fun createMesh(
        dx: Int,
        dy: Int,
        dz: Int,
        side: BlockSide,
        getBuffer: (TextureCoordinates) -> BlockBuffer
    ) {
        TODO("Not yet implemented")
    }

    override fun getVertexCount(dx: Int, dy: Int, dz: Int, side: BlockSide): Int {
        TODO("Not yet implemented")
    }

}