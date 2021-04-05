package me.anno.blocks.rendering

import me.anno.blocks.rendering.ShaderLib2.defineTextures
import me.anno.gpu.shader.Shader

class SolidShader(
    shaderName: String,
    vertex: String, varying: String, fragment: String, textures: List<String>
) :
    Shader(shaderName, vertex, varying, fragment) {
    init {
        defineTextures(this, textures)
    }

    val sunDir = this["sunDir"]
    val sunLight = this["sunLight"]
    val baseLight = this["baseLight"]
    val tint = this["tint"]
    val fogFactor = this["fogFactor"]
    val matrix = this["matrix"]
    val offset = this["offset"]
    val alpha = this["alpha"]

}