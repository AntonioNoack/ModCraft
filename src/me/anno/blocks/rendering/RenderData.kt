package me.anno.blocks.rendering

import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector3d
import org.joml.Vector3f

class RenderData {

    val cameraPosition = Vector3d()
    val cameraRotation = Vector3f()

    val matrix = Matrix4fArrayList()
    val inverse = Matrix4f()

    var chunkTriangles = 0
    var chunkBuffers = 0

    var lastTexture: String? = null
    var renderLines = false

    fun clearStats() {
        chunkTriangles = 0
        chunkBuffers = 0
    }

    val delta = Vector3d()
    val centerDelta = Vector3d()

}