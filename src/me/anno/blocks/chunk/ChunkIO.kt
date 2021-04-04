package me.anno.blocks.chunk

import me.anno.blocks.chunk.Chunk.Companion.CS3
import me.anno.blocks.chunk.Chunk.Companion.FIRST_SOLID
import me.anno.blocks.chunk.Chunk.Companion.FIRST_TRANS
import me.anno.blocks.chunk.Chunk.Companion.uAIR
import me.anno.blocks.chunk.Chunk.Companion.uOTHER
import me.anno.blocks.chunk.ramless.FullChunkContent
import me.anno.blocks.registry.BlockRegistry
import me.anno.blocks.utils.readBlockState
import me.anno.blocks.utils.writeBlockState
import me.anno.utils.LOGGER
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ProtocolException

object ChunkIO {

    fun write(chunkContent: FullChunkContent, output: DataOutputStream) {
        chunkContent.sendBasicGeometry(output)
        chunkContent.sendExtraBlocks(output)
        // todo write metadata
        // todo write entities...
    }

    fun read(chunkContent: FullChunkContent, input: DataInputStream, registry: BlockRegistry) {
        chunkContent.blocks.fill(uAIR)
        chunkContent.readBasicGeometry(input, registry)
        chunkContent.readExtraBlocks(input, registry)
        chunkContent.recalculateHeight()
    }

    fun FullChunkContent.sendBlockIndex(output: DataOutputStream) {
        output.writeByte(solidIndex)
        for (i in FIRST_SOLID until solidIndex) {
            output.writeBlockState(blockStates[i]!!)
        }
        output.writeByte(transIndex)
        for (i in FIRST_TRANS until transIndex) {
            output.writeBlockState(blockStates[i]!!)
        }
        // LOGGER.info("Sent $solidIndex/$transIndex/${extraBlocks.size}/${blocks.groupBy { it }.size}")
    }

    fun FullChunkContent.readBlockIndex(input: DataInputStream, registry: BlockRegistry) {
        val solidIndex = input.read()
        if (solidIndex < FIRST_SOLID) throw ProtocolException("Chunk.solidIndex was less than start")
        for (i in FIRST_SOLID until solidIndex) {
            val state = input.readBlockState(registry)
            // LOGGER.info("$coordinates/$i = $state")
            blockStates[i] = state
            blockStateIds[state] = i.toUByte()
        }
        this.solidIndex = solidIndex
        val transIndex = input.read()
        if (transIndex < FIRST_TRANS) throw ProtocolException("Chunk.transIndex was less than start")
        for (i in FIRST_TRANS until transIndex) {
            val state = input.readBlockState(registry)
            // LOGGER.info("$coordinates/$i = $state")
            blockStates[i] = state
            blockStateIds[state] = i.toUByte()
        }
        this.transIndex = transIndex
        // LOGGER.info("Read registry for chunk: $solidIndex/$transIndex")
    }

    fun FullChunkContent.sendBasicGeometry(output: DataOutputStream) {
        // write library of blocks
        sendBlockIndex(output)
        val blocks = blocks
        val firstIndex = blocks.indexOfFirst { it != Chunk.uAIR }
        output.writeInt(firstIndex)
        if (firstIndex >= 0) {
            val lastIndex = blocks.indexOfLast { it != Chunk.uAIR }
            output.writeInt(lastIndex)
            for (i in firstIndex..lastIndex) {
                output.writeByte(blocks[i].toInt())
            }
        }
    }

    var hasWarned = false

    fun FullChunkContent.readBasicGeometry(input: DataInputStream, registry: BlockRegistry) {
        readBlockIndex(input, registry)
        val firstIndex = input.readInt()
        val blocks = blocks
        if (firstIndex in 0 until CS3) {
            val lastIndex = input.readInt()
            val bytes = input.readNBytes(lastIndex - firstIndex + 1)
            for (index in firstIndex..lastIndex) {
                val code = bytes[index - firstIndex].toUByte()
                blocks[index] = code
                val block = getBlockNullable(index)
                if (block == null && code != uOTHER && !hasWarned) {
                    LOGGER.warn("Got null for $code, at $index in $firstIndex .. $lastIndex")
                    hasWarned = true
                    // break
                }
            }
        }
    }

    fun FullChunkContent.sendExtraBlocks(output: DataOutputStream) {
        val extraBlocks = extraBlocks
        if(extraBlocks != null){
            for ((index, block) in extraBlocks) {
                output.writeShort(index)
                output.writeBlockState(block)
            }
        }
        output.writeShort(CS3)
    }

    fun FullChunkContent.readExtraBlocks(input: DataInputStream, registry: BlockRegistry) {
        extraBlocks = null
        while (true) {
            val index = input.readUnsignedShort()
            if (index >= CS3) break
            val block = input.readBlockState(registry)
            if(extraBlocks == null) extraBlocks = HashMap()
            extraBlocks!![index] = block
        }
    }

}