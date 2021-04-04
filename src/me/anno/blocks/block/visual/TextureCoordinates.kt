package me.anno.blocks.block.visual

import org.joml.Vector2f

class TextureCoordinates(val x: Int, val y: Int, val texture: Texture) {

    constructor(texture: Texture) : this(0, 0, texture)
    constructor() : this(0, 0, whiteTextureInstance)

    val uv0 = Vector2f(x.toFloat()/texture.sx, y.toFloat()/texture.sy)
    val uv1 = Vector2f((x+1f)/texture.sx, (y+1f)/texture.sy)

    companion object {
        val whiteTextureInstance = Texture("white", 1, 1)
    }

}
