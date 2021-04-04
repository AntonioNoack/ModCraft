package me.anno.blocks.commands

object CommandSplitter {

    // todo don't split objects
    // todo parse objects
    fun split(command: String): List<Any> = command.split(' ')

}