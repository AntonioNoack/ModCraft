package me.anno.blocks.multiplayer.packets

import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.SendRecvUtils.readName8
import me.anno.blocks.multiplayer.SendRecvUtils.writeString8
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.packets.PacketIDs.PLAYER_LIST
import me.anno.utils.Maths.clamp
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.min

// todo send image as well?
class PlayerListPacket(var players: List<PlayerInfo>?) : Packet(PLAYER_LIST) {

    class PlayerInfo(val name: String, val ping: Int) {
        constructor(client: ServerSideClient) : this(
            client.name,
            clamp(client.ping, 0, 10_000).toInt()
        )

        constructor(input: DataInputStream) : this(
            input.readName8(), input.readInt()
        )

        fun write(output: DataOutputStream) {
            output.writeString8(name)
            output.writeInt(ping)
        }
    }

    constructor() : this(null)

    override fun onSend(output: DataOutputStream) {
        val players = players
        if (players == null) output.writeInt(0)
        else {
            output.writeInt(players.size)
            for (i in 0 until min(100, players.size)) {
                val info = players[i]
                info.write(output)
            }
        }
    }

    override fun onReceive(input: DataInputStream) {
        val length = input.readInt()
        players = Array(length) {
            PlayerInfo(input)
        }.toList()
    }

    override fun onServer(server: Server, client: ServerSideClient) {
        // we know our players -> it's a request
        val players = server.players
        val infos = synchronized(players) { players.map { PlayerInfo(it) } }
        client.send(PlayerListPacket(infos))
    }

    override fun onClient(client: Client) {
        client.playerInfo = players
    }

}