package me.anno.blocks.commands

import me.anno.blocks.block.BlockState
import me.anno.blocks.chunk.Chunk.Companion.getIndex
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.packets.chunk.ChunkDataPacket
import me.anno.blocks.multiplayer.packets.motion.TeleportPacket
import me.anno.blocks.multiplayer.packets.utils.MessagePacket
import me.anno.blocks.world.World
import org.joml.Vector3i
import kotlin.concurrent.thread

class CommandManager(val world: World) {

    val blockRegistry = world.registry

    val commands = HashMap<List<String>, Pair<String, Action>>()

    // todo add permissions by default...
    fun register(names: Any, usage: String, action: Action) {
        when (names) {
            is String -> commands[listOf(names.toLowerCase())] = usage to action
            is List<*> -> {
                val listOfNames = names.any { it is List<*> }
                if (listOfNames) {
                    for (name in names) {
                        name ?: continue
                        register(name, usage, action)
                    }
                } else {
                    val names2 = names.filterIsInstance<String>()
                    commands[names2.map { it.toLowerCase() }] = usage to action
                }
            }
        }
    }

    fun exec(command: String, server: Server, client: ServerSideClient) {
        exec(CommandSplitter.split(command), server, client)
    }

    /**
     * returns whether a command was found
     * */
    fun exec(command: List<Any>, server: Server, client: ServerSideClient): Boolean {
        for (i in command.lastIndex downTo 1) {
            val commandVariant = command.subList(0, i).map { if (it is String) it.toLowerCase() else it }
            val (usage, action) = commands[commandVariant] ?: continue
            val arguments = command.subList(i, command.size)
            val msg = try {
                action(server, client, commandVariant, arguments)?.trim()
            } catch (e: UsageException) {
                "Usage: ${commandVariant.joinToString(" ")} $usage"
            } catch (e: ParamException){
                e.message
            }
            if (msg != null && msg.isNotEmpty()) {
                client.send(MessagePacket(msg, true))
            }
            return true
        }
        client.send(MessagePacket("Command '${command.first()}' was not found", true))
        return false
    }

    init {
        register("setBlock", usage("x", "y", "z", "type")) { server, client, _, arguments ->
            if (arguments.size != 4) throw UsageException
            val x = parseInt(arguments[0])
            val y = parseInt(arguments[1])
            val z = parseInt(arguments[2])
            val type = parseType(arguments[3])
            if (type != null) {
                val dimension = client.entity.dimension
                val chunk = dimension.getChunkAt(Vector3i(x, y, z), true)
                if (chunk != null) {
                    chunk.setBlock(getIndex(x, y, z), type)
                    client.send(ChunkDataPacket(chunk))
                }
                null
            } else "Block type '$type' unknown"
        }
        register("msg", usage("player", "message")) { server, client, _, arguments ->
            if (arguments.size < 2) throw UsageException
            val message = arguments.subList(1, arguments.size).joinToString(" ")
            val target = getPlayer(server, arguments[0])
            val packet = MessagePacket("[/msg] ${client.name}: $message")
            thread { target.send(packet) }
            thread { client.send(packet) }
            null
        }
        register(
            listOf("tp", "teleport", emptyList<Int>()),
            usage("x", "y", "z") + " or " + usage("player")
        ) { server, client, _, arguments ->
            when (arguments.size) {
                1 -> {
                    val target = getPlayer(server, arguments[0])
                    if (target !== client) {
                        client.entity.position.set(target.entity.position)
                        client.send(TeleportPacket(server, target.entity))
                        null
                    } else "Cannot teleport to yourself"
                }
                3 -> {
                    val x = parseDouble(arguments[0])
                    val y = parseDouble(arguments[1])
                    val z = parseDouble(arguments[2])
                    client.entity.position.set(x,y,z)
                    client.send(TeleportPacket(server, client.entity))
                    null
                }
                else -> throw UsageException
            }
        }
        register("clear-cache", usage()){ server, _, _, _ ->
            server.world.dimensions.values.forEach { dimension ->
                synchronized(dimension.chunks){
                    dimension.chunks.clear()
                }
            }
            null
        }
        register(
            "kill",
            usage("player") + " or "+ "@e/@p/@..."
        ){ server, client, _, arguments ->
            if(arguments.size > 1) throw UsageException
            val player = if(arguments.isEmpty()) client else getPlayer(server, arguments[0])
            // todo kill the player...
            null
        }
    }

    fun getPlayer(server: Server, value: Any) = getPlayer(server, value.toString())
    fun getPlayer(server: Server, value: String) =
        server.players.firstOrNull { it.name.equals(value, true) }
            ?: throw ParamException("Player '$value' not found")

    fun parseInt(value: Any) = value.toString().toIntOrNull() ?: throw UsageException
    fun parseDouble(value: Any) = value.toString().toDoubleOrNull() ?: throw UsageException

    fun parseType(type: Any): BlockState? {
        val blockState = type.toString()
        val colonIndex = blockState.indexOf(':')
        val block = if(colonIndex < 0) blockState else blockState.substring(0, colonIndex)
        return blockRegistry.blocks[block]?.run {
            val state = if(colonIndex < 0) null else blockState.substring(colonIndex+1)
            BlockState(this, state)
        }
    }

    fun usage(vararg names: String) = names.joinToString(" ") { "<$it>" }

    class ParamException(msg: String) : Throwable(msg)

    object UsageException : Throwable()

}