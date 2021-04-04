package me.anno.blocks.multiplayer.packets.chunk

import me.anno.blocks.block.base.Stone
import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.ExclusiveSender
import me.anno.blocks.multiplayer.SendRecvUtils.readName8
import me.anno.blocks.multiplayer.SendRecvUtils.readVec
import me.anno.blocks.multiplayer.SendRecvUtils.writeName8
import me.anno.blocks.multiplayer.SendRecvUtils.writeVec
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.CHUNK_DENIED
import org.joml.Vector3i
import java.io.DataInputStream
import java.io.DataOutputStream

class ChunkDeniedPacket(var dimension: String, val coordinates: Vector3i) :
    Packet(CHUNK_DENIED, ExclusiveSender.Server) {

    constructor(request: ChunkRequestPacket): this(request.dimension, request.coordinates)
    constructor() : this("", Vector3i())

    override fun onReceive(input: DataInputStream) {
        dimension = input.readName8()
        input.readVec(coordinates)
    }

    override fun onSend(output: DataOutputStream) {
        output.writeName8(dimension)
        output.writeVec(coordinates)
    }

    override fun onClient(client: Client) {
        val dimension = client.world.getDimension(dimension) ?: return
        dimension.unlockRequestSlot()
        val chunk = dimension.getChunk(coordinates, false) ?: return
        chunk.fill(Stone)
        chunk.finish()
    }

}