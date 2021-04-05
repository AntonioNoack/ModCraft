package me.anno.blocks.multiplayer

import me.anno.blocks.entity.player.Player
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.utils.ErrorPacket
import me.anno.blocks.world.Dimension
import me.anno.gpu.GFX
import me.anno.utils.hpc.ProcessingQueue
import org.apache.logging.log4j.LogManager
import org.joml.Vector3d
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.ForkJoinWorkerThread
import kotlin.concurrent.thread

open class ServerSideClient(
    var name: String,
    dimension: Dimension
) {

    lateinit var socket: Socket
    lateinit var input: DataInputStream
    lateinit var output: DataOutputStream

    var ping = -1L
    var isClosed = false
    var lastTime = 0L

    fun close(reason: String) {
        LOGGER.info("Closing session, because '$reason'")
        try {
            socket.close()
        } catch (e: Exception) {
        }
        isClosed = true
    }

    constructor(socket: Socket, input: DataInputStream, output: DataOutputStream, dimension: Dimension) :
            this("", dimension) {
        this.socket = socket
        this.input = input
        this.output = output
    }

    val entity = Player(dimension, Vector3d())
    val player get() = entity
    val dimension get() = entity.dimension

    fun send(vararg packets: Packet) {
        if (isClosed) return
        for (packet in packets) {
            send(packet)
        }
    }

    fun send(packet: Packet) {
        if (isClosed) return
        GFX.checkIsNotGFXThread()
        packet.send(output)
        if (packet is ErrorPacket && packet.isDeadly) {
            close(packet.msg)
        }
    }

    val packetWorker = ProcessingQueue("PacketWorker")
        .apply { start() }

    fun sendAsync(packet: Packet){
        if (isClosed) return
        packetWorker += { send(packet) }
    }

    override fun toString(): String = name

    companion object {
        private val LOGGER = LogManager.getLogger(ServerSideClient::class)
    }

}