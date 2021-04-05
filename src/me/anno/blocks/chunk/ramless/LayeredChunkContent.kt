package me.anno.blocks.chunk.ramless

import me.anno.blocks.block.Block
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CS2
import me.anno.blocks.chunk.Chunk.Companion.CS3
import me.anno.blocks.chunk.Chunk.Companion.CSm1
import me.anno.blocks.chunk.Chunk.Companion.getY
import me.anno.blocks.registry.BlockRegistry
import me.anno.blocks.utils.readBlockState
import me.anno.blocks.utils.writeBlockState
import java.io.DataInputStream
import java.io.DataOutputStream

class LayeredChunkContent(val layers: Array<BlockState>) : ChunkContent() {

    private val isSolid = BooleanArray(layers.size) { layers[it].isSolid }

    // todo update this value
    private var highestY = layers.lastIndexOf(Air)

    override fun getHighestNonAirY(index: Int): Int {
        return highestY
    }

    override fun optimize(): ChunkContent {
        val first = layers[0]
        for (y in 1 until CS) {
            if (layers[y] != first) return this
        }
        return SimplestChunkContent(first)
    }

    override fun getBlockState(index: Int): BlockState {
        return layers[getY(index)]
    }

    override fun getBlockNullable(index: Int): Block? {
        return layers[getY(index)].block
    }

    override fun isSolid(index: Int): Boolean {
        return isSolid[getY(index)]
    }

    override fun setBlockInternally(index: Int, newState: BlockState): ChunkContent {
        // expand to full size
        val content = FullChunkContent()
        val blocks = content.blocks
        for (y in 0 until CS) {
            blocks.fill(content.register(layers[y]), y * CS2, (y + 1) * CS2)
        }
        val height = content.highestY
        height.fill(highestY.toByte())
        content.setBlock(index, newState)
        return content
    }

    override fun getAllBlocks(): Array<BlockState> {
        val firstState = layers[0]
        val states = Array(CS3) { firstState }
        for (y in 1 until CS) {
            val state = layers[y]
            if (state != firstState) {
                states.fill(state, y * CS2, (y + 1) * CS2)
            }
        }
        return states
    }

    override fun getAllBlockTypes(): List<BlockState> {
        return layers.toList()
    }

    override fun fillY(newState: BlockState, y: Int): ChunkContent {
        layers[y and CSm1] = newState
        isSolid[y and CSm1] = newState.isSolid
        return this
    }

    override fun writeInternally(output: DataOutputStream) {
        for (y in 0 until CS) {
            output.writeBlockState(layers[y])
        }
    }

    override fun readInternally(input: DataInputStream, registry: BlockRegistry): ChunkContent {
        for (y in 0 until CS) {
            val newState = input.readBlockState(registry)
            layers[y] = newState
            isSolid[y] = newState.isSolid
        }
        return this
    }

    override fun isCompletelySolid(): Boolean {
        for (y in 0 until CS) {
            if (!isSolid[y]) return false
        }
        return true
    }

    override fun isClosedSolid(): Boolean = isClosedSolid()

    override fun containsLights(): Boolean {
        for (y in 0 until CS) {
            if (layers[y].isLight) return true
        }
        return false
    }


}