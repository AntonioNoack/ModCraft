package me.anno.blocks.chunk.ramless

import me.anno.blocks.block.Block
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.base.AirBlock
import me.anno.blocks.block.base.ErrorBlock
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.chunk.Chunk
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CS2
import me.anno.blocks.chunk.Chunk.Companion.CS2m1
import me.anno.blocks.chunk.Chunk.Companion.CS3
import me.anno.blocks.chunk.Chunk.Companion.CSm1
import me.anno.blocks.chunk.Chunk.Companion.FIRST_SOLID
import me.anno.blocks.chunk.Chunk.Companion.FIRST_TRANS
import me.anno.blocks.chunk.Chunk.Companion.getIndex
import me.anno.blocks.chunk.Chunk.Companion.getY
import me.anno.blocks.chunk.Chunk.Companion.uAIR
import me.anno.blocks.chunk.Chunk.Companion.uFIRST_SOLID
import me.anno.blocks.chunk.Chunk.Companion.uFIRST_TRANS
import me.anno.blocks.chunk.Chunk.Companion.uOTHER
import me.anno.blocks.chunk.ChunkIO
import me.anno.blocks.registry.BlockRegistry
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.max

class FullChunkContent : ChunkContent() {

    val blocks = UByteArray(CS3) { uAIR }
    val highestY = ByteArray(CS2) { -1 }

    val blockStateIds = HashMap<BlockState, UByte>()
    val blockStates = arrayOfNulls<BlockState>(254)

    var extraBlocks: HashMap<Int, BlockState>? = null

    var solidIndex = FIRST_SOLID
    var transIndex = FIRST_TRANS

    private fun clearRegistry() {
        solidIndex = FIRST_SOLID
        transIndex = FIRST_TRANS
        blockStateIds.clear()
        extraBlocks = null
    }

    override fun optimize(): ChunkContent {
        if (extraBlocks?.isNotEmpty() == true) {
            val counter = HashMap<BlockState, Int>()
            val blockStates = getAllBlocks()
            for (block in blockStates) {
                if (block !== Air) {
                    counter[block] = (counter[block] ?: 0) + 1
                }
            }
            var bestSolid = counter.filter { it.key.isSolid }.toList()
            var bestTrans = counter.filter { !it.key.isSolid }.toList()
            if (bestSolid.size > Chunk.SOLID_SIZE) bestSolid = bestSolid.sortedByDescending { it.second }
            if (bestTrans.size > Chunk.TRANS_SIZE) bestTrans = bestTrans.sortedByDescending { it.second }
            clearRegistry()
            bestSolid.forEach { register(it.first) }
            bestTrans.forEach { register(it.first) }
            blocks.fill(uAIR)
            for (index in blockStates.indices) {
                val block = blockStates[index]
                if (block !== Air) {
                    setBlock(index, block)
                }
            }
            // val effectiveSize = solidIndex + transIndex - (FIRST_SOLID + FIRST_TRANS)
            // if(effectiveSize < counter.size) throw RuntimeException("Registry is broken")
            // LOGGER.info("Optimized $solidIndex/$transIndex from ${counter.size} types")
        }
        return optimizeIntoLayers() ?: this
    }

    private fun optimizeIntoLayers(): ChunkContent? {
        // check if all layers are monotonous
        if (extraBlocks?.isNotEmpty() == true) return null
        var layers: Array<BlockState>? = null
        for (y in 0 until CS) {
            val index0 = y * CS2
            val index1 = (y + 1) * CS2
            val firstType = blocks[index0]
            for (index in index0 + 1 until index1) {
                if (blocks[index] != firstType) return null
            }
            val blockState = if (firstType == uAIR) Air else blockStates[firstType.toInt()]!!
            if (y == 0) layers = Array(CS) { blockState }
            else layers!![y] = blockState
        }
        layers!!
        return optimizeIntoSimplest(layers) ?: LayeredChunkContent(layers)
    }

    private fun optimizeIntoSimplest(layers: Array<BlockState>): SimplestChunkContent? {
        val first = layers[0]
        for (y in 1 until CS) {
            if (layers[y] != first) return null
        }
        return SimplestChunkContent(first)
    }

    fun register(state: BlockState): UByte {
        if (state == Air) return uAIR
        val id = blockStateIds[state]
        if (id != null) return id
        val isSolid = state.isSolid
        // if we have enough space...
        synchronized(this) {
            val index = if (isSolid) solidIndex else transIndex
            val lastIndex = if (isSolid) Chunk.FIRST_TRANS else Chunk.AIR
            return if (index < lastIndex) {
                // there is still space
                blockStates[index] = state
                blockStateIds[state] = index.toUByte()
                if (isSolid) solidIndex++
                else transIndex++
                index.toUByte()
            } else uOTHER
        }
    }

    override fun getBlockState(index: Int): BlockState {
        return when (val id = blocks[index].toInt()) {
            Chunk.AIR -> Air
            Chunk.OTHER -> extraBlocks?.get(index) ?: ErrorBlock.nullState
            else -> blockStates[id] ?: ErrorBlock.nullState
        }
    }

