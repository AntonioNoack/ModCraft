package me.anno.blocks.world.generator

import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Dirt
import me.anno.blocks.block.base.Grass
import me.anno.blocks.chunk.Chunk
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CSm1

class FlatGenerator(val content: Array<BlockState>, val voidBlock: BlockState?) : Generator(1, voidBlock != null) {

    constructor() : this(arrayOf(Dirt, Dirt, Dirt, Grass), null)

    override fun generate(chunk: Chunk, stage: Int) {
        when (stage) {
            0 -> {
                val y0 = chunk.coordinates.y * CS
                val y1 = StrictMath.min(y0 + CS, content.size)
                if (y0 >= 0) {
                    for(y in y0 until y1){
                        chunk.fillY(content[y], y and CSm1)
                    }
                } else if (voidBlock != null) {
                    chunk.fill(voidBlock)
                }
            }
        }
    }

}