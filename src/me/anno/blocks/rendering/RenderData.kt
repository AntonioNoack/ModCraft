package me.anno.blocks.rendering

import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector3d

class RenderData {

    val cameraPosition = Vector3d()
    val matrix = Matrix4fArrayList()
    val inverse = Matrix4f()

    var chunkTriangles = 0
    var chunkBuffers = 0

    fun clearStats() {
        chunkTriangles = 0
        chunkBuffers = 0
    }

}