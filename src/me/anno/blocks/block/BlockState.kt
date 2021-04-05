package me.anno.blocks.block

data class BlockState(val block: Block, val state: String?){

    val isSolid get() = block.isSolid
    val isLight get() = block.isLight

    override fun hashCode(): Int {
        return block.hashCode() + 31 * state.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is BlockState &&
                other.block === block && other.state == state
    }

    override fun toString(): String = "${block.id}:$state"

}