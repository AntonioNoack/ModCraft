package me.anno.blocks.ui

import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.packets.utils.MessagePacket
import me.anno.config.DefaultConfig
import me.anno.ui.input.TextInput
import org.lwjgl.glfw.GLFW
import kotlin.concurrent.thread

class CommandPanel(val getClient: () -> Client?) : TextInput("Write message or command", false, DefaultConfig.style) {

    init {
        hide()
    }

    override fun onEnterKey(x: Float, y: Float) {
        val value = text.trim()
        if (value.isNotEmpty()) {
            setText("", false)
            getClient()?.sendAsync(MessagePacket(value, true))
            unfocusAndHide()
        }
    }

    override fun onKeyDown(x: Float, y: Float, key: Int) {
        if (key == GLFW.GLFW_KEY_TAB) {
            val value = text.trim()
            getClient()?.sendAsync(MessagePacket(value, true))
        }
    }

    override fun onEscapeKey(x: Float, y: Float) {
        unfocusAndHide()
    }

    private fun unfocusAndHide() {
        getClient()?.ui?.dimensionView?.requestFocus()
        hide()
    }

}