package me.anno.blocks.multiplayer.packets.chunk

import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.half
import me.anno.blocks.multiplayer.ExclusiveSender
import me.anno.blocks.multiplayer.SendRecvUtils.readName8
import me.anno.blocks.multiplayer.SendRecvUtils.readVec
import me.anno.blocks.multiplayer.SendRecvUtils.writeName8
import me.anno.blocks.multiplayer.SendRecvUtils.writeVec
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.CHUNK_REQUEST
import me.anno.blocks.multiplayer.packets.utils.ErrorPacket
import me.anno.blocks.utils.struct.Vector3j
import org.apache.logging.log4j.LogManager
import org.joml.Vector3d
import org.joml.Vector3i
import java.io.DataInputStream
import java.io.DataOutputStream

class ChunkRequestPacket(
    val coordinates: Vector3i,
    var dimension: String
) : Packet(CHUNK_REQUEST, ExclusiveSender.Client) {

    constructor() : this(Vector3i(), "")

    constructor(coordinates: Vector3j, dimension: String) : this(coordinates.mutable(), dimension)

    override fun onReceive(input: DataInputStream) {
        input.readVec(coordinates)
        dimension = input.readName8()
    }

    override fun onSend(output: DataOutputStream) {
        output.writeVec(coordinates)
        output.writeName8(dimension)
    }

    override fun onServer(server: Server, client: ServerSideClient) {
        // LOGGER.info("Got request from $client: $coordinates")
        // check if the player has access to the chunk
        if (hasAccess(client)) {
            // send him the chunk he requested
            val world = server.world
            val dimension = world.dimensions[dimension]
            if (dimension == null) {
                client.send(ErrorPacket("Dimension is null", true))
            } else {
                val chunk = dimension.getChunk(coordinates, true)
                if (chunk != null) {
                    if (!chunk.isFinished) throw RuntimeException("Chunk was unfinished")
                    client.send(ChunkDataPacket(chunk))
                } else client.send(ChunkDeniedPacket(this))
            }
        } else {
            client.send(ChunkDeniedPacket(this))
        }
    }

    fun hasAccess(client: ServerSideClient): Boolean {
        val player = client.entity
        if (player.dimension.id != dimension) return false
        val chunkPos = Vector3d(coordinates).add(half).mul(CS.toDouble())
        val distanceSquared = player.position.distanceSquared(chunkPos)
        val maxDistance = 1e3
        return distanceSquared < maxDistance * maxDistance
    }

    companion object {
        private val LOGGER = LogManager.getLogger(ChunkRequestPacket::class)
    }

}