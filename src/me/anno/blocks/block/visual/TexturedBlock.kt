package me.anno.blocks.block.visual

import me.anno.blocks.block.BlockSide
import me.anno.blocks.rendering.BlockBuffer
import me.anno.blocks.rendering.BlockBuffer.Companion.VERTEX_COUNT_QUAD

class TexturedBlock(
    val nx: TextureCoordinates,
    val px: TextureCoordinates,
    val ny: TextureCoordinates,
    val py: TextureCoordinates,
    val nz: TextureCoordinates,
    val pz: TextureCoordinates,
    materialType: MaterialType = MaterialType.SOLID_BLOCK
) : BlockVisuals(materialType, true, arrayOf(
    nx.texture, px.texture,
    ny.texture, py.texture,
    nz.texture, pz.texture
)) {

    constructor(t: TextureCoordinates, materialType: MaterialType = MaterialType.SOLID_BLOCK) :
            this(t, t, t, t, t, t, materialType)

    constructor(
        side: TextureCoordinates,
        top: TextureCoordinates,
        bottom: TextureCoordinates,
        materialType: MaterialType = MaterialType.SOLID_BLOCK
    ) : this(side, side, bottom, top, side, side, materialType)

    override fun createMesh(
        dx: Int,
        dy: Int,
        dz: Int,
        side: BlockSide,
        getBuffer: (TextureCoordinates) -> BlockBuffer
    ) {
        val texture = when (side) {
            BlockSide.NX -> nx
            BlockSide.NY -> ny
            BlockSide.NZ -> nz
            BlockSide.PX -> px
            BlockSide.PY -> py
            BlockSide.PZ -> pz
        }
        getBuffer(texture).addQuad(side, dx, dy, dz)
    }

    override fun getVertexCount(dx: Int, dy: Int, dz: Int, side: BlockSide): Int {
        return VERTEX_COUNT_QUAD
    }

}