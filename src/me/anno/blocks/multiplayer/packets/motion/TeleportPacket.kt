package me.anno.blocks.multiplayer.packets.motion

import me.anno.blocks.entity.player.Player
import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.ExclusiveSender
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.SendRecvUtils.readName8
import me.anno.blocks.multiplayer.SendRecvUtils.readVec
import me.anno.blocks.multiplayer.SendRecvUtils.writeString8
import me.anno.blocks.multiplayer.SendRecvUtils.writeVec
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.TELEPORT
import me.anno.blocks.world.Dimension
import org.joml.Vector3d
import org.joml.Vector3f
import java.io.DataInputStream
import java.io.DataOutputStream

class TeleportPacket(
    var dimension: DimensionData,
    val targetPosition: Vector3d,
    val targetRotation: Vector3f
) : Packet(TELEPORT, ExclusiveSender.Server) {

    class DimensionData(val id: String, val hasBlocksBelowZero: Boolean) {
        constructor(server: Server, client: ServerSideClient) : this(server, client.entity)
        constructor(server: Server, target: Player) : this(target.dimension)
        constructor(dimension: Dimension) : this(dimension.id, dimension.hasBlocksBelowZero)
        constructor() : this("default", false)
    }

    constructor(server: Server, target: Player) : this(
        DimensionData(server, target),
        Vector3d(target.position),
        Vector3f(target.rotation)
    )

    constructor() : this(DimensionData(), Vector3d(), Vector3f())

    override fun onReceive(input: DataInputStream) {
        dimension = DimensionData(
            input.readName8(),
            input.readBoolean()
        )
        input.readVec(targetPosition)
        input.readVec(targetRotation)
    }

    override fun onSend(output: DataOutputStream) {
        dimension.apply {
            output.writeString8(id)
            output.writeBoolean(hasBlocksBelowZero)
        }
        output.writeVec(targetPosition)
        output.writeVec(targetRotation)
    }

    override fun onClient(client: Client) {
        synchronized(client) {
            val world = client.world
            val dimension = world.getOrCreateDimension(dimension.id, dimension.hasBlocksBelowZero, client)
            client.entity.dimension = dimension
            client.position.set(targetPosition)
            client.rotation.set(targetRotation)
        }
    }

}