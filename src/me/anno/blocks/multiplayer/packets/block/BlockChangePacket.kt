package me.anno.blocks.multiplayer.packets.block

import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.SendRecvUtils.readName8
import me.anno.blocks.multiplayer.SendRecvUtils.readVec
import me.anno.blocks.multiplayer.SendRecvUtils.writeName8
import me.anno.blocks.multiplayer.SendRecvUtils.writeVec
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.BLOCK_CHANGE
import me.anno.blocks.utils.readString
import me.anno.blocks.utils.struct.Vector3j
import me.anno.blocks.utils.writeString
import me.anno.blocks.world.Dimension
import org.joml.Vector3i
import java.io.DataInputStream
import java.io.DataOutputStream

class BlockChangePacket(
    val coordinates: Vector3i,
    var oldBlockId: String,
    var oldState: String?,
    var newBlockId: String,
    var newState: String?
) : Packet(BLOCK_CHANGE) {

    constructor(coordinates: Vector3j, newBlock: BlockState, oldBlock: BlockState) :
            this(coordinates.mutable(), newBlock, oldBlock)

    constructor(coordinates: Vector3i, newBlock: BlockState, oldBlock: BlockState) :
            this(coordinates, newBlock.block.id, newBlock.state, oldBlock.block.id, oldBlock.state)

    constructor() : this(Vector3i(), Air.block.id, null, Air.block.id, null)

    override fun onSend(output: DataOutputStream) {
        output.writeVec(coordinates)
        output.writeName8(newBlockId)
        output.writeString(newState)
        output.writeName8(oldBlockId)
        output.writeString(oldState)
    }

    override fun onReceive(input: DataInputStream) {
        input.readVec(coordinates)
        newBlockId = input.readName8()
        newState = input.readString()
        oldBlockId = input.readName8()
        oldState = input.readString()
    }

    override fun onClient(client: Client) {
        val block = client.world.registry.getBlock(newBlockId)
        // we only need to update the chunk, if we have it locally
        val chunk = client.dimension.getChunkAtMaybe(coordinates, true)
        chunk?.setBlock(coordinates, BlockState(block, newState))
    }

    fun switch() {
        val s = oldState
        oldState = newState
        newState = s
        val t = oldBlockId
        oldBlockId = newBlockId
        newBlockId = t
    }

    override fun onServer(server: Server, client: ServerSideClient) {
        if (canChangeBlock(
                server,
                client,
                coordinates,
                newBlockId,
                newState
            ) && matchesExistingBlock(client.dimension)
        ) {
            val dimension = client.dimension
            val world = dimension.world
            val registry = world.registry
            val newBlock = registry.getBlock(newBlockId)
            client.dimension.setBlock(coordinates, true, BlockState(newBlock, newState))
            // todo if not creative mode, remove block from inventory
        } else {
            switch()
            client.send(this)
        }
    }

    fun matchesExistingBlock(dimension: Dimension): Boolean {
        val world = dimension.world
        val registry = world.registry
        val oldBlock = registry.getBlock(oldBlockId)
        val c = coordinates
        return dimension.getBlock(c.x, c.y, c.z, true) == BlockState(oldBlock, oldState)
    }

    fun canChangeBlock(
        server: Server,
        client: ServerSideClient,
        coordinates: Vector3i,
        blockId: String,
        state: String?
    ): Boolean {
        val distance = client.entity.position.distance(coordinates.x + 0.5, coordinates.y + 0.5, coordinates.z + 0.5)
        return distance < MAX_DISTANCE
    }

    val MAX_DISTANCE = 5f

}