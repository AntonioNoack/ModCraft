package me.anno.blocks.world

import me.anno.blocks.multiplayer.Client
import me.anno.blocks.registry.BlockRegistry
import me.anno.blocks.world.generator.RemoteGenerator
import me.anno.blocks.world.generator.Generator

class World {

    val registry = BlockRegistry()

    val dimensions = HashMap<String, Dimension>()

    fun getDimension(id: String) = dimensions[id]

    fun getOrCreateDimension(id: String, hasBlocksBelowZero: Boolean, client: Client): Dimension {
        val dimension0 = dimensions[id]
        if(dimension0 != null) return dimension0
        val generator = RemoteGenerator(client, hasBlocksBelowZero)
        return createDimension(generator, id)
    }

    fun createDimension(generator: Generator, id: String): Dimension {
        return dimensions.getOrPut(id){
            Dimension(this, id, generator).apply {
                generator.dimension = this
            }
        }
    }

}