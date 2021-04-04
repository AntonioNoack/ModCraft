package me.anno.blocks.multiplayer

import me.anno.blocks.commands.CommandManager
import me.anno.blocks.multiplayer.SendRecvUtils.readName8
import me.anno.blocks.multiplayer.SendRecvUtils.readString8
import me.anno.blocks.multiplayer.SendRecvUtils.writeString8
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.motion.TeleportPacket
import me.anno.blocks.multiplayer.packets.utils.ErrorPacket
import me.anno.blocks.world.World
import me.anno.blocks.world.generator.PerlinGenerator
import org.apache.logging.log4j.LogManager
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ServerSocket
import kotlin.concurrent.thread

class Server(val port: Int = defaultPort) {

    var name = "Most basic server"
    var motd = "A ModCraft server"

    val world = World()
    val defaultDimension = world.createDimension(PerlinGenerator(), "default")

    val commandManager = CommandManager(world)

    val players = ArrayList<ServerSideClient>()

    var shallStop = false

    fun stop(){
        shallStop = true
    }

    fun start(async: Boolean) {
        if (async) {
            thread { start(false) }
            return
        }
        val server = ServerSocket(port)
        val thread = Thread.currentThread()
        thread.name = "ServerWaiting"
        LOGGER.info("Started Server")
        while (!server.isClosed && !shallStop) {
            try {
                val socket = server.accept()
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                val client = ServerSideClient(socket, input, output, defaultDimension)
                thread {
                    try {
                        handshake(client)
                        addPlayer(client)
                        spawnPlayer(client)
                        readAllPackets(client, input)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        try {
                            client.send(
                                ErrorPacket(
                                    e.message ?: e.javaClass.toString(), true
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    removePlayer(client)
                }
            } catch (e: Exception) {

            }
        }
        try {
            server.close()
        } catch (e: Exception){}
    }

    fun gameTick(){
        // todo calculate game ticks...
        // todo unload no longer required chunks
        // todo update chunks around players to stay loaded...
        // todo send block updates to the players
        // (redstone may become expensive -> what do we do?)
        // todo update frequency depending on distance
    }

    fun removePlayer(client: ServerSideClient) {
        if (synchronized(players) { players.remove(client) }) {
            LOGGER.info("${client.name} left the game")
        }
    }

    fun addPlayer(client: ServerSideClient) {
        synchronized(players) {
            if (players.any { it.name == client.name }) {
                throw EOFException("Name is already taken")
            }
            players += client
        }
        LOGGER.info("${client.name} joined the game")
    }

    fun handshake(client: ServerSideClient) {
        val input = client.input
        val output = client.output
        output.writeString8(name)
        output.writeString8(motd)
        val name = input.readName8()
        client.name = name
        val password = input.readString8()
        client.lastTime = input.readLong()
        val error = playerMayJoin(name, password)
        if (error != null) client.send(ErrorPacket(error, true))
    }

    fun readAllPackets(client: ServerSideClient, input: DataInputStream) {
        LOGGER.info("Reading all packets from client ${client.name}")
        while (true) {
            val packet = Packet.read(input, true)
            packet.onServer(this, client)
        }
    }

    fun playerMayJoin(name: String, password: String): String? {
        if (!name.isNotBlank() && password.isEmpty()) return "Password incorrect"
        synchronized(players) {
            if (players.any { it.name.equals(name, true) }) {
                return "Name already taken"
            }
        }
        return null
    }

    fun spawnPlayer(client: ServerSideClient){
        val defaultDimension = world.getDimension("default")!!
        val player = client.entity
        client.send(TeleportPacket(
            TeleportPacket.DimensionData(
                defaultDimension.id,
                defaultDimension.hasBlocksBelowZero
        ), player.position, player.rotation))
    }

    fun sendAll(packet: Packet) {
        synchronized(players) {
            for (player in players) {
                thread { player.send(packet) }
            }
        }
    }

    companion object {
        val defaultIP = "localhost"
        val defaultPort = 65535
        private val LOGGER = LogManager.getLogger(Server::class)
    }


}