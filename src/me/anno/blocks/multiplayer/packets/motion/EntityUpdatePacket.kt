package me.anno.blocks.multiplayer.packets.motion

import me.anno.blocks.entity.Entity
import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.ExclusiveSender
import me.anno.blocks.multiplayer.SendRecvUtils.writeName8
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.ENTITY_UPDATE
import java.io.DataInputStream
import java.io.DataOutputStream

class EntityUpdatePacket(
    var entities: List<Entity>?
) : Packet(ENTITY_UPDATE, ExclusiveSender.Server) {

    constructor() : this(null)

    override fun onSend(output: DataOutputStream) {
        val entities = entities!!
        output.writeInt(entities.size)
        for (entity in entities) {
            output.writeInt(entity.id)
            output.writeName8(entity.type)
            entity.sendState(output)
        }
    }

    override fun onReceive(input: DataInputStream) {
        // todo
        /*val size = input.readInt()
        val entities = ArrayList<Entity>(size)
        this.entities = entities
        for (i in 0 until size) {
            val id = input.readInt()
            val type = input.readShort().toInt() and 0xffff
            val entity = EntityRegistry.get(id, type) ?: continue
            entities += entity
        }*/
    }

    override fun onClient(client: Client) {
        // todo
        /*val world = client.world
        synchronized(world.entities) {
            for (entity in entities!!) {
                if (entity.isDead) {
                    world.entities.remove(entity.id)
                } else {
                    world.entities[entity.id] = entity
                }
            }
        }*/
    }

}