package me.anno.blocks.chunk

import me.anno.blocks.block.BlockSide
import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.visual.BlockVisuals
import me.anno.blocks.block.visual.MaterialType
import me.anno.blocks.block.visual.Texture
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CS3
import me.anno.blocks.chunk.Chunk.Companion.CSm1
import me.anno.blocks.chunk.Chunk.Companion.CSx2Bits
import me.anno.blocks.chunk.Chunk.Companion.CSx2m1
import me.anno.blocks.chunk.Chunk.Companion.dx
import me.anno.blocks.chunk.Chunk.Companion.dy
import me.anno.blocks.chunk.Chunk.Companion.dz
import me.anno.blocks.chunk.mesh.MeshInfo
import me.anno.blocks.rendering.BlockBuffer
import me.anno.blocks.rendering.ShaderLib2
import me.anno.gpu.buffer.StaticBuffer
import me.anno.utils.LOGGER
import org.joml.Matrix4x3f
import org.joml.Vector4f

object BakeMesh {

    inline fun <V> Array<V>.mapBooleanArray(func: (V) -> Boolean): BooleanArray {
        return BooleanArray(size) { func(this[it]) }
    }

    inline fun <V> List<V>.mapBooleanArray(func: (V) -> Boolean): BooleanArray {
        return BooleanArray(size) { func(this[it]) }
    }

    fun bakeMesh(
        side: BlockSide,
        neighborChunk: Chunk?,
        blockStates: Array<BlockState>,
        materialType: MaterialType,
        oldMeshes: MeshInfo?
    ): MeshInfo? {

        // todo create meshes, that are as efficient as possible
        val textures = blockStates.map {
            if (it == Air) null
            else it.block.visuals.getTexture(side)
        }.toMutableList()

        val visuals = blockStates.map { it.block.visuals }

        // remove blocks, which are covered by solid blocks
        when (materialType) {
            MaterialType.SOLID_BLOCK -> {
                val isSolid = visuals.mapBooleanArray { it.materialType == MaterialType.SOLID_BLOCK }
                removeSolidInnerBlocks(side, neighborChunk, textures, isSolid)
            }
            MaterialType.SOLID_COMPLEX -> {
                // todo instanced rendering (?), maybe...
                val isSolid = visuals.mapBooleanArray { it.materialType == MaterialType.SOLID_BLOCK }
                removeSolidInnerBlocks(side, null, textures, isSolid)
            }
            MaterialType.TRANSPARENT_MASS -> {
                removeFluidMassInnerBlocks(side, neighborChunk, textures, visuals)
            }
            MaterialType.TRANSPARENT_COMPLEX -> {
                // todo instanced rendering
            }
            else -> throw RuntimeException("not implemented")
        }

        val nullBlock = 0
        val defaultBlock = Chunk.getSizeInfo(1, 1, 1)

        val mergedBlocks = IntArray(CS3) {
            if (textures[it] == null || visuals[it].materialType != materialType) {
                nullBlock
            } else defaultBlock
        }

        MergeBlocks.mergeBlocks(mergedBlocks, blockStates, visuals.mapBooleanArray { it.supportsRepetitions })

        val vertexCounts = HashMap<String, Int>()
        for ((index, state) in blockStates.withIndex()) {
            if (state !== Air) {
                val visual = state.block.visuals
                val texture = textures[index]
                if (texture != null) {
                    val size = mergedBlocks[index]
                    if (size > 0) {
                        val sx = size.shr(CSx2Bits) and CSx2m1
                        val sy = size.shr(CSx2Bits * 2) and CSx2m1
                        val sz = size and CSx2m1
                        val vc = visual.getVertexCount(sx, sy, sz, side)
                        vertexCounts[texture.path] = (vertexCounts[texture.path] ?: 0) + vc
                    }
                }
            }
        }

        // todo don't destroy, if it has the same length
        // todo don't clear, just replace, if needed
        // todo delete unnecessary
        oldMeshes?.destroy()

        if(vertexCounts.isEmpty()) return null

        val light = Vector4f(1f)

        val meshes2 = HashMap<String, StaticBuffer>()

        val matrix = Matrix4x3f()
        for ((path, vertexCount) in vertexCounts) {
            if (vertexCount > 0) {
                val buffer = StaticBuffer(ShaderLib2.attributes, vertexCount)
                for (y in 0 until CS) {
                    for (x in 0 until CS) {
                        for (z in 0 until CS) {
                            val index = Chunk.getIndex(x, y, z)
                            val size = mergedBlocks[index]
                            if (size > 0) {
                                val texture = textures[index]
                                if (texture?.path == path) {
                                    val state = blockStates[index]
                                    val block = state.block
                                    val visual = block.visuals
                                    matrix.identity()
                                    matrix.translate(x.toFloat(), y.toFloat(), z.toFloat())
                                    val sx = size.shr(CSx2Bits) and CSx2m1
                                    val sy = size.shr(CSx2Bits * 2) and CSx2m1
                                    val sz = size and CSx2m1
                                    visual.createMesh(sx, sy, sz, side) { coordinates ->
                                        BlockBuffer(buffer, matrix, coordinates.uv0, coordinates.uv1) { light }
                                    }
                                }
                            }
                        }
                    }
                }
                if(buffer.nioBuffer!!.position() > 0){
                    meshes2[path] = buffer
                } else LOGGER.warn("Though I'd need a mesh, but I didn't -> wrong vertex count")
            }
        }

        return MeshInfo.get(meshes2)

    }

