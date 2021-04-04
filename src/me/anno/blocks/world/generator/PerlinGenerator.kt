package me.anno.blocks.world.generator

import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Dirt
import me.anno.blocks.block.base.Grass
import me.anno.blocks.block.base.Stone
import me.anno.blocks.block.base.Water
import me.anno.blocks.chunk.Chunk
import me.anno.blocks.chunk.Chunk.Companion.CS
import org.kdotjpg.OpenSimplexNoise

class PerlinGenerator(val topLayers: Array<BlockState>, val bottomLayers: BlockState, val voidBlock: BlockState?) :
    Generator(1, voidBlock != null) {

    constructor() : this(arrayOf(Dirt, Dirt, Dirt, Grass), Stone, null)

    val noise = OpenSimplexNoise(1234L)

    override fun generate(chunk: Chunk, stage: Int) {
        when (stage) {
            0 -> {
                val yMin = chunk.coordinates.y * CS
                val yMax = yMin + CS
                if (yMin >= 0) {
                    for (x in 0 until CS) {
                        for (z in 0 until CS) {
                            val noise = noise.eval((x + chunk.center.x()) * 0.01, (z + chunk.center.z()) * 0.01)
                            val height = (64f * noise + 64f).toInt()
                            val y2 = height - topLayers.size
                            for (y in yMin until StrictMath.min(yMax, y2)) {
                                chunk.setBlock(x, y, z, bottomLayers)
                            }
                            for (y in StrictMath.max(yMin, y2) until StrictMath.min(yMax, height)) {
                                chunk.setBlock(x, y, z, topLayers[y - y2])
                            }
                            // water test
                            for (y in StrictMath.max(yMin, height) until StrictMath.min(yMax, 64)) {
                                chunk.setBlock(x, y, z, Water)
                            }
                        }
                    }
                } else if (voidBlock != null) {
                    chunk.fill(voidBlock)
                }
            }
        }
    }

}