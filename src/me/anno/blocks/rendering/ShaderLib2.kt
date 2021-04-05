package me.anno.blocks.rendering

import me.anno.gpu.ShaderLib.brightness
import me.anno.gpu.ShaderLib.createShader
import me.anno.gpu.buffer.Attribute
import me.anno.gpu.shader.Shader
import org.lwjgl.opengl.GL20

object ShaderLib2 {

    lateinit var solidShader: SolidShader

    lateinit var skyShader: SkyShader

    val attributes = listOf(
        Attribute("coordinates", 3),
        Attribute("normals", 3),
        Attribute("uvsNormalized", 2),
        Attribute("uvsMinMax", 4),
        Attribute("lights", 4)
    )

    val noise3D = "" +
            "float rand(vec2 co){\n" +
            "    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);\n" +
            "}" +
            "float noise(vec3 v){" +
            "   return rand(vec2(rand(v.xy), v.z));\n" +
            "}"

    fun init() {

        val applyTint = "" +
                "   if(tint.a > 0.0) gl_FragColor.rgb = mix(gl_FragColor.rgb, tint.rgb, tint.a);\n"

        val skyColor = "" +
                "uniform vec3 topColor, midColor, bottomColor;\n" +
                "uniform float sunSize, sunSharpness;\n" +
                "vec3 sq(vec3 color){ return color*color; }\n" +
                "vec3 getSkyColor(){\n" +
                "   vec3 normal = normalize(position);\n" +
                "   vec3 sunColor = vec3(1.0);\n" +
                "   vec3 s1 = normal.y < 0.0 ? bottomColor : midColor;\n" +
                "   vec3 s2 = normal.y < 0.0 ? midColor : topColor;\n" +
                "   vec3 skyColor = mix(s1*s1, s2*s2, normal.y < 0.0 ? normal.y*0.7 + 1.0 : normal.y*0.7);\n" +
                "   float sunFactor = pow(.5-.5*dot(normal, sunDir), 10.0);\n" +
                "   return mix(skyColor, sunColor, sunFactor);\n" +
                "}"

        skyShader = SkyShader(
            "sky", "" +
                    "attribute vec3 attr0;\n" +
                    "uniform mat4 matrix;\n" +
                    noise3D +
                    "void main(){" +
                    "   gl_Position = matrix * vec4(attr0, 1.0);\n" +
                    "   gl_Position.z = gl_Position.w * 0.99999;\n" +
                    "   position = attr0;\n" +
                    "}", "" +
                    "varying vec3 position;\n", "" +
                    "uniform vec3 sunDir;\n" +
                    "uniform vec4 tint;\n" +
                    skyColor +
                    "void main(){" +
                    "   vec3 color = sqrt(getSkyColor());\n" +
                    "   gl_FragColor = vec4(color, 1.0);\n" +
                    // "   gl_FragColor = vec4(mix(color, tint.rgb, tint.a), 1.0);\n" +
                    "}", listOf()
        )

        val solidShaderVert = "" +
                "attribute vec3 coordinates;\n" +
                "attribute vec3 normals;\n" +
                "attribute vec2 uvsNormalized;\n" +
                "attribute vec4 uvsMinMax;\n" +
                "attribute vec4 lights;\n" +
                "uniform vec3 offset;\n" +
                "uniform mat4 matrix;\n" +
                "void main(){" +
                "   position = coordinates + offset;\n" +
                "   gl_Position = matrix * vec4(position, 1.0);\n" +
                "   normal = normals;\n" +
                "   bakedLight = lights;\n" +
                "   uvMinMax = uvsMinMax;\n" +
                "   uvNormalized = uvsNormalized;\n" +
                "}"
        val solidShaderVarying = "" +
                "varying vec3 position;\n" +
                "varying vec3 normal;\n" +
                "varying vec4 bakedLight;\n" +
                "varying vec4 uvMinMax;\n" +
                "varying vec2 uvNormalized;\n"
        solidShader = SolidShader(
            "solid", solidShaderVert, solidShaderVarying, "" +
                    "uniform vec3 sunDir, sunLight, baseLight;\n" +
                    "uniform vec4 tint;\n" +
                    "uniform float fogFactor, alpha;\n" +
                    "uniform float showLight;\n" +
                    "uniform sampler2D colors;\n" +
                    brightness +
                    "void main(){" +
                    "   vec2 uv = mix(uvMinMax.xy, uvMinMax.zw, fract(uvNormalized));\n" +
                    "   vec4 rawColor = texture(colors, uv);\n" +
                    "   if(rawColor.a < 0.5) discard;\n" + // todo dithering
                    // "   float sunLightInfluence = mix(1.0, light.a, showLight);\n" +
                    "   vec3 light3 = dot(sunDir, normal) * sunLight + baseLight;\n" +
                    "   gl_FragColor = mix(vec4(light3, 1.0) * rawColor, vec4(bakedLight.rga, 1.0), showLight);\n" +
                    "   float distance = length(position);\n" +
                    "   float relativeDistance = distance * fogFactor;\n" +
                    "   gl_FragColor.a *= alpha/(1.0+relativeDistance*relativeDistance);\n" +
                    applyTint +
                    "}", listOf("colors")
        )
    }

    fun <V : Shader> defineTextures(shader: V, textures: List<String>): V {
        shader.use()
        textures.forEachIndexed { index, name ->
            GL20.glUniform1i(shader[name], index)
        }
        return shader
    }

}