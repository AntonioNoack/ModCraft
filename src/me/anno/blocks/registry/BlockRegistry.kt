package me.anno.blocks.registry

import me.anno.blocks.block.Block
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.*

class BlockRegistry {

    val blocks = HashMap<String, Block>()

    fun getBlock(id: String) = blocks[id] ?: throw BlockNotFoundException(id)

    fun register(block: Block){
        blocks[block.id] = block
    }

    fun register(state: BlockState){
        register(state.block)
    }

    init {
        // default blocks, which always exist
        // should we really only include the basics here, and use a plugin for complex stuff?
        // interactions may become hard... except we have dependencies ;)
        // dependency circles may become an issue
        register(AirBlock)
        register(DirtBlock)
        register(GrassBlock)
        register(StoneBlock)
        register(WaterBlock)
        register(LeavesBlock)
        register(ErrorBlock)
    }

}