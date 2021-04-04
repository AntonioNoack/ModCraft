package me.anno.blocks.world

import org.joml.Vector3i

object ChunksByDistance {

    val maxDistance = 5
    val maxDistanceX = maxDistance * 2 + 1
    val chunksByDistance = Array(maxDistanceX * maxDistanceX * maxDistanceX) {
        val x = (it % maxDistanceX) - maxDistance
        val y = ((it / maxDistanceX) % maxDistanceX) - maxDistance
        val z = ((it / (maxDistanceX * maxDistanceX)) % maxDistanceX) - maxDistance
        Vector3i(x, y, z)
    }.apply { sortBy { it.lengthSquared() } }

}