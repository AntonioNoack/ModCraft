package me.anno.blocks.multiplayer.packets.utils

import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.SendRecvUtils.readString8
import me.anno.blocks.multiplayer.SendRecvUtils.writeString8
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.ERROR
import org.apache.logging.log4j.LogManager
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

class ErrorPacket(
    var msg: String,
    var isDeadly: Boolean
) : Packet(ERROR) {

    constructor() : this("ErrorPacket", false)

    override fun onReceive(input: DataInputStream) {
        msg = input.readString8()
        isDeadly = input.readBoolean()
    }

    override fun onSend(output: DataOutputStream) {
        output.writeString8(msg)
        output.writeBoolean(isDeadly)
        LOGGER.info("$msg:$isDeadly")
    }

    override fun onClient(client: Client) {
        onReceive()
    }

    override fun onServer(server: Server, client: ServerSideClient) {
        onReceive()
    }

    fun onReceive() {
        if (isDeadly) throw EOFException(msg)
        else warn()
    }

    fun warn(){
        LOGGER.error("'$msg'")
    }

    companion object {
        private val LOGGER = LogManager.getLogger(ErrorPacket::class)
    }

}