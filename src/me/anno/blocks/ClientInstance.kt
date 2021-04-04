package me.anno.blocks

import me.anno.blocks.entity.player.Player
import me.anno.blocks.multiplayer.Client
import me.anno.blocks.multiplayer.Server
import me.anno.blocks.rendering.ShaderLib2
import me.anno.blocks.ui.CommandPanel
import me.anno.blocks.ui.DimensionView
import me.anno.blocks.world.Dimension
import me.anno.blocks.world.World
import me.anno.config.DefaultConfig
import me.anno.gpu.Window
import me.anno.studio.Logging
import me.anno.studio.StudioBase
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.debug.ConsoleOutputPanel

class ClientInstance(var playerName: String = "Hans") : StudioBase(
    false, "ModCraft", 10000
) {

    var client: Client? = null

    val world: World? get() = client?.world
    val dimension: Dimension? get() = player?.dimension
    val player: Player? get() = client?.entity

    lateinit var dimensionView: DimensionView
    lateinit var console: CommandPanel

    override fun createUI() {
        dimensionView = DimensionView(this, { client }, { dimension }, { player })
        windowStack.clear()
        if (client == null) tryStartClient()
        val list = PanelListY(DefaultConfig.style)
        list.add(dimensionView.apply { setWeight(1f) })
        list.add(CommandPanel { client }.apply { console = this })
        list.add(ConsoleOutputPanel(DefaultConfig.style).apply { Logging.console = this })
        windowStack.add(Window(list))
        DefaultConfig["debug.ui.enableVsync"] = false
    }

    override fun onGameInit() {
        DefaultConfig.init()
        ShaderLib2.init()
        tryStartServer()
        tryStartClient()
    }

    override fun onGameClose() {
        server?.stop()
    }

    override fun onGameLoopStart() {

    }

    override fun onGameLoopEnd() {

    }

    // todo open window and such...

    var server: Server? = null

    fun tryStartServer() {
        server = Server()
        server!!.start(true)
    }

    fun tryStartClient(name: String = playerName) {
        client = Client(name, this).apply { start(true) }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ClientInstance("Hans").run()
        }
    }

}