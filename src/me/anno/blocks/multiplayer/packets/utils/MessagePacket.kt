package me.anno.blocks.multiplayer.packets.utils

import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.MESSAGE
import org.apache.logging.log4j.LogManager
import java.io.DataInputStream
import java.io.DataOutputStream

class MessagePacket(
    var msg: String,
    var shallBeSent: Boolean // no = we only want hints; yes = please send it
) : Packet(MESSAGE) {

    constructor() : this("", true)
    constructor(msg: String) : this(msg, true)

    override fun onSend(output: DataOutputStream) {
        output.writeUTF(msg)
        output.writeBoolean(shallBeSent)
    }

    override fun onReceive(input: DataInputStream) {
        msg = input.readUTF().trim()
        shallBeSent = input.readBoolean()
    }

    override fun onServer(server: Server, client: ServerSideClient) {
        // first just echo the message to everyone
        if (msg.isNotEmpty()) {
            if (shallBeSent) {
                if(msg.startsWith("/")){
                    server.commandManager.exec(msg.substring(1), server, client)
                } else {
                    server.sendAll(MessagePacket("${client.name}: $msg", true))
                }
            } else {
                // todo give hints to the player...
            }
        }
    }

    override fun onClient(client: Client) {
        if (shallBeSent) {
            // todo add message to chat
            if (msg.isNotEmpty()) {
                LOGGER.info(msg)
            } else {
                // special command to clear the chat <3 :D
                // todo clear chat...
            }
        } else {
            // todo send help

        }
    }

    companion object {
        private val LOGGER = LogManager.getLogger(MessagePacket::class)
    }

}