    /*fun fillY(newState: BlockState, y: Int) {
        if (y !in 0 until Chunk.CS) return fillY(newState, y and Chunk.CSm1)
        synchronized(this) {
            val index0 = Chunk.getIndex(0, y, 0)
            val index1 = index0 + Chunk.CS2
            val stateId = register(newState)
            blocks.fill(stateId, index0, index1)
            if (stateId == Chunk.uOTHER) {
                // we deserve a better spot
                // this function isn't very effective, but it should be ok,
                // as it never will be called
                for (i in index0 until index1) {
                    extraBlocks[i] = newState
                }
                optimizeBlocks()
            }
        }
    }*/

    override fun getBlockNullable(index: Int): Block? {
        val type = blocks[index]
        if (type == uAIR) return AirBlock
        if (type == uOTHER) return extraBlocks?.get(index)?.block
        return blockStates[type.toInt()]?.block
    }

    override fun isSolid(index: Int): Boolean {
        val type = blocks[index]
        if (type == uAIR) return false
        if (type.toInt() in Chunk.FIRST_SOLID until Chunk.FIRST_TRANS) return true
        return getMaterialType(index) == MaterialType.SOLID_BLOCK
    }

    override fun getHighestNonAirY(index: Int): Int {
        return highestY[index and CS2m1].toInt()
    }

    override fun setBlockInternally(index: Int, newState: BlockState): ChunkContent {

        val oldStateId = blocks[index]
        val newStateId = register(newState)

        // changed
        if (oldStateId != newStateId && oldStateId == uOTHER) {
            extraBlocks?.remove(index)
        }

        if (oldStateId != newStateId) {
            blocks[index] = newStateId
        }

        if (newStateId == uOTHER) {
            if (extraBlocks == null) extraBlocks = HashMap()
            extraBlocks!![index] = newState
        }

        recalculateHighestNonAirBlock(index, newStateId, oldStateId)

        return this

    }

    // todo this may be inaccurate for fields, which access the <blocks> property
    private fun recalculateHighestNonAirBlock(index: Int, newStateId: UByte, oldStateId: UByte): Byte {
        // recalculate highest non-air block
        val ixz = index and CS2m1
        if (oldStateId == uAIR) {
            // we got a new block -> no longer air
            highestY[ixz] = max(getY(index), ixz).toByte()
        } else if (newStateId == uAIR) {
            // we got more air -> need to update via search
            var done = false
            for (y in getY(index) downTo 0) {
                if (blocks[y] != uAIR) {
                    highestY[ixz] = y.toByte()
                    done = true
                    break
                }
            }
            if (!done) {// there are no solid blocks here
                highestY[ixz] = -1
            }
        }
        return highestY[ixz]
    }

    fun recalculateHeight() {
        highestY.fill(-1)
        for (index in 0 until CS3) {
            if (blocks[index] != uAIR) {
                highestY[index and CS2m1] = getY(index).toByte()
            }
        }
    }

    override fun getAllBlocks(): Array<BlockState> {
        return Array(CS3) { getBlockState(it) }
    }

    override fun getAllBlockTypes(): List<BlockState> {
        val list0 = blockStateIds.keys.toList()
        val list1 = extraBlocks?.values?.toList()
        return if (list1 == null) list0 else list0 + list1
    }

    override fun readInternally(input: DataInputStream, registry: BlockRegistry): ChunkContent {
        ChunkIO.read(this, input, registry)
        return this
    }

    override fun writeInternally(output: DataOutputStream) {
        ChunkIO.write(this, output)
        wasChanged = true
    }

    override fun isCompletelySolid(): Boolean {
        val start = uFIRST_SOLID
        val end = uFIRST_TRANS
        for (block in blocks) {
            if (block !in start until end) {
                return false
            }
        }
        return true
    }

    override fun isClosedSolid(): Boolean {
        val start = FIRST_SOLID.toUByte()
        val end = FIRST_TRANS.toUByte()
        val topIndex = getIndex(0, CSm1, 0)
        for (i in 0 until CS2) if (blocks[i] !in start until end) return false
        for (i in topIndex until CS2 + topIndex) if (blocks[i] !in start until end) return false
        // check sides
        for (i in 1 until CSm1) {
            for (j in 1 until CSm1) {
                if (blocks[getIndex(i, j, 0)] !in start until end) return false
                if (blocks[getIndex(i, j, CSm1)] !in start until end) return false
                if (blocks[getIndex(0, j, i)] !in start until end) return false
                if (blocks[getIndex(CSm1, j, i)] !in start until end) return false
            }
        }
        return true
    }

    override fun containsLights(): Boolean {
        for (i in FIRST_SOLID until solidIndex) if (blockStates[i]!!.isLight) return true
        for (i in FIRST_TRANS until transIndex) if (blockStates[i]!!.isLight) return true
        val extraBlocks = extraBlocks ?: return false
        return extraBlocks.values.any { it.isLight }
    }

}