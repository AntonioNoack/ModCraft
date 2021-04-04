package me.anno.blocks.block.base

import me.anno.blocks.block.Block
import me.anno.blocks.block.physical.NothingPhysical

val AirBlock = Block(NothingLogic, NothingVisuals, NothingPhysical, NothingAcoustic, "base.air", "base.air")
val Air = AirBlock.nullState
