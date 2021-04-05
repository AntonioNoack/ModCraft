package me.anno.blocks.rendering

import me.anno.blocks.rendering.ShaderLib2.defineTextures
import me.anno.gpu.shader.Shader

class SkyShader(
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
    val matrix = this["matrix"]
    val topColor = this["topColor"]
    val midColor = this["midColor"]
    val bottomColor = this["bottomColor"]

}