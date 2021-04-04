package me.anno.blocks.block.base

import me.anno.blocks.block.Block
import me.anno.blocks.block.physical.SolidPhysical
import me.anno.blocks.block.visual.TextureCoordinates
import me.anno.blocks.block.visual.TexturedBlock

val StoneBlock = Block(NothingLogic, TexturedBlock(TextureCoordinates(1, 0, Atlas)), SolidPhysical, NothingAcoustic, "base.stone")
val Stone = StoneBlock.nullState

