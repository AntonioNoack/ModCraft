package me.anno.blocks.multiplayer.packets.utils

import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.multiplayer.packets.Packet
import me.anno.blocks.multiplayer.packets.PacketIDs.PING
import java.io.DataInputStream
import java.io.DataOutputStream

class PingPacket : Packet(PING) {

    var isFirst = false
    var time0 = System.nanoTime()

    override fun onSend(output: DataOutputStream) {
        output.writeLong(time0)
        isFirst = false
    }

    override fun onReceive(input: DataInputStream) {
        time0 = input.readLong()
        isFirst = false
    }

    override fun onClient(client: Client) {
        if (isFirst) client.send(this)
        else client.ping = System.nanoTime() - time0
    }

    override fun onServer(server: Server, client: ServerSideClient) {
        if (isFirst) client.send(this)
        else client.ping = System.nanoTime() - time0
    }

}