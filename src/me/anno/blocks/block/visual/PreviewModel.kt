package me.anno.blocks.block.visual

import me.anno.blocks.block.BlockSide
import me.anno.blocks.chunk.mesh.BlockBuffer
import me.anno.blocks.chunk.mesh.MeshInfo
import me.anno.blocks.rendering.RenderData
import me.anno.blocks.rendering.ShaderLib2
import me.anno.blocks.rendering.SolidShader
import me.anno.gpu.buffer.StaticBuffer
import org.joml.Matrix4x3f

class PreviewModel(val model: BlockVisuals) {

    private val vertexCount = BlockSide.values2.sumBy { model.getVertexCount(1, 1, 1, it) }
    private val content = lazy {
        if (vertexCount > 0) {
            val buffers = HashMap<String, StaticBuffer>()
            val vertexCounts = HashMap<String, Int>()
            for (side in BlockSide.values2) {
                val vertexCount = model.getVertexCount(1, 1, 1, side)
                if (vertexCount > 0) {
                    val texture = model.getTexture(side) ?: continue
                    val path = texture.path
                    vertexCounts[path] = (vertexCounts[path] ?: 0) + vertexCount
                }
            }
            for ((path, vertexCount) in vertexCounts) {
                val buffer = StaticBuffer(ShaderLib2.attributes, vertexCount)
                for (side in BlockSide.values2) {
                    if (model.getVertexCount(1, 1, 1, side) > 0) {
                        model.createMesh(1, 1, 1, side) {
                            BlockBuffer(buffer, Matrix4x3f(), it.uv0, it.uv1) { _, _, _ -> 15 }
                        }
                    }
                }
                buffers[path] = buffer
            }
            MeshInfo(buffers)
        } else null
    }

    fun draw(data: RenderData, shader: SolidShader) {
        val info = content.value ?: return
        shader.use()
        for (index in 0 until info.length) {
            info.bindPath(data, index)
            val buffer = info.getBuffer(index)
            buffer.draw(shader)
        }
    }

}