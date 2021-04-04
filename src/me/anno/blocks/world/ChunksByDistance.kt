package me.anno.blocks.world

import me.anno.blocks.chunk.Chunk
import org.joml.Vector3i

class ChunksByDistance(val maxDistance: Int) {

    val maxDistanceX = maxDistance * 2 + 1
    val size = maxDistanceX * maxDistanceX * maxDistanceX
    val values = Array(size) {
        val x = (it % maxDistanceX) - maxDistance
        val y = ((it / maxDistanceX) % maxDistanceX) - maxDistance
        val z = ((it / (maxDistanceX * maxDistanceX)) % maxDistanceX) - maxDistance
        Vector3i(x, y, z)
    }.apply { sortBy { it.lengthSquared() } }

    val chunks = arrayOfNulls<Chunk>(size)
    val chunksForRendering = arrayOfNulls<Chunk>(size)

    val lastCoordinates = Vector3i(0, 0, 0)
    fun update(chunk: Vector3i) {
        if (lastCoordinates != chunk) {
            chunks.fill(null)
        }
    }

    fun getChunk(index: Int) = chunks[index]
    fun setChunk(index: Int, chunk: Chunk) {
        chunks[index] = chunk
    }

    var renderingIndex = 0
    fun resetForRendering(){
        renderingIndex = 0
    }

    fun getChunkForRendering(index: Int) = chunksForRendering[index]!!

    fun pushForRendering(chunk: Chunk){
        chunksForRendering[renderingIndex++] = chunk
    }

}