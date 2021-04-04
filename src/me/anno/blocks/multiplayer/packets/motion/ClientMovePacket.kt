package me.anno.blocks.multiplayer.packets.motion

import me.anno.blocks.entity.player.Player
import me.anno.blocks.multiplayer.ExclusiveSender
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.SendRecvUtils.readString8
import me.anno.blocks.multiplayer.SendRecvUtils.readVec
import me.anno.blocks.multiplayer.SendRecvUtils.writeString8
import me.anno.blocks.multiplayer.SendRecvUtils.writeVec
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.CLIENT_MOVE
import org.joml.Vector3d
import org.joml.Vector3f
import java.io.DataInputStream
import java.io.DataOutputStream

class ClientMovePacket(
    var dimension: String,
    val clientPosition: Vector3d,
    val clientRotation: Vector3f,
    val clientVelocity: Vector3f,
    var clientTime: Long = System.nanoTime()
) : Packet(CLIENT_MOVE, ExclusiveSender.Client) {

    constructor(player: Player) : this(player.dimension.id, player.position, player.rotation, player.velocity, System.nanoTime())
    constructor() : this("", Vector3d(), Vector3f(), Vector3f(), 0L)

    override fun onSend(output: DataOutputStream) {
        output.writeString8(dimension)
        output.writeVec(clientPosition)
        output.writeVec(clientRotation)
        output.writeLong(clientTime)
    }

    override fun onReceive(input: DataInputStream) {
        dimension = input.readString8()
        input.readVec(clientPosition)
        input.readVec(clientRotation)
        clientTime = input.readLong()
    }

    override fun onServer(server: Server, client: ServerSideClient) {

        // todo check if the movement is allowed (collisions)
        // todo check that the clock of the client isn't running too fast or slow

        val lastTime = client.lastTime
        val thisTime = clientTime
        val deltaNanos = thisTime - lastTime
        val deltaSeconds = deltaNanos * 1e-9
        client.lastTime = thisTime

        val entity = client.entity
        val velocity = entity.position.distance(clientPosition) / deltaSeconds

        entity.position.set(clientPosition)
        entity.rotation.set(clientRotation)

        // todo answer with list of all other player updates? would be reasonable :)
        // todo also send a teleport packet, if the player offset is too large

        val dimension = entity.dimension
        val closeChunks = synchronized(dimension.chunks){
            dimension.chunks.filterValues {
                it.center.distanceSquared(clientPosition) < MAX_ENTITY_CHUNK_DISTANCE_SQ
            }.toList()
        }

        val closeEntities = closeChunks.flatMap { (_, value) ->
            value.entities.filter {
                it.position.distanceSquared(clientPosition) < MAX_ENTITY_CHUNK_DISTANCE_SQ
            }
        }

        val sendingEntities =
            if (closeEntities.size < ENTITY_LIMIT) closeEntities
            else closeEntities.subList(0, ENTITY_LIMIT)
        EntityUpdatePacket(sendingEntities)


    }

    companion object {
        const val ENTITY_LIMIT = 512
        const val MAX_ENTITY_DISTANCE = 250.0
        const val MAX_ENTITY_DISTANCE_SQ = MAX_ENTITY_DISTANCE * MAX_ENTITY_DISTANCE
        const val MAX_ENTITY_CHUNK_DISTANCE = MAX_ENTITY_DISTANCE + 32.0 * 1.732 // sqrt3
        const val MAX_ENTITY_CHUNK_DISTANCE_SQ = MAX_ENTITY_CHUNK_DISTANCE * MAX_ENTITY_CHUNK_DISTANCE
    }

}