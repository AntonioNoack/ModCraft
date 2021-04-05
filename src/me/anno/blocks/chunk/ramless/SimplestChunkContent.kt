package me.anno.blocks.chunk.ramless

import me.anno.blocks.block.Block
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CS3
import me.anno.blocks.chunk.Chunk.Companion.CSm1
import me.anno.blocks.registry.BlockRegistry
import me.anno.blocks.utils.readBlockState
import me.anno.blocks.utils.writeBlockState
import java.io.DataInputStream
import java.io.DataOutputStream

class SimplestChunkContent(var state: BlockState) : ChunkContent() {

    private var isSolid = state.isSolid

    override fun getHighestNonAirY(index: Int): Int {
        return if (state == Air) -1 else CSm1
    }

    override fun optimize(): ChunkContent {
        return this
    }

    override fun fill(newState: BlockState): ChunkContent {
        state = newState
        isSolid = state.isSolid
        return this
    }

    override fun getBlockState(index: Int): BlockState {
        return state
    }

    override fun getBlockNullable(index: Int): Block? {
        return state.block
    }

    override fun isSolid(index: Int): Boolean {
        return isSolid
    }

    override fun setBlockInternally(index: Int, newState: BlockState): ChunkContent {
        // we were changed ->
        // we need to expand to a full block of content
        val content = FullChunkContent()
        val type1 = content.register(state)
        val type2 = content.register(newState)
        val data = content.blocks
        data.fill(type1)
        data[index] = type2
        val height = content.highestY
        height.fill(getHighestNonAirY(0).toByte())
        return content
    }

    override fun getAllBlocks(): Array<BlockState> {
        return Array(CS3) { state }
    }

    override fun getAllBlockTypes(): List<BlockState> {
        return listOf(state)
    }

    override fun writeInternally(output: DataOutputStream) {
        output.writeBlockState(state)
    }

    override fun readInternally(input: DataInputStream, registry: BlockRegistry): ChunkContent {
        return fill(input.readBlockState(registry))
    }

    override fun fillY(newState: BlockState, y: Int): ChunkContent {
        return if (newState != state) {
            LayeredChunkContent(Array(CS) {
                if (it == y) newState
                else state
            })
        } else this
    }

    override fun isCompletelySolid(): Boolean = isSolid

    override fun isClosedSolid(): Boolean = isSolid

    override fun containsLights(): Boolean = state.isLight

}