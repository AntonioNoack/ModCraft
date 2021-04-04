package me.anno.blocks.chunk.mesh

import me.anno.gpu.buffer.StaticBuffer

class MeshInfo(
    val paths: Array<String>?,
    val buffers: Array<StaticBuffer>?
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

    fun destroy() {
        buffers?.forEach { it.destroy() }
    }

    companion object {
        fun get(data: Map<String, StaticBuffer>): MeshInfo? {
            return if(data.isEmpty()) null
            else MeshInfo(data)
        }
    }

}