    fun update(index: Int, types: List<BlockVisuals>, secondType: BlockVisuals, textures: MutableList<Texture?>) {
        val firstType = types[index]
        // remove superfluous blocks
        if (firstType == secondType || secondType.materialType == MaterialType.SOLID_BLOCK) {
            textures[index] = null
        }
    }

    fun chunkBordersX(
        neighborChunk: Chunk?,
        x: Int,
        sideOffset0: Int,
        types: List<BlockVisuals>,
        textures: MutableList<Texture?>
    ) {
        if (neighborChunk == null) return
        for (y in 0 until CS) {
            for (z in 0 until CS) {
                val index = Chunk.getIndex(x, y, z)
                update(index, types, neighborChunk.getBlockState(index + sideOffset0).block.visuals, textures)
            }
        }
    }

    fun chunkBordersY(
        neighborChunk: Chunk?,
        y: Int,
        sideOffset0: Int,
        types: List<BlockVisuals>,
        textures: MutableList<Texture?>
    ) {
        if (neighborChunk == null) return
        for (x in 0 until CS) {
            for (z in 0 until CS) {
                val index = Chunk.getIndex(x, y, z)
                update(index, types, neighborChunk.getBlockState(index + sideOffset0).block.visuals, textures)
            }
        }
    }

    fun chunkBordersZ(
        neighborChunk: Chunk?,
        z: Int,
        sideOffset0: Int,
        types: List<BlockVisuals>,
        textures: MutableList<Texture?>
    ) {
        if (neighborChunk == null) return
        for (x in 0 until CS) {
            for (y in 0 until CS) {
                val index = Chunk.getIndex(x, y, z)
                update(index, types, neighborChunk.getBlockState(index + sideOffset0).block.visuals, textures)
            }
        }
    }

    fun chunkBordersX(
        neighborChunk: Chunk?,
        x: Int,
        sideOffset0: Int,
        isSolid: BooleanArray,
        textures: MutableList<Texture?>
    ) {
        if (neighborChunk == null) return
        for (y in 0 until CS) {
            for (z in 0 until CS) {
                val index = Chunk.getIndex(x, y, z)
                if(isSolid[index] && neighborChunk.isSolid(index+sideOffset0)){
                    textures[index] = null
                }
            }
        }
    }

    fun chunkBordersY(
        neighborChunk: Chunk?,
        y: Int,
        sideOffset0: Int,
        isSolid: BooleanArray,
        textures: MutableList<Texture?>
    ) {
        if (neighborChunk == null) return
        for (x in 0 until CS) {
            for (z in 0 until CS) {
                val index = Chunk.getIndex(x, y, z)
                if(isSolid[index] && neighborChunk.isSolid(index+sideOffset0)){
                    textures[index] = null
                }
            }
        }
    }

    fun chunkBordersZ(
        neighborChunk: Chunk?,
        z: Int,
        sideOffset0: Int,
        isSolid: BooleanArray,
        textures: MutableList<Texture?>
    ) {
        if (neighborChunk == null) return
        for (x in 0 until CS) {
            for (y in 0 until CS) {
                val index = Chunk.getIndex(x, y, z)
                if(isSolid[index] && neighborChunk.isSolid(index+sideOffset0)){
                    textures[index] = null
                }
            }
        }
    }

