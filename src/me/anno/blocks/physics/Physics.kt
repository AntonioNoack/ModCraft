package me.anno.blocks.physics

import me.anno.blocks.block.BlockState
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.physical.PhysicsType
import me.anno.blocks.entity.Entity
import me.anno.blocks.utils.floorToInt
import me.anno.blocks.world.Dimension
import me.anno.utils.Maths.clamp
import me.anno.utils.Maths.fract
import me.anno.utils.types.Lists.sumByFloat
import org.joml.*
import kotlin.math.floor

object Physics {

    val gravity = -9.81f

    val maxStep = 0.2f // to prevent collisions

    val frictionMultiplier = 5f

    fun raytrace(
        dimension: Dimension,
        origin: Vector3d,
        direction: Vector3f,
        maxDistance: Double,
        canHitFluids: Boolean,
        canHitEntities: Boolean,
        finished: Boolean
    ): RaycastHit? {

        direction.normalize()

        var bestDistance = maxDistance
        var hitEntity: Entity? = null
        if (canHitEntities) {
            // todo get all chunks in distance... or sorted?...

        }

        fun set(dst: Vector3i, src: Vector3d) {
            dst.set(floor(src.x).toInt(), floor(src.y).toInt(), floor(src.z).toInt())
        }

        val coordinates = Vector3i()
        set(coordinates, origin)

        var distance = 0.0

        val positionI = Vector3i()
        val position = Vector3d()

        val previousPosition = origin.floorToInt()
        var previousBlock = Air

        fun checkHitExact(c: Vector3i, blockState: BlockState): RaycastHit {
            // todo check collision with collision mesh???...
            // println("hit $blockState at $c")
            position.set(direction).mul(distance).add(origin)
            return BlockHit(distance, position, previousPosition, previousBlock, positionI, blockState)
        }

        fun checkHit(c: Vector3i): RaycastHit? {
            val block = dimension.getBlock(c.x, c.y, c.z, finished)
            val result = if (block != Air) {
                val visuals = block.block.visuals
                val physics = block.block.physical
                when (physics.type) {
                    PhysicsType.SOLID -> {
                        checkHitExact(c, block)
                    }
                    PhysicsType.FLUID -> {
                        if (canHitFluids) {
                            checkHitExact(c, block)
                        } else null
                    }
                    PhysicsType.AIR -> {
                        // nothing
                        null
                    }
                    PhysicsType.FRICTION -> {
                        checkHitExact(c, block)
                    }
                }
            } else null
            if (result == null) previousBlock = block
            return result
        }

        checkHit(coordinates)

        val minY = if (dimension.hasBlocksBelowZero) Int.MIN_VALUE else 0

        val lastHit = Vector3i(Int.MIN_VALUE)

        // acceptable inaccuracy
        val epsilon = 1e-4f
        val maxSteps = 1000
        var steps = 0
        while (steps++ < maxSteps && distance < bestDistance) {

            // increase the coordinates until we read the next block
            position.set(direction).mul(distance).add(origin)

            positionI.x = floor(position.x).toInt()
            positionI.y = floor(position.y).toInt()
            positionI.z = floor(position.z).toInt()

            if (positionI.y >= minY && lastHit != positionI) {
                lastHit.set(positionI)
                val hit = checkHit(positionI)
                if (hit != null) return hit
                else previousPosition.set(positionI)
            }

            var minLength = Double.POSITIVE_INFINITY

            // extend the line to the world
            if (position.y < minY) {
                minLength = StrictMath.min(minLength, ((minY + epsilon) - position.y) / direction.y)
                if (minLength.isFinite() && minLength > 0f) {
                    distance += minLength + epsilon
                    continue
                }
            }

            // we need to go forward...
            val fractX = fract(origin.x)
            val fractY = fract(origin.y)
            val fractZ = fract(origin.z)

            val minStep = StrictMath.max(
                StrictMath.min(
                    StrictMath.min(
                        (if (direction.x > 0f) 1f - fractX else -fractX) / direction.x,
                        (if (direction.y > 0f) 1f - fractY else -fractY) / direction.y
                    ),
                    (if (direction.z > 0f) 1f - fractZ else -fractZ) / direction.z
                ), 0.0
            ) + epsilon // epsilon, so we land in the next block, not this one again

            distance += minStep

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