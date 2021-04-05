package me.anno.blocks.block

import me.anno.blocks.block.acoustic.BlockAcoustic
import me.anno.blocks.block.logical.BlockLogic
import me.anno.blocks.block.physical.BlockPhysical
import me.anno.blocks.block.visual.BlockVisuals
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.rendering.RenderData
import me.anno.blocks.rendering.RenderPass
import me.anno.blocks.rendering.SolidShader

class Block(
    val logic: BlockLogic,
    val visuals: BlockVisuals,
    val physical: BlockPhysical,
    val acoustic: BlockAcoustic,
    val id: String, val fallback: String = id
) {

    val nullState = BlockState(this, null)

    init {
        visuals.checkTextures()
        if(id.isBlank()) throw RuntimeException("Id must not be empty")
    }

    val isSolid = visuals.materialType == MaterialType.SOLID_BLOCK

    val isLightSolid = logic.isLightSolid
    val isLight = logic.isLight
    val lightState = logic.lightState

    fun draw(data: RenderData, shader: SolidShader){
        visuals.draw(data, shader)
    }

}