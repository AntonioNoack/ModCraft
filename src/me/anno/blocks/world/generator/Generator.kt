package me.anno.blocks.world.generator

import me.anno.blocks.chunk.Chunk
import me.anno.blocks.utils.struct.Vector3j
import me.anno.blocks.world.Dimension
import me.anno.gpu.GFX.checkIsNotGFXThread
import me.anno.utils.LOGGER
import org.apache.logging.log4j.LogManager

abstract class Generator(val stages: Int, val hasBlocksBelowZero: Boolean) {

    lateinit var dimension: Dimension

    fun getFinishedChunk(coordinates: Vector3j): Chunk? {
        if(this !is RemoteGenerator) LOGGER.info("Requested $coordinates")
        val chunk = getChunk(coordinates, stages)
        if(this !is RemoteGenerator) LOGGER.info("Finished $coordinates")
        return chunk
    }

    private val lockedChunks = HashSet<Chunk>()
    private fun lock(chunk: Chunk, run: () -> Unit) {
        while (true) {
            var hasLock = false
            synchronized(lockedChunks) {
                if (lockedChunks.add(chunk)) {
                    hasLock = true
                }
            }
            if (hasLock) break
            else Thread.sleep(1)
        }
        run()
        synchronized(lockedChunks) {
            lockedChunks.remove(chunk)
        }
    }

    private fun getChunk(coordinates: Vector3j, requestedStage: Int): Chunk? {
        if (coordinates.y >= 0 || hasBlocksBelowZero) {
            if (requestedStage !in 0..stages) throw IllegalArgumentException()
            val chunk = synchronized(dimension.chunks) {
                dimension.chunks.getOrPut(coordinates) {
                    Chunk(dimension, coordinates)
                }
            }
            while (chunk.stage < requestedStage) {
                checkIsNotGFXThread()
                lock(chunk) {
                    val chunkStage = chunk.stage
                    if (chunkStage < requestedStage) {
                        generate(chunk, chunkStage)
                        chunk.stage = chunkStage + 1
                        if (chunk.stage >= stages) chunk.finish()
                    }
                }
            }
            return chunk
        } else return null
    }

    abstract fun generate(chunk: Chunk, stage: Int)

    // todo calculate lights

    companion object {
        private val LOGGER = LogManager.getLogger(Generator::class)
    }

}