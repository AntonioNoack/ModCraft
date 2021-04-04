package me.anno.blocks.chunk

import me.anno.blocks.block.BlockState
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CSm1
import me.anno.blocks.chunk.Chunk.Companion.CSx2Bits
import me.anno.blocks.chunk.Chunk.Companion.CSx2m1
import me.anno.blocks.chunk.Chunk.Companion.getIndex
import me.anno.blocks.chunk.Chunk.Companion.getSizeInfo

object MergeBlocks {

    fun mergeBlocks(
        blocks: IntArray,
        types: Array<BlockState>,
        supportsRepetitions: BooleanArray
    ) {
        // join z
        for (y in 0 until CS) {
            for (x in 0 until CS) {
                var z = -1
                while (++z < CSm1) {
                    val index0 = getIndex(x, y, z)
                    if (blocks[index0] != 0 && supportsRepetitions[index0]) {
                        val hereType = types[index0]
                        val hereBlock = blocks[index0]
                        val startZ = z
                        while (++z < CS) {
                            val indexI = getIndex(x, y, z)
                            if (types[indexI] != hereType || blocks[indexI] != hereBlock) break
                        }
                        if (z > startZ + 1) {
                            blocks[index0] = getSizeInfo(1, 1, z - startZ)
                            for (zi in startZ + 1 until z) {
                                blocks[getIndex(x, y, zi)] = 0
                            }
                        }
                    }
                }
            }
        }
        // join x
        for (y in 0 until CS) {
            for (z in 0 until CS) {
                var x = -1
                while (++x < CSm1) {
                    val index0 = getIndex(x, y, z)
                    if (blocks[index0] != 0 && supportsRepetitions[index0]) {
                        val hereType = types[index0]
                        val hereBlock = blocks[index0]
                        val startX = x
                        while (++x < CS) {
                            val indexI = getIndex(x, y, z)
                            if (types[indexI] != hereType || blocks[indexI] != hereBlock) break
                        }
                        if (x > startX + 1) {
                            val oldSZ = hereBlock and CSx2m1
                            blocks[index0] = getSizeInfo(x - startX, 1, oldSZ)
                            for (xi in startX + 1 until x) {
                                blocks[getIndex(xi, y, z)] = 0
                            }
                        }
                    }
                }
            }
        }
        // join y
        for (x in 0 until CS) {
            for (z in 0 until CS) {
                var y = -1
                while (++y < CSm1) {
                    val index0 = getIndex(x, y, z)
                    if (blocks[index0] != 0 && supportsRepetitions[index0]) {
                        val hereType = types[index0]
                        val hereBlock = blocks[index0]
                        val startY = y
                        while (++y < CS) {
                            val indexI = getIndex(x, y, z)
                            if (types[indexI] != hereType || blocks[indexI] != hereBlock) break
                        }
                        if (y > startY + 1) {
                            val oldSX = hereBlock.shr(CSx2Bits) and CSx2m1
                            val oldSZ = hereBlock and CSx2m1
                            blocks[index0] = getSizeInfo(oldSX,y - startY, oldSZ)
                            for (yi in startY + 1 until y) {
                                blocks[getIndex(x, yi, z)] = 0
                            }
                        }
                    }
                }
            }
        }
    }

}