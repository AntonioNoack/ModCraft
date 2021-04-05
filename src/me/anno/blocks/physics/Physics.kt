package me.anno.blocks.physics

import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.physical.PhysicsType
import me.anno.blocks.entity.Entity
import me.anno.blocks.utils.floorToInt
import me.anno.blocks.world.Dimension
import me.anno.utils.Maths.clamp
import me.anno.utils.types.Floats.f3
import me.anno.utils.types.Lists.sumByFloat
import org.apache.logging.log4j.LogManager
import org.joml.*
import kotlin.math.floor

object Physics {

    private val LOGGER = LogManager.getLogger(Physics::class)

    val gravity = -9.81f

    val maxStep = 0.2f // to prevent collisions

    val frictionMultiplier = 5f

    // test raytracing
    @JvmStatic
    fun main(args: Array<String>) {
        raytrace(
            Vector3d(-10.0), Vector3f(1f), 20f,
            false, true, null, true
        ) { _, _, _ ->
            Air
        }
        LOGGER.info("---")
        raytrace(
            Vector3d(10.0), Vector3f(-1f), 20f,
            false, true, null, true
        ) { _, _, _ -> Air }
    }

    fun raytrace(
        origin: Vector3d,
        direction: Vector3f,
        maxDistance: Float,
        hasBlocksBelowZero: Boolean,
        canHitFluids: Boolean,
        entities: List<Entity>?,
        debug: Boolean = false,
        getBlock: (x: Int, y: Int, z: Int) -> BlockState?
    ): RaycastHit? {

        direction.normalize()

        var bestDistance = maxDistance.toDouble()
        var hitEntity: Entity? = null
        if (entities != null) {
            // todo get all chunks in distance... or sorted?...

        }

        var distance = 0.0

        val positionI = Vector3i()
        val position = Vector3d()

        val previousPosition = origin.floorToInt()
        var previousBlock = Air

        fun checkHitExact(blockState: BlockState): RaycastHit {
            // todo check collision with collision mesh???...
            // println("hit $blockState at $c")
            position.set(direction).mul(distance).add(origin)
            return BlockHit(distance, position, previousPosition, previousBlock, positionI, blockState)
        }

        val minY = if (hasBlocksBelowZero) Int.MIN_VALUE else 0

        fun checkHit(c: Vector3i): RaycastHit? {
            if (debug) LOGGER.debug("Checking ${c.x} ${c.y} ${c.z}")
            val block = getBlock(c.x, c.y, c.z) ?:
                return UnloadedChunkHit(distance, position)
            val result = if (block != Air) {
                val physics = block.block.physical
                when (physics.type) {
                    PhysicsType.SOLID -> {
                        checkHitExact(block)
                    }
                    PhysicsType.FLUID -> {
                        if (canHitFluids) {
                            checkHitExact(block)
                        } else null
                    }
                    PhysicsType.AIR -> {
                        // nothing
                        null
                    }
                    PhysicsType.FRICTION -> {
                        checkHitExact(block)
                    }
                    else -> throw NotImplementedError()
                }
            } else null
            if (result == null) previousBlock = block
            return result
        }


        // acceptable inaccuracy
        val epsilon = 1e-4f
        val maxSteps = 1000
        var steps = 0

        val dx = if (direction.x > 0f) +1 else -1
        val dy = if (direction.y > 0f) +1 else -1
        val dz = if (direction.z > 0f) +1 else -1

        val dirX = direction.x
        val dirY = direction.y
        val dirZ = direction.z

        // extend the line to the world
        if (!hasBlocksBelowZero && origin.y < minY && direction.y > 0f) {
            val minLength = (minY - origin.y) / direction.y
            distance = minLength
            position.set(direction).mul(distance).add(origin)
            positionI.set(floor(position.x).toInt(), minY, floor(position.z).toInt())
            if (debug) LOGGER.debug("Jumped to $distance")
        } else {
            position.set(origin)
            positionI.x = floor(position.x).toInt()
            positionI.y = floor(position.y).toInt()
            positionI.z = floor(position.z).toInt()
            if (debug) LOGGER.debug("Set start position")
        }

        while (steps++ < maxSteps) {

            if (!hasBlocksBelowZero && positionI.y < minY) {
                break
            }

            // increase the coordinates until we read the next block
            val hit = checkHit(positionI)
            if (hit != null) return hit
            else previousPosition.set(positionI)

            val fractX = position.x - positionI.x
            val fractY = position.y - positionI.y
            val fractZ = position.z - positionI.z

            // we need to go forward
            // from origin to next wall
            val sx = (if (dirX > 0f) 1f - fractX else -fractX) / dirX
            val sy = (if (dirY > 0f) 1f - fractY else -fractY) / dirY
            val sz = (if (dirZ > 0f) 1f - fractZ else -fractZ) / dirZ

            if (debug) LOGGER.debug("${sx.f3()} ${sy.f3()} ${sz.f3()}")

            distance = StrictMath.min(sx, StrictMath.min(sy, sz))
            if (distance >= bestDistance) break

            when (distance) {
                sx -> positionI.x += dx
                sy -> positionI.y += dy
                sz -> positionI.z += dz
            }

            if (debug) LOGGER.debug("$distance: $position")

        }

        if (hitEntity != null) {
            position.set(direction).mul(bestDistance).add(origin)
            return EntityHit(bestDistance, position, hitEntity)
        }

        return null
    }

