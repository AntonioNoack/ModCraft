package me.anno.blocks.block.visual

enum class MaterialType(val id: Int, val needsSorting: Boolean) {
    SOLID_BLOCK(0, false), // solid mode, baked mesh
    SOLID_COMPLEX(1, false), // solid mode, instanced rendering? would probably be the best :)
    TRANSPARENT_MASS(2, true), // transparent mode,
    TRANSPARENT_COMPLEX(3, true) // transparent mode, instanced rendering?
    ;

    companion object {
        val MaterialTypeCount = values().size
    }
}