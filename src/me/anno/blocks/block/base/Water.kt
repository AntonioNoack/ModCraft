package me.anno.blocks.block.base

import me.anno.blocks.block.Block
import me.anno.blocks.block.physical.FluidPhysical
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.block.visual.TextureCoordinates
import me.anno.blocks.block.visual.TexturedBlock


val WaterBlock = Block(
    NothingLogic, TexturedBlock(TextureCoordinates(15, 13, Atlas), MaterialType.TRANSPARENT_MASS),
    FluidPhysical, NothingAcoustic, "base.water"
)
val Water = WaterBlock.nullState



