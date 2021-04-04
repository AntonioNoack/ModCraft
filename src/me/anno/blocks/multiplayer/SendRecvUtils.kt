package me.anno.blocks.multiplayer

import org.joml.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import kotlin.math.min

object SendRecvUtils {

    fun DataInputStream.readString8(filter: (Char) -> Boolean) =
        readString8().filter { filter(it) }

    val NameFilter: (Char) -> Boolean = { it in '0'..'Z' || it in 'A'..'Z' || it in 'a'..'z' || it in " _-." }
    fun DataInputStream.readName8() = readString8(NameFilter).trim()

    fun DataInputStream.readString8(): String {
        val length = read()
        if(length < 0) throw EOFException("Length < 0")
        val bytes = ByteArray(length){ readByte() }
        return String(bytes)
    }

    fun DataOutputStream.writeName8(value: String){
        if(value.filter(NameFilter) != value) throw IllegalArgumentException("'$value' isn't a name")
        writeString8(value)
    }

    fun DataOutputStream.writeString8(value: String) {
        val bytes = value.toByteArray()
        if(bytes.size > 255) throw IllegalArgumentException("String is too long")
        write(bytes.size)
        write(bytes)
    }

    fun DataInputStream.readVec(v: Vector3i){
        v.set(readInt(), readInt(), readInt())
    }

    fun DataInputStream.readVec(v: Vector3f){
        v.set(readFloat(), readFloat(), readFloat())
    }

    fun DataInputStream.readVec(v: Vector3d){
        v.set(readDouble(), readDouble(), readDouble())
    }

    fun DataOutputStream.writeVec(v: Vector3ic){
        writeInt(v.x())
        writeInt(v.y())
        writeInt(v.z())
    }

    fun DataOutputStream.writeVec(v: Vector3fc){
        writeFloat(v.x())
        writeFloat(v.y())
        writeFloat(v.z())
    }

    fun DataOutputStream.writeVec(v: Vector3dc){
        writeDouble(v.x())
        writeDouble(v.y())
        writeDouble(v.z())
    }

}