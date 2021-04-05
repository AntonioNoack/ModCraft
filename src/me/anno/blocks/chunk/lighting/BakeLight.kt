package me.anno.blocks.chunk.lighting

import me.anno.blocks.block.base.GlowstoneBlock
import me.anno.blocks.chunk.Chunk
import me.anno.blocks.chunk.Chunk.Companion.AIR
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CS2
import me.anno.blocks.chunk.Chunk.Companion.CS3
import me.anno.blocks.chunk.Chunk.Companion.CSBits
import me.anno.blocks.chunk.Chunk.Companion.CSm1
import me.anno.blocks.chunk.ramless.ChunkContent
import me.anno.blocks.chunk.ramless.FullChunkContent
import me.anno.blocks.chunk.ramless.LayeredChunkContent
import me.anno.blocks.chunk.ramless.SimplestChunkContent
import me.anno.blocks.utils.struct.Vector3j
import me.anno.blocks.world.Dimension
import me.anno.blocks.world.World
import me.anno.blocks.world.generator.PerlinGenerator
import me.anno.utils.image.ImageWriter.writeImageInt
import me.anno.utils.structures.arrays.IntArrayList
import me.anno.utils.types.Floats.f3
import org.apache.logging.log4j.LogManager
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min

// is there something brighter than sunlight? no
object BakeLight {

    val EnableLighting = true

    private val LOGGER = LogManager.getLogger(BakeLight::class)

    @JvmStatic
    fun main(args: Array<String>) {
        // create a test (or we could just test with the whole world lol)
        val world = World()
        val dimension = world.createDimension(PerlinGenerator(), "default")
        val lightInfo = LightInfo()
        val coo = Vector3j(0, 2, 0)
        dimension.setBlock(Vector3j(0, 2 * CS + 15, 0), true, GlowstoneBlock.nullState)
        export(dimension.getChunk(coo, true)!!.content)
        calculateLightValues(dimension, coo, 5, lightInfo)
        export(lightInfo)
    }

    fun export(content: ChunkContent) {
        content as FullChunkContent
        val values = content.blocks
        val w = CS2
        val h = CS
        val colors = IntArray(256) { if (it == AIR) -1 else it * 48 * 0x10101 }
        writeImageInt(w, h, false, "terrain.png", w * h) { x, lz, _ ->
            val ly = x shr CSBits
            val lx = x and CSm1
            colors[values[Chunk.getIndex(lx, ly, lz)].toInt()]
        }
    }

    fun export(lightInfo: LightInfo) {
        val w = CS2
        val h = CS
        val colors = IntArray(1 shl 16) {
            val a = it.and(aMask)
            val r = min(it.and(rMask).shr(12) + a, 15)
            val g = min(it.and(gMask).shr(12) + a, 15)
            val b = min(it.and(bMask).shr(12) + a, 15)
            r.shl(20) or g.shl(12) or b.shl(4)
        }
        writeImageInt(w, h, false, "light.png", w * h) { x, lz, _ ->
            val ly = x shr CSBits
            val lx = x and CSm1
            colors[lightInfo.getLightLevel(lx, ly, lz).toInt().and(0xffff)]
        }
    }

