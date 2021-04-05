package me.anno.blocks.block.base

import me.anno.blocks.block.Block
import me.anno.blocks.block.physical.SolidPhysical
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.block.visual.TextureCoordinates
import me.anno.blocks.block.visual.TexturedBlock

val leavesTexture = TexturedBlock(TextureCoordinates(4, 3, Atlas), MaterialType.SOLID_COMPLEX)
val LeavesBlock = Block(
    NothingLogic,
    leavesTexture, SolidPhysical, NothingAcoustic, "base.leaves"
)
val Leaves = LeavesBlock.nullState