package me.anno.blocks.utils

import me.anno.blocks.block.BlockState
import me.anno.blocks.multiplayer.SendRecvUtils.readString8
import me.anno.blocks.multiplayer.SendRecvUtils.writeString8
import me.anno.blocks.registry.BlockRegistry
import me.anno.utils.input.readNBytes2
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

fun DataOutputStream.writeString(value: String?) {
    if (value == null) writeInt(0)
    else {
        val bytes = value.toByteArray()
        writeInt(bytes.size)
        write(bytes)
    }
}

fun DataInputStream.readString(): String? {
    val length = readInt()
    return if (length == 0) null
    else String(readNBytes2(length))
}

fun DataOutputStream.writeBlockState(block: BlockState) {
    writeString8(block.block.id)
    val state = block.state
    if (state == null) writeInt(0)
    else {
        val bytes = state.toByteArray()
        writeInt(bytes.size)
        write(bytes)
    }
}

fun DataInputStream.readBlockState(registry: BlockRegistry): BlockState {
    val id = readString8()
    val block = registry.blocks[id] ?: throw RuntimeException("Block '$id' was not found")
    val size = readInt()
    val state = if (size <= 0) null else String(readNBytes2(size))
    return BlockState(block, state)
}

fun main() {

    fun testIO() {

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        dos.writeString(null)
        dos.writeString("hey")
        dos.writeString("")
        dos.flush()

        val bis = bos.toByteArray().inputStream()
        val dis = DataInputStream(bis)
        println(dis.readString())
        println(dis.readString())
        println(dis.readString())

    }

    testIO()

}