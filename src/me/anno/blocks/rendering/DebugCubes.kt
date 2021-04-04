package me.anno.blocks.rendering

import me.anno.blocks.utils.struct.Vector3j
import me.anno.gpu.GFXx3D
import org.joml.Vector3d
import org.joml.Vector3i
import org.joml.Vector4f

object DebugCubes {

    fun drawDebugCube(data: RenderData, position: Vector3d?){
        position ?: return
        val matrix = data.matrix
        matrix.pushMatrix()
        val delta = Vector3d(position).sub(data.cameraPosition)
        matrix.translate(delta.x.toFloat(), delta.y.toFloat(), delta.z.toFloat())
        GFXx3D.drawDebugCube(matrix, 1f, Vector4f(1f))
        matrix.popMatrix()
    }

    fun drawDebugCube(data: RenderData, position: Vector3j?){
        position ?: return
        val matrix = data.matrix
        matrix.pushMatrix()
        val delta = position.getBlockCenter().sub(data.cameraPosition)
        matrix.translate(delta.x.toFloat(), delta.y.toFloat(), delta.z.toFloat())
        GFXx3D.drawDebugCube(matrix, 1f, Vector4f(1f))
        matrix.popMatrix()
    }

    fun drawDebugCube(data: RenderData, position: Vector3i?){
        position ?: return
        val matrix = data.matrix
        matrix.pushMatrix()
        val delta = Vector3d(position).add(0.5, 0.5, 0.5).sub(data.cameraPosition)
        matrix.translate(delta.x.toFloat(), delta.y.toFloat(), delta.z.toFloat())
        GFXx3D.drawDebugCube(matrix, 1f, Vector4f(1f))
        matrix.popMatrix()
    }

}