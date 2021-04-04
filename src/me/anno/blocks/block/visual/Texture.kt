package me.anno.blocks.block.visual

import me.anno.cache.instances.ImageCache
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering

class Texture(val path: String, val sx: Int, val sy: Int) {

    fun bind(index: Int){
        ImageCache.getInternalTexture(path, false)?.bind(index, GPUFiltering.NEAREST, Clamping.REPEAT)
    }

}