    fun calculateLightValues(
        dimension: Dimension,
        coordinates: Vector3j,
        chunksOnTop: Int,
        dstInfo: LightInfo
    ) {

        if (!Thread.currentThread().name.startsWith("Generator-")) throw IllegalAccessException()

        LOGGER.info("Starting $coordinates")

        val t0 = System.nanoTime()

        val border = 16 // 15 is the max light level, and it would reach 1 with this setting -> perfect :)
        val sizeX = border * 2 + CS
        val sizeZ = sizeX + 0
        val sizeY = border + CS * (1 + chunksOnTop)

        val size = sizeX * sizeY * sizeZ

        val lightLevels = ShortArray(size)
        val isSolid = BooleanArray(size)

        for (dy in -1..(chunksOnTop + 1)) {
            for (dx in -1..+1) {
                for (dz in -1..+1) {
                    // todo engine is hanging here... why? not generating enough chunks???... how does this deadlock?
                    if(dx != 0 || dy != 0 || dz != 0) continue
                    val chunk2 = dimension.getChunk(Vector3j(coordinates, dx, dy, dz), true)
                    if (chunk2 != null) fillBlocksIntoBuffer(
                        lightLevels, isSolid, chunk2.content,
                        border, dx, dy, dz, sizeX, sizeY, sizeZ
                    )
                }
            }
        }

        // clock.stop("Filling input")

        // populate the containers: isSolid and emission values
        /*val x0 = chunk.coordinates.x * CS - border
        val y0 = chunk.coordinates.y * CS - border
        val z0 = chunk.coordinates.z * CS - border
        var index = 0
        for (y in y0 until y0 + sizeY) {
            for (x in x0 until x0 + sizeX) {
                for (z in z0 until z0 + sizeX) {
                    // this is way too inefficient; we should cache the chunk
                    // even better would be, if we would fill the isSolid/lightLevels chunk per chunk
                    val blockState = dimension.getBlock(x, y, z, true).block
                    isSolid[index] = blockState.isSolid
                    lightLevels[index] = blockState.lightState
                    index++
                }
            }
        }*/

        LOGGER.info("Calculating $coordinates")

        val t1 = System.nanoTime()

        val zero = 0.toShort()
        val hasLights = lightLevels.any { it != zero }

        calculateLightValues(lightLevels, isSolid, sizeX, sizeY, sizeZ, !hasLights)

        val t2 = System.nanoTime()

        LOGGER.info("Filling up $coordinates")

        fillValuesIntoResult(lightLevels, border, sizeX, sizeZ, dstInfo)

        val t3 = System.nanoTime()
        LOGGER.info("${((t1 - t0) * 1e-9f).f3()} + ${((t2 - t1) * 1e-9f).f3()} + ${((t3 - t2) * 1e-9f).f3()}")

        // clock.stop("Filling result")

    }

    fun fillBlocksIntoBuffer(
        lightLevels: ShortArray,
        isSolid: BooleanArray,
        content: ChunkContent,
        border: Int,
        dx: Int, dy: Int, dz: Int,
        sizeX: Int, sizeY: Int, sizeZ: Int
    ) {
        // destination space
        val x0 = dx * CS + border
        val y0 = dy * CS + border
        val z0 = dz * CS + border
        when (content) {
            is SimplestChunkContent -> {
                val block = content.state.block
                if (block.isSolid || block.isLight) {
                    // we need to fill
                    val solid = block.isLightSolid
                    val lightState = block.lightState
                    for (dstY in max(y0, 0) until min(y0 + CS, sizeY)) {
                        for (dstX in max(x0, 0) until min(x0 + CS, sizeX)) {
                            for (dstZ in max(z0, 0) until min(z0 + CS, sizeZ)) {
                                val dstIndex = getIndex(sizeX, sizeZ, dstX, dstY, dstZ)
                                isSolid[dstIndex] = solid
                                lightLevels[dstIndex] = lightState
                            }
                        }
                    }
                }
            }
            is LayeredChunkContent -> {
                for (dstY in max(y0, 0) until min(y0 + CS, sizeY)) {
                    val chunkY = dstY - y0
                    val block = content.layers[chunkY].block
                    val solid = block.isLightSolid
                    val lightState = block.lightState
                    for (dstX in max(x0, 0) until min(x0 + CS, sizeX)) {
                        for (dstZ in max(z0, 0) until min(z0 + CS, sizeZ)) {
                            val dstIndex = getIndex(sizeX, sizeZ, dstX, dstY, dstZ)
                            isSolid[dstIndex] = solid
                            lightLevels[dstIndex] = lightState
                        }
                    }
                }
            }
            else -> {
                for (dstY in max(y0, 0) until min(y0 + CS, sizeY)) {
                    for (dstX in max(x0, 0) until min(x0 + CS, sizeX)) {
                        for (dstZ in max(z0, 0) until min(z0 + CS, sizeZ)) {
                            val chunkX = dstX - x0
                            val chunkY = dstY - y0
                            val chunkZ = dstZ - z0
                            val blockState = content.getBlock(chunkX, chunkY, chunkZ)
                            val dstIndex = getIndex(sizeX, sizeZ, dstX, dstY, dstZ)
                            isSolid[dstIndex] = blockState.isLightSolid
                            lightLevels[dstIndex] = blockState.lightState
                        }
                    }
                }
            }
        }
    }

