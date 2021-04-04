package me.anno.blocks.block.base

import me.anno.blocks.block.Block
import me.anno.blocks.block.physical.SolidPhysical
import me.anno.blocks.block.visual.TextureCoordinates
import me.anno.blocks.block.visual.TexturedBlock

val grassTop = TextureCoordinates(0, 0, Atlas)
val grassSide = TextureCoordinates(3, 0, Atlas)
val dirtTex = TextureCoordinates(2, 0, Atlas)

val GrassBlock = Block(NothingLogic, TexturedBlock(grassSide, grassTop, dirtTex), SolidPhysical, NothingAcoustic, "base.grass")
val Grass = GrassBlock.nullState

val DirtBlock = Block(NothingLogic, TexturedBlock(dirtTex), SolidPhysical, NothingAcoustic, "base.dirt")
val Dirt = DirtBlock.nullState


