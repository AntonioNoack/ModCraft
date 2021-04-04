package me.anno.blocks.commands

import me.anno.blocks.multiplayer.ServerSideClient
import me.anno.blocks.multiplayer.Server

typealias Action = (server: Server, client: ServerSideClient, command: List<Any>, arguments: List<Any>) -> String?