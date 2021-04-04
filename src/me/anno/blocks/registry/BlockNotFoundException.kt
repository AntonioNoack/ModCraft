package me.anno.blocks.registry

import java.lang.RuntimeException

class BlockNotFoundException(id: String): RuntimeException("Block not found '$id'") {
}