package me.anno.blocks.world.generator

import me.anno.blocks.chunk.Chunk
import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.packets.chunk.ChunkRequestPacket
import org.apache.logging.log4j.LogManager
import org.joml.Vector3i
import kotlin.concurrent.thread

class RemoteGenerator(
    val client: Client,
    hasBlocksBelowZero: Boolean
) : Generator(1, hasBlocksBelowZero) {

    override fun generate(chunk: Chunk, stage: Int) {

        if (stage != 0) return

        // println("generating stage $stage of ${chunk.coordinates}, ${chunk.isFinished}, ${Thread.currentThread().name}")

        dimension.requestSlot()
        client.send(ChunkRequestPacket(chunk.coordinates, dimension.id))

        val maxSteps = 10000 // = 10s
        for(i in 0 until maxSteps){
            if(chunk.isFinished) break
            else Thread.sleep(1)
        }

        if(!chunk.isFinished){
            LOGGER.warn("Did not finish chunk ${chunk.coordinates}, ${Thread.currentThread().name}")
            chunk.finish()
        }

    }

    companion object {
        private val LOGGER = LogManager.getLogger(RemoteGenerator::class)
    }

}