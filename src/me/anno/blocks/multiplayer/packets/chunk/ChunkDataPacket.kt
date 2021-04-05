package me.anno.blocks.multiplayer.packets.chunk

import me.anno.blocks.chunk.Chunk
import me.anno.blocks.chunk.lighting.BakeLight.EnableLighting
import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.ExclusiveSender
import me.anno.blocks.multiplayer.SendRecvUtils.readName8
import me.anno.blocks.multiplayer.SendRecvUtils.readVec
import me.anno.blocks.multiplayer.SendRecvUtils.writeName8
import me.anno.blocks.multiplayer.SendRecvUtils.writeVec
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.CHUNK_DATA
import me.anno.utils.Sleep
import org.apache.logging.log4j.LogManager
import org.joml.Vector3i
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.max

class ChunkDataPacket(
    val coordinates: Vector3i,
    var dimension: String,
    var data: ByteArray?
) : Packet(CHUNK_DATA, true, ExclusiveSender.Server) {

    constructor() : this(Vector3i(), "", null)
    constructor(chunk: Chunk) : this(chunk.coordinates.mutable(), chunk.dimension.id, getData(chunk))

    companion object {
        private val LOGGER = LogManager.getLogger(ChunkDataPacket::class)
        fun getData(chunk: Chunk): ByteArray {
            synchronized(chunk) {
                val buffer = ByteArrayOutputStream(4096)
                val dos = DataOutputStream(buffer)
                LOGGER.info("Sending ${chunk.coordinates}")
                chunk.optimizeMaybe()
                chunk.content.write(dos)
                if (EnableLighting) {
                    LOGGER.info("Sent main content ${chunk.coordinates}")
                    chunk.dimension.lightingWorker += { chunk.calculateLightMap() }
                    while (!chunk.lightMap.isValid) Sleep.sleepShortly()
                    LOGGER.info("Started writing ${chunk.coordinates}")
                    chunk.lightMap.write(dos)
                }
                LOGGER.info("Sent ${chunk.coordinates}")
                dos.flush()
                return buffer.toByteArray()
            }
        }
    }

    override fun onSend(output: DataOutputStream) {
        // LOGGER.info("Sending chunk packet $coordinates")
        output.writeVec(coordinates)
        output.writeName8(dimension)
        val data = data!!
        output.writeInt(data.size)
        output.write(data)
    }

    override fun onReceive(input: DataInputStream) {
        input.readVec(coordinates)
        dimension = input.readName8()
        val length = max(input.readInt(), 0)
        val data = ByteArray(length)
        input.readNBytes(data, 0, length)
        this.data = data
    }

    override fun onClient(client: Client) {
        // LOGGER.info("Got packet from client: $coordinates, ${data?.size}")
        val world = client.world
        val dimension = world.getDimension(dimension)!!
        val data = data
        if (data == null) {
            LOGGER.warn("Data is null")
            return
        }
        val chunk = dimension.getChunk(coordinates, false)
        if (chunk == null) {
            LOGGER.warn("Chunk is null")
            return
        }
        val dis = DataInputStream(data.inputStream())
        chunk.content = chunk.content.read(dis, chunk.dimension.world.registry)
        if (EnableLighting) chunk.lightMap.read(dis)
        LOGGER.info("Got chunk $coordinates")
        chunk.finish()
    }

}