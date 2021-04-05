package me.anno.blocks.chunk.lighting

import me.anno.blocks.chunk.Chunk.Companion.getIndex
import org.joml.Vector4f
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.min

class LightInfo {

    var isValid = false
    var isCalculating = false

    // at half positions, block centers, so we can interpolate (?)
    var firstIndex = -1
    var lastIndex = -1
    var firstValue: Short = 0
    var lastValue: Short = 0
    var values: ShortArray? = null

    fun fillBlack() {
        values = null
        firstIndex = -1
        firstValue = 0
    }

    fun fill(value: Short) {
        values = null
        firstIndex = -1
        firstValue = value
    }

    fun update(values: ShortArray) {
        val firstValue = values.first()
        val firstIndex = values.indexOfFirst { it != firstValue }
        this.firstIndex = firstIndex
        this.firstValue = firstValue
        if (firstIndex >= 0) {
            val lastValue = values.last()
            val lastIndex = values.indexOfLast { it != lastValue }
            val values2 = ShortArray(lastIndex - firstIndex + 1)
            System.arraycopy(values, firstIndex, values2, 0, values2.size)
            this.values = values2
            this.lastValue = lastValue
            this.lastIndex = lastIndex
        }
    }

    fun write(output: DataOutputStream) {
        val firstIndex = firstIndex
        val lastIndex = lastIndex
        output.writeInt(firstIndex)
        output.writeShort(firstValue.toInt())
        if (firstIndex >= 0) {
            val values = values!!
            output.writeInt(lastIndex)
            output.writeShort(lastValue.toInt())
            for (index in firstIndex..lastIndex) {
                output.writeShort(values[index - firstIndex].toInt())
            }
        }
    }

    fun read(input: DataInputStream) {
        firstIndex = input.readInt()
        firstValue = input.readShort()
        if (firstIndex >= 0) {
            lastIndex = input.readInt()
            lastValue = input.readShort()
            val values = ShortArray(lastIndex-firstIndex+1)
            this.values = values
            val firstIndex = firstIndex
            for (index in firstIndex..lastIndex) {
                values[index-firstIndex] = input.readShort()
            }
        }
    }

    fun getLightLevel(x: Int, y: Int, z: Int) = getLightLevel(getIndex(x,y,z))
    fun getLightLevel(index: Int): Short {
        if(firstIndex < 0 || index < firstIndex) return firstValue
        if(index <= lastIndex) return values!![index-firstIndex]
        return lastValue
    }

    fun getX(value: Int) = value.shr(12).and(15)
    fun getY(value: Int) = value.shr(8).and(15)
    fun getZ(value: Int) = value.shr(4).and(15)
    fun getW(value: Int) = value.and(15)

    fun get(value: Int, dst: Vector4f) {
        dst.set(
            value.shr(12).and(15) / 15f,
            value.shr(8).and(15) / 15f,
            value.shr(4).and(15) / 15f,
            value.and(15) / 15f
        )
    }

    /**
     * sunLight: 0 .. 15
     * */
    fun getBrightness(value: Int, sunLight: Int): Int {
        val w = getW(value) * sunLight
        val r = getX(value)
        val g = getY(value)
        val b = getZ(value)
        val perceivedBrightnessX30 = r * 9 + g * 18 + b * 3
        val sunLightBrightnessX30 = w * 2
        return min((perceivedBrightnessX30 + sunLightBrightnessX30) / 30, 15)
    }

    // 0.299*R + 0.587*G + 0.114*B
    // sqrt(0.299*R^2 + 0.587*G^2 + 0.114*B^2)


}