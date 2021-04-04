package me.anno.blocks.multiplayer.packets

import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.ExclusiveSender
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.packets.block.BlockChangePacket
import me.anno.blocks.multiplayer.packets.chunk.ChunkDataPacket
import me.anno.blocks.multiplayer.packets.chunk.ChunkDeniedPacket
import me.anno.blocks.multiplayer.packets.chunk.ChunkRequestPacket
import me.anno.blocks.multiplayer.packets.motion.ClientMovePacket
import me.anno.blocks.multiplayer.packets.motion.EntityUpdatePacket
import me.anno.blocks.multiplayer.packets.motion.TeleportPacket
import me.anno.blocks.multiplayer.packets.utils.ErrorPacket
import me.anno.blocks.multiplayer.packets.utils.MessagePacket
import me.anno.blocks.multiplayer.packets.utils.PingPacket
import org.apache.logging.log4j.LogManager
import java.io.*
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

abstract class Packet(
    val id: Int,
    var isCompressed: Boolean,
    val audience: ExclusiveSender
) {

    constructor(id: Int) : this(id, false, ExclusiveSender.Both)
    constructor(id: Int, isCompressed: Boolean) : this(
        id, isCompressed,
        ExclusiveSender.Both
    )

    constructor(id: Int, audience: ExclusiveSender) : this(id, false, audience)

    fun send(output: DataOutputStream) {
        if (this is ErrorPacket) {
            LOGGER.warn("Sending error packet '$msg'")
        }/* else {
            LOGGER.info("Sending packet of type $id")
        }*/
        synchronized(output) {
            output.write(id)
            if (isCompressed) {
                val bos = ByteArrayOutputStream()
                val zos = DeflaterOutputStream(bos)
                val zosData = DataOutputStream(zos)
                onSend(zosData)
                zosData.flush()
                zos.finish()
                zos.flush()
                val data = bos.toByteArray()
                output.writeInt(data.size)
                output.write(data)
            } else {
                onSend(output)
            }
            output.flush()
        }
    }

    fun receive(input: DataInputStream) {
        if (isCompressed) {
            val size = input.readInt()
            val data = input.readNBytes(size)
            val bis = ByteArrayInputStream(data)
            val zis = InflaterInputStream(bis)
            val zisData = DataInputStream(zis)
            onReceive(zisData)
        } else {
            onReceive(input)
        }
    }

    abstract fun onSend(output: DataOutputStream)
    abstract fun onReceive(input: DataInputStream)

    open fun onServer(server: Server, client: ServerSideClient) {
        throw IllegalArgumentException("$javaClass.onServer undefined")
    }

    open fun onClient(client: Client) {
        throw IllegalArgumentException("$javaClass.onClient undefined")
    }

    companion object {

        private val LOGGER = LogManager.getLogger(Packet::class)

        val listOfPackets: ArrayList<() -> Packet> = arrayListOf(
            { MessagePacket() },
            { PingPacket() }, { PlayerListPacket() },
            { ChunkDataPacket() }, { ChunkDeniedPacket() }, { ChunkRequestPacket() },
            { TeleportPacket() }, { ClientMovePacket() },
            { EntityUpdatePacket() },
            { BlockChangePacket() },
            // { InventoryOpenPacket() }, { InventoryRequestPacket() },
            // ...
            { ErrorPacket() }
        )

        val registry by lazy {
            val registry = HashMap<Int, () -> Packet>()
            listOfPackets.forEach {
                val id = it().id
                if (id in registry) throw RuntimeException("Packet $id was overridden")
                registry[id] = it
            }
            registry
        }

        fun read(input: DataInputStream, isServer: Boolean): Packet {
            val packetType = input.read()
            // LOGGER.info("Reading packet of type $packetType")
            val packetGen = registry[packetType]
            val packet = packetGen?.invoke() ?: ErrorPacket(
                "Packet type $packetType unknown",
                true
            )
            if (isServer && packet.audience == ExclusiveSender.Server) {
                throw EOFException("Packet only can be sent by server")
            }
            if (!isServer && packet.audience == ExclusiveSender.Client) {
                throw EOFException("Packet only can be sent by client")
            }
            packet.receive(input)
            if (packet is ErrorPacket && packet.isDeadly) {
                throw EOFException(packet.msg)
            }
            return packet
        }

    }

}