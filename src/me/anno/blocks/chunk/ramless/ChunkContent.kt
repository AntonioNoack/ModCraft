package me.anno.blocks.chunk.ramless

import me.anno.blocks.block.Block
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.base.ErrorBlock
import me.anno.blocks.chunk.Chunk
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CS2
import me.anno.blocks.registry.BlockRegistry
import java.io.DataInputStream
import java.io.DataOutputStream

// make chunks more memory efficient;
// especially empty ones, and those in flat worlds
abstract class ChunkContent {

    var wasChanged = false

    // abstract

    // -1 = only air, 31 = last block is solid
    abstract fun getHighestNonAirY(index: Int): Int

    abstract fun optimize(): ChunkContent

    abstract fun getBlockState(index: Int): BlockState

    abstract fun getBlockNullable(index: Int): Block?

    abstract fun isSolid(index: Int): Boolean

    abstract fun setBlockInternally(index: Int, newState: BlockState): ChunkContent

    abstract fun getAllBlocks(): Array<BlockState>

    abstract fun getAllBlockTypes(): List<BlockState>

    abstract fun writeInternally(output: DataOutputStream)

    abstract fun readInternally(input: DataInputStream, registry: BlockRegistry): ChunkContent

    // additional functions

    fun write(output: DataOutputStream) {
        val type = when (this) {
            is FullChunkContent -> 0
            is LayeredChunkContent -> 1
            is SimplestChunkContent -> 2
            else -> throw RuntimeException()
        }
        output.writeByte(type)
        writeInternally(output)
    }

    fun read(input: DataInputStream, registry: BlockRegistry): ChunkContent {
        return when (input.readUnsignedByte()) {
            0 -> FullChunkContent()
            1 -> LayeredChunkContent(Array(CS) { Air })
            2 -> SimplestChunkContent(Air)
            else -> throw RuntimeException()
        }.readInternally(input, registry)
    }

    open fun fill(newState: BlockState): ChunkContent {
        return SimplestChunkContent(newState)
    }

    fun getBlock(index: Int): Block = getBlockNullable(index) ?: ErrorBlock

    fun setBlock(index: Int, newState: BlockState): ChunkContent {
        return if (getBlockState(index) != newState) {
            wasChanged = true
            setBlockInternally(index, newState)
        } else this
    }

    fun getBlockVisuals(index: Int) = getBlock(index).visuals
    fun getMaterialType(index: Int) = getBlockVisuals(index).materialType

    open fun fillY(newState: BlockState, y: Int): ChunkContent {
        if (y !in 0 until CS) return fillY(newState, y and Chunk.CSm1)
        var content = this
        val index0 = Chunk.getIndex(0, y, 0)
        val index1 = index0 + CS2
        for (i in index0 until index1) {
            content = setBlock(i, newState)
        }
        wasChanged = true
        return content
    }

}