    fun fillValuesIntoResult(
        lightLevels: ShortArray,
        border: Int,
        sizeX: Int, sizeZ: Int,
        dstInfo: LightInfo
    ) {
        var index = 0
        val sizeXZ = sizeX * sizeZ
        val dst = ShortArray(CS3)
        for (y in 0 until CS) {
            val yOffset = sizeXZ * (y + border)
            for (x in 0 until CS) {
                for (z in 0 until CS) {
                    dst[index++] = lightLevels[getIndex(sizeZ, x + border, z + border) + yOffset]
                }
            }
        }
        dstInfo.update(dst)
    }

    fun getIndex(sizeZ: Int, x: Int, z: Int): Int {
        return x * sizeZ + z
    }

    fun getIndex(sizeX: Int, sizeZ: Int, x: Int, y: Int, z: Int): Int {
        return (y * sizeX + x) * sizeZ + z
    }

    fun getX(sizeX: Int, sizeZ: Int, index: Int): Int {
        return (index / sizeZ) % sizeX
    }

    fun getY(sizeXZ: Int, index: Int): Int {
        return index / sizeXZ
    }

    fun getZ(sizeZ: Int, index: Int): Int {
        return index % sizeZ
    }

    const val rBase = 256 * 16
    const val gBase = 256
    const val bBase = 16
    const val aBase = 1

    const val rMask = rBase * 15
    const val gMask = gBase * 15
    const val bMask = bBase * 15
    const val aMask = aBase * 15

    const val rMask2 = rMask.toShort()
    const val gMask2 = gMask.toShort()
    const val bMask2 = bMask.toShort()
    const val aMask2 = aMask.toShort()

    const val rgbMask = (rMask or gMask or bMask).toShort()

    fun isLessBright(x: Short, y: Short): Boolean {
        return (x.and(rMask2) < y.and(rMask2) ||
                x.and(gMask2) < y.and(gMask2) ||
                x.and(bMask2) < y.and(bMask2) ||
                x.and(aMask2) < y.and(aMask2))
    }

    fun calculateLightValues(
        lightLevels: ShortArray,
        isSolid: BooleanArray,
        sizeX: Int, // 3 * CS
        sizeY: Int, // N * CS
        sizeZ: Int, // 3 * CS
        hasNoLights: Boolean
        // we could scale them up to 4*CS to save on computations (& + >> + << instead of * + % + /)
        // but the saving is, that computations are cheap, memory access is expensive, soo...
    ) {

        val todoList = IntArrayList(512)

        val sizeXZ = sizeX * sizeZ

        fun transferFunction(ownLight: Int, neighborLight: Int, reduceSunlight: Boolean): Int {
            val r = max(ownLight.and(rMask), neighborLight.and(rMask) - rBase)
            val g = max(ownLight.and(gMask), neighborLight.and(gMask) - gBase)
            val b = max(ownLight.and(bMask), neighborLight.and(bMask) - bBase)
            var otherA = neighborLight.and(aMask)
            if (reduceSunlight) otherA -= aBase
            val a = max(ownLight.and(aMask), otherA)
            return r or g or b or a
        }

        fun transferFunction(ownLight: Short, neighborLight: Short, reduceSunlight: Boolean): Short {
            return transferFunction(ownLight.toInt(), neighborLight.toInt(), reduceSunlight).toShort()
        }

        fun todoMaybe(x: Int, y: Int, z: Int, index: Int, neighborLight: Short) {
            if (x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ) {
                if (!isSolid[index]) {
                    val lightThere = lightLevels[index]
                    if (isLessBright(lightThere, neighborLight)) {
                        todoList += index
                    }
                }
            }
        }

        val sunlight = aMask.toShort()

        fun addNeighborsMaybe(x: Int, y: Int, z: Int, index0: Int, light0: Short, dy: Boolean) {
            if (dy) {
                todoMaybe(x, y - 1, z, index0 - sizeXZ, light0)
                todoMaybe(x, y + 1, z, index0 + sizeXZ, light0)
            }
            todoMaybe(x - 1, y, z, index0 - sizeZ, light0)
            todoMaybe(x + 1, y, z, index0 + sizeZ, light0)
            todoMaybe(x, y, z - 1, index0 - 1, light0)
            todoMaybe(x, y, z + 1, index0 + 1, light0)
        }

        val sxm1 = sizeX - 1
        val sym1 = sizeY - 1
        val szm1 = sizeZ - 1

        if (hasNoLights) {

            // if there are no lights,
            // count how many points there are, where there is rock above air
            // if zero, just fill everything with sunlight
            // this is often the case for deserts, plains and mountains

            var rockAboveAirCounter = 0
            search@ for (y in 0 until sizeY - 1) {
                for (xz in 0 until sizeXZ) {
                    if (isSolid[xz] && !isSolid[xz + sizeXZ]) {
                        rockAboveAirCounter++
                        break@search
                    }
                }
            }

            // LOGGER.info("Rock above Air: $rockAboveAirCounter")

            if (rockAboveAirCounter == 0) {
                for (index in 0 until sizeXZ * sizeY) {
                    lightLevels[index] = if (isSolid[index]) sunlight else 0
                }
                return
            }

        }

        // fill sunlight
        val maxYIndex = sizeXZ * (sizeY - 1)
        for (index in maxYIndex until maxYIndex + sizeXZ) {
            if (!isSolid[index]) {

                val x = getX(sizeX, sizeZ, index)
                val z = getZ(sizeZ, index)

                // adding sunlight
                lightLevels[index] = sunlight

                // forgetting about the border, we can just make it one larger, and the issue is solved
                if (x !in 1 until sxm1 || z !in 1 until szm1) {
                    continue
                }

                // propagating the sunlight down as far as possible, as it's faster than our todoList
                var index2 = index

                // whether the neighbors need updates
                var nx = isSolid[index2 - sizeZ]
                var px = isSolid[index2 + sizeZ]
                var nz = isSolid[index2 - 1]
                var pz = isSolid[index2 + 1]

                index2 -= sizeXZ

                while (!isSolid[index2] && index2 >= sizeXZ) {

                    val nx2 = isSolid[index2 - sizeZ]
                    val px2 = isSolid[index2 + sizeZ]
                    val nz2 = isSolid[index2 - 1]
                    val pz2 = isSolid[index2 + 1]

                    nx = nx || nx2
                    nz = nz || nz2
                    px = px || px2
                    pz = pz || pz2

                    lightLevels[index2] = sunlight

                    if (nx && !nx2) todoList += index2 - sizeZ
                    if (px && !px2) todoList += index2 + sizeZ
                    if (nz && !nz2) todoList += index2 - 1
                    if (pz && !pz2) todoList += index2 + 1


                    index2 -= sizeXZ

                }

            }
        }

        LOGGER.info("Tasks before emissive blocks: ${todoList.size}")

        // find all emissive light values
        var index0 = 0
        for (y in 0 until sizeY) {
            for (x in 0 until sizeX) {
                for (z in 0 until sizeZ) {
                    // add all potential neighbors to require an update
                    val light0 = lightLevels[index0]
                    if (light0.and(rgbMask) > 0) {
                        addNeighborsMaybe(x, y, z, index0, light0, true)
                    }
                    index0++
                }
            }
        }

        LOGGER.info("Tasks after emissive blocks: ${todoList.size}")

        var i = -1
        while (++i < todoList.size) {
            val index = todoList[i]
            if (isSolid[index]) throw IllegalStateException()
            val oldLight = lightLevels[index]
            val x = getX(sizeX, sizeZ, index)
            val y = getY(sizeXZ, index)
            val z = getZ(sizeZ, index)
            var light = oldLight
            fun check(index2: Int, dy: Boolean) {
                if (!isSolid[index2]) light = transferFunction(light, lightLevels[index2], dy)
            }
            // update the light value
            if (x > 0) check(index - sizeZ, true)
            if (x < sxm1) check(index + sizeZ, true)
            if (z > 0) check(index - 1, true)
            if (z < szm1) check(index + 1, true)
            if (y > 0) check(index - sizeXZ, true)
            if (y < sym1) check(index + sizeXZ, false)
            if (oldLight != light) {
                // if changed, add neighbors, which are not solid
                lightLevels[index] = light
                addNeighborsMaybe(x, y, z, index, light, true)
            }
        }
    }

}