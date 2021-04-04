package me.anno.blocks.world.generator

import me.anno.blocks.chunk.Chunk

class NothingGenerator : Generator(0, false) {

    override fun generate(chunk: Chunk, stage: Int) {}

}