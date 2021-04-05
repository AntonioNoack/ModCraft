package me.anno.blocks.chunk.mesh

import me.anno.blocks.rendering.RenderData
import me.anno.cache.instances.ImageCache
import me.anno.gpu.GFX
import me.anno.gpu.TextureLib
import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering

class MeshInfo(
    private val paths: Array<String>?,
    private val buffers: Array<StaticBuffer>?
) {

    constructor(data: Map<String, StaticBuffer>) :
            this(
                if (data.isEmpty()) null
                else data.keys.toTypedArray(),
                if (data.isEmpty()) null
                else data.values.toTypedArray()
            )

    val length = paths?.size ?: 0

    fun getPath(index: Int) = paths!![index]
    fun getBuffer(index: Int) = buffers!![index]

    fun bindPath(data: RenderData, index: Int){
        val path = getPath(index)
        if (path != data.lastTexture) {

            GFX.check()

            val texture = ImageCache.getInternalTexture(path, true) ?: TextureLib.whiteTexture
            texture.bind(GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
            data.lastTexture = path

        }
    }

    fun destroy() {
        buffers?.forEach { it.destroy() }
    }

    companion object {
        fun get(data: Map<String, StaticBuffer>): MeshInfo? {
            return if (data.isEmpty()) null
            else MeshInfo(data)
        }
    }

}