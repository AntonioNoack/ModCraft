package me.anno.blocks.block.base

import me.anno.blocks.block.Block
import me.anno.blocks.block.physical.SolidPhysical
import me.anno.blocks.block.visual.Texture
import me.anno.blocks.block.visual.TextureCoordinates
import me.anno.blocks.block.visual.TexturedBlock

val ErrorBlock = Block(NothingLogic, TexturedBlock(TextureCoordinates(15,15,Atlas)), SolidPhysical, NothingAcoustic, "base.error")