    fun removeFluidMassInnerBlocks(
        side: BlockSide,
        neighborChunk: Chunk?,
        textures: MutableList<Texture?>,
        types: List<BlockVisuals>
    ) {
        var minX = 0
        var maxX = CS
        var minY = 0
        var maxY = CS
        var minZ = 0
        var maxZ = CS
        val sideOffset0 = side.offset * (1 - CS)
        // chunk borders need to be respected, and superfluous blocks need to be removed
        when (side) {
            BlockSide.NX -> {
                chunkBordersX(neighborChunk, 0, sideOffset0, types, textures)
                minX++
            }
            BlockSide.PX -> {
                chunkBordersX(neighborChunk, CSm1, sideOffset0, types, textures)
                maxX--
            }
            BlockSide.NY -> {
                chunkBordersY(neighborChunk, 0, sideOffset0, types, textures)
                minY++
            }
            BlockSide.PY -> {
                chunkBordersY(neighborChunk, CSm1, sideOffset0, types, textures)
                maxY--
            }
            BlockSide.NZ -> {
                chunkBordersZ(neighborChunk, 0, sideOffset0, types, textures)
                minZ++
            }
            BlockSide.PZ -> {
                chunkBordersZ(neighborChunk, CSm1, sideOffset0, types, textures)
                maxZ--
            }
        }
        val sideOffset = side.offset
        for (y in minY until maxY) {
            for (x in minX until maxX) {
                for (z in minZ until maxZ) {
                    val index = Chunk.getIndex(x, y, z)
                    update(index, types, types[index + sideOffset], textures)
                }
            }
        }
    }

    fun removeSolidInnerBlocks(
        side: BlockSide,
        neighborChunk: Chunk?,
        textures: MutableList<Texture?>,
        isSolid: BooleanArray
    ) {
        var minX = 0
        var maxX = CS
        var minY = 0
        var maxY = CS
        var minZ = 0
        var maxZ = CS
        val sideOffset0 = side.offset * (1 - CS)
        when (side) {
            BlockSide.NX -> {
                chunkBordersX(neighborChunk, 0, sideOffset0, isSolid, textures)
                minX++
            }
            BlockSide.PX -> {
                chunkBordersX(neighborChunk, CSm1, sideOffset0, isSolid, textures)
                maxX--
            }
            BlockSide.NY -> {
                chunkBordersY(neighborChunk, 0, sideOffset0, isSolid, textures)
                minY++
            }
            BlockSide.PY -> {
                chunkBordersY(neighborChunk, CSm1, sideOffset0, isSolid, textures)
                maxY--
            }
            BlockSide.NZ -> {
                chunkBordersZ(neighborChunk, 0, sideOffset0, isSolid, textures)
                minZ++
            }
            BlockSide.PZ -> {
                chunkBordersZ(neighborChunk, CSm1, sideOffset0, isSolid, textures)
                maxZ--
            }
        }
        val sideOffset = side.offset
        for (y in minY until maxY) {
            for (x in minX until maxX) {
                for (z in minZ until maxZ) {
                    val index = Chunk.getIndex(x, y, z)
                    if (isSolid[index] && isSolid[index + sideOffset]) {
                        textures[index] = null
                    }
                }
            }
        }
        for (y in 1 until CSm1) {
            for (x in 1 until CSm1) {
                for (z in 1 until CSm1) {
                    val index = Chunk.getIndex(x, y, z)
                    if (!isSolid[index] && textures[index] != null) {
                        if ( // completely covered
                            isSolid[index + dx] && isSolid[index - dx] &&
                            isSolid[index - dy] && isSolid[index + dy] &&
                            isSolid[index - dz] && isSolid[index + dz]
                        ) {
                            textures[index] = null
                        }
                    }
                }
            }
        }
    }

    fun hasStatesOnSide(states: Array<BlockState>, type: MaterialType, side: BlockSide): Boolean {
        val c = when (side) {
            BlockSide.PX, BlockSide.PY, BlockSide.PZ -> CSm1
            else -> 0
        }
        when (side) {
            BlockSide.NX, BlockSide.PX -> {
                for (i in 0 until CS) {
                    for (j in 0 until CS) {
                        if (states[Chunk.getIndex(c, i, j)].block.visuals.materialType == type)
                            return true
                    }
                }
            }
            BlockSide.NY, BlockSide.PY -> {
                for (i in 0 until CS) {
                    for (j in 0 until CS) {
                        if (states[Chunk.getIndex(i, c, j)].block.visuals.materialType == type)
                            return true
                    }
                }
            }
            BlockSide.NZ, BlockSide.PZ -> {
                for (i in 0 until CS) {
                    for (j in 0 until CS) {
                        if (states[Chunk.getIndex(j, i, c)].block.visuals.materialType == type)
                            return true
                    }
                }
            }
        }
        return false
    }

}