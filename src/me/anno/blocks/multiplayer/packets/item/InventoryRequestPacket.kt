package me.anno.blocks.multiplayer.packets.item

/*class InventoryRequestPacket(
    val location: Vector3i
) : Packet(INVENTORY_REQUEST, ExclusiveSender.Client) {

    constructor(): this(Vector3i())

    override fun onSend(output: DataOutputStream) {
        output.writeVec(location)
    }

    override fun onReceive(input: DataInputStream) {
        input.readVec(location)
    }

    override fun onServer(server: Server, client: ServerSideClient) {
        val player = client.playerEntity
        if(location.x == Int.MIN_VALUE){
            openPlayerInventory(client, player)
        } else {
            openChestInventory(server, client, player)
        }
    }

    fun openPlayerInventory(client: ServerSideClient, player: Player){
        // todo
    }

    fun openChestInventory(server: Server, client: ServerSideClient, player: Player){
        val world = server.world
        val dim = player.dimension
        val dimension = world.getDimension(dim)!!
        val distance =
            player.position.distanceSquared(location.x.toDouble(), location.y.toDouble(), location.z.toDouble())
        // check whether the player is close enough
        if (distance < MAX_DISTANCE_SQ) {
            val chunk = dimension.getChunkAt(location) ?: return
            val blockIndex = getIndex(location)
            val blockId = chunk.getBlock(blockIndex)
            if (blockId != 0) {
                val inventory = synchronized(chunk){
                    val blockInstance = BlockRegistry.blocks[blockId]
                    if (blockInstance is InventoryBlock) {
                        val meta = chunk.blockMeta[blockIndex]
                        val (inventory, newMeta) = blockInstance.getInventory(meta, player)
                        if (newMeta == null) chunk.blockMeta.remove(blockIndex)
                        else chunk.blockMeta[blockIndex] = newMeta
                        inventory
                    } else null
                }
                if(inventory != null){
                    client.send(
                        InventoryOpenPacket(inventory)
                    )
                }
            }
        }
    }

    companion object {

        fun ownInventory() = InventoryRequestPacket(Vector3i(Int.MIN_VALUE))

        val MAX_DISTANCE = 4.5f
        val MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE

    }

}*/