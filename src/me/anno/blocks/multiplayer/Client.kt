package me.anno.blocks.multiplayer

import me.anno.blocks.ClientInstance
import me.anno.gpu.GFX
import me.anno.blocks.multiplayer.SendRecvUtils.readName8
import me.anno.blocks.multiplayer.SendRecvUtils.readString8
import me.anno.blocks.multiplayer.SendRecvUtils.writeString8
import me.anno.blocks.multiplayer.Server.Companion.defaultIP
import me.anno.blocks.multiplayer.Server.Companion.defaultPort
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PlayerListPacket
import me.anno.blocks.world.World
import me.anno.blocks.world.generator.NothingGenerator
import org.apache.logging.log4j.LogManager
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import kotlin.concurrent.thread

class Client(
    name: String,
    val ui: ClientInstance,
    val ip: String = defaultIP,
    val port: Int = defaultPort
) : ServerSideClient(name, getDimension()) {

    var playerInfo: List<PlayerListPacket.PlayerInfo>? = null

    val position = entity.position
    val rotation = entity.rotation

    val world = dimension.world

    var password = ""

    var serverName = ""
    var serverMotd = ""

    var crashMessage: String? = null
    val hasCrashed get() = crashMessage != null

    companion object {
        fun getDimension() = World().createDimension(NothingGenerator(), "void")
    }

    fun start(async: Boolean) {
        if (async) {
            thread(name = "Client"){ start(false) }
            return
        }
        socket = Socket(ip, port)
        LOGGER.info("Connected to $ip:$port")
        input = DataInputStream(socket.getInputStream())
        output = DataOutputStream(socket.getOutputStream())
        try {
            handshake()
            readAllPackets()
        } catch (e: Exception) {
            crashMessage = e.message ?: e.javaClass.toString()
            e.printStackTrace()
            close(crashMessage!!)
        }
    }

    fun readAllPackets() {
        LOGGER.info("Reading all packets from server")
        while (true) {
            val packet = Packet.read(input, false)
            packet.onClient(this)
        }
    }

    fun handshake() {
        serverName = input.readName8()
        serverMotd = input.readString8()
        LOGGER.info("Connected to $serverName ($serverMotd)")
        output.writeString8(this.name)
        output.writeString8(this.password)
        output.writeLong(GFX.gameTime)
    }

    private val LOGGER = LogManager.getLogger(Client::class)

}