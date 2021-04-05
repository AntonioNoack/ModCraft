package me.anno.blocks.block.base

import me.anno.blocks.block.Block
import me.anno.blocks.block.logical.BlockLogic
import me.anno.blocks.block.physical.SolidPhysical
import me.anno.blocks.block.visual.TextureCoordinates
import me.anno.blocks.block.visual.TexturedBlock
import me.anno.blocks.utils.struct.Vector3j

val GlowstoneBlock = Block(
    BlockLogic(Vector3j(15, 15, 12), true),
    TexturedBlock(TextureCoordinates(2, 10, Atlas)),
    SolidPhysical,
    NothingAcoustic,
    "base.glowstone"
)