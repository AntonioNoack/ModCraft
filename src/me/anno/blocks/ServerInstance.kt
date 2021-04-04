package me.anno.blocks

import me.anno.blocks.multiplayer.Server

class ServerInstance {

    // todo open console only...
    fun run(){
        Server().start(false)
    }

}