    fun step(dimension: Dimension, entity: Entity, dt: Float) {

        entity.calculateForces()

        val forces = Vector3f(entity.ownForces)
        var gravityEffect = if (entity.hasGravity) 1f else 0f

        // if in water, flow upwards, if controller says so
        if (entity.shallFloat != 0f) {
            val inWater = getInWater(dimension, entity)
            gravityEffect -= inWater
        }

        forces.y += gravity * gravityEffect

        val acceleration = forces.div(entity.mass)
        // todo apply friction based on floor
        // todo is on floor?
        val friction = 0.5f
        val velocity = entity.velocity
        velocity.mul(1f - friction * frictionMultiplier * dt)
        velocity.add(acceleration.mul(dt))

        val stepSize = velocity.length() * dt
        val stepCount = (stepSize / maxStep).toInt() + 1

        val stepDt = dt / stepCount
        for (i in 0 until stepCount) {
            if (velocity.lengthSquared() < 1e-7f) break
            tryMove(dimension, entity, velocity, stepDt)
        }

    }

    fun AABBf.volume(): Float {
        return StrictMath.max(0f, maxX - minX) * StrictMath.max(0f, maxY - minY) * StrictMath.max(0f, maxZ - minZ)
    }

    fun AABBd(a: AABBf) = AABBd(
        a.minX.toDouble(), a.minY.toDouble(), a.minZ.toDouble(),
        a.maxX.toDouble(), a.maxY.toDouble(), a.maxZ.toDouble()
    )

    fun getInWater(dimension: Dimension, entity: Entity): Float {
        // check all water blocks, we are colliding with...
        val aabb = entity.AABB
        aabb.union(aabb).volume()
        val blocks = dimension.getBlocks(AABBd(entity.AABB).translate(entity.position), true) ?: return 0f
        val overlap = blocks
            .filter { it.state.block.physical.type == PhysicsType.FLUID }
            .sumByFloat {
                val physical = it.state.block.physical
                val coo = it.coordinates
                val epo = entity.position
                val pos = Vector3f((coo.x - epo.x).toFloat(), (coo.y - epo.y).toFloat(), (coo.z - epo.z).toFloat())
                val overlappingVolume = physical.getShapes(pos)?.sumByFloat { box -> box.union(aabb).volume() } ?: 0f
                overlappingVolume * physical.fluidDensity
            }
        return clamp(overlap / aabb.volume(), 0f, 1f)
    }

    fun tryMove(dimension: Dimension, entity: Entity, velocity: Vector3f, dt: Float) {
        // todo try to move the collision box
        // todo stop at collisions
    }

}