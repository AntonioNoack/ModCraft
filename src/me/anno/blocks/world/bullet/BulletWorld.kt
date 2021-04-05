package me.anno.blocks.world.bullet

import com.bulletphysics.collision.broadphase.DbvtBroadphase
import com.bulletphysics.collision.dispatch.CollisionDispatcher
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration
import com.bulletphysics.collision.shapes.BoxShape
import com.bulletphysics.collision.shapes.CollisionShape
import com.bulletphysics.collision.shapes.CompoundShape
import com.bulletphysics.collision.shapes.SphereShape
import com.bulletphysics.dynamics.DiscreteDynamicsWorld
import com.bulletphysics.dynamics.RigidBody
import com.bulletphysics.dynamics.RigidBodyConstructionInfo
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver
import com.bulletphysics.linearmath.DefaultMotionState
import com.bulletphysics.linearmath.Transform
import me.anno.blocks.block.base.Air
import me.anno.blocks.block.physical.BlockPhysical.Companion.boxShapeList
import me.anno.blocks.chunk.Chunk
import me.anno.blocks.chunk.Chunk.Companion.CS
import me.anno.blocks.chunk.Chunk.Companion.CS3
import me.anno.blocks.chunk.Chunk.Companion.getVec3d
import me.anno.blocks.chunk.Chunk.Companion.getVec3f
import me.anno.blocks.rendering.RenderData
import me.anno.blocks.world.bullet.ShapeMerger.getShape
import me.anno.blocks.world.bullet.ShapeMerger.mergeX
import me.anno.blocks.world.bullet.ShapeMerger.mergeY
import me.anno.blocks.world.bullet.ShapeMerger.mergeZ
import me.anno.blocks.world.bullet.ShapeMerger.removeInside
import me.anno.blocks.world.bullet.ShapeMerger.shapeToVec
import me.anno.gpu.GFX
import me.anno.objects.distributions.CuboidHullDistribution
import me.anno.objects.distributions.SphereHullDistribution
import me.anno.utils.Maths.next
import me.anno.utils.types.Vectors.minus
import me.anno.utils.types.Vectors.plus
import org.joml.*
import javax.vecmath.Vector3f
import kotlin.math.abs

// https://github.com/bulletphysics/bullet3/blob/master/examples/HelloWorld/HelloWorld.cpp
class BulletWorld {

    val collisionConfig = DefaultCollisionConfiguration()
    val dispatcher = CollisionDispatcher(collisionConfig)

    val overlappingPairCache = DbvtBroadphase()
    val solver = SequentialImpulseConstraintSolver()
    val world = DiscreteDynamicsWorld(dispatcher, overlappingPairCache, solver, collisionConfig)

    init {
        world.setGravity(Vector3f(0f, -10f, 0f))
    }

    init {
        addObject(BoxShape(Vector3f(50f, 50f, 50f)), Vector3f(0f, -56f, 0f), 0f)
        for (i in 0 until 5) addObject(SphereShape(1f), Vector3f(0f, i * 3f + 20f, 0f), 1f)
    }

    var ctr = 0

    fun addChunk(chunk: Chunk) {
        // if (ctr++ > 0) return
        val chunkPosition = Vector3d(chunk.center).add(0.5, 0.5, 0.5) // center -> left edge
        val compoundShape = CompoundShape()
        // val ids = chunk.blockIds
        val v111 = getShape(1, 1, 1)
        val simplestShapes = IntArray(CS3)
        val ids = chunk.getAllBlocks()
        for (index in ids.indices) {
            val id = ids[index]
            if (id != Air) {
                val block = id.block
                val shapes = block.physical.collisionShapes
                if (shapes === boxShapeList) {
                    simplestShapes[index] = v111
                } else if (shapes != null && shapes.isNotEmpty()) {
                    val blockPosition = getVec3d(index) - Vector3d(CS * 0.5)
                    for ((shape, deltaPosition, rotation) in shapes) {
                        val shapePosition = vec3f(blockPosition) + deltaPosition
                        val transform = Transform()
                        transform.setIdentity()
                        if (rotation.x != 0f || rotation.y != 0f || rotation.z != 0f) {
                            val mat = transform.basis
                            // correct order?
                            mat.rotY(rotation.y)
                            mat.rotX(rotation.x)
                            mat.rotZ(rotation.z)
                        }
                        transform.origin.set(shapePosition.x, shapePosition.y, shapePosition.z)
                        compoundShape.addChildShape(transform, shape)
                    }
                }
            }
        }
        simplifyShapes(simplestShapes)
        for (index in simplestShapes.indices) {
            val blockSizeI = simplestShapes[index]
            if (blockSizeI != 0) {
                val blockSize = shapeToVec(blockSizeI)
                val position = getVec3f(index)
                val transform = Transform()
                transform.setIdentity()
                transform.origin.set(
                    position.x - 16f + (blockSize.x - 1) * 0.5f,
                    position.y - 16f + (blockSize.y - 1) * 0.5f,
                    position.z - 16f + (blockSize.z - 1) * 0.5f
                )
                val default = { BoxShape(Vector3f(blockSize.x * 0.5f, blockSize.y * 0.5f, blockSize.z * 0.5f)) }
                val jointShape =
                    if (blockSize.x + blockSize.y + blockSize.z < 16) shapes.getOrPut(blockSize, default) else default()
                compoundShape.addChildShape(transform, jointShape)
            }
        }
        val body = addObject(compoundShape, vec3f2(chunkPosition), 0f)
        chunk.rigidBody = body
    }

    val shapes = HashMap<Vector3i, BoxShape>()

    fun simplifyShapes(shapes: IntArray) {
        // todo remove all superfluous blocks??..
        removeInside(shapes)
        mergeZ(shapes)
        mergeX(shapes)
        mergeY(shapes)
        // todo merge all y
        // todo merge all x
    }

    fun vec3f(v: Vector3d) = org.joml.Vector3f(v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
    fun vec3f2(v: Vector3d) = Vector3f(v.x.toFloat(), v.y.toFloat(), v.z.toFloat())

    fun addObject(shape: CollisionShape, position: Vector3f, mass: Float): RigidBody {

        val transform = Transform()
        transform.setIdentity()
        transform.origin.set(position)

        val isDynamic = mass != 0f
        val localInertia = Vector3f(0f, 0f, 0f)
        if (isDynamic) {
            shape.calculateLocalInertia(mass, localInertia)
        }

        val motionState = DefaultMotionState(transform)
        val rbInfo = RigidBodyConstructionInfo(mass, motionState, shape, localInertia)
        val body = RigidBody(rbInfo)
        world.addRigidBody(body)
        return body

    }

    fun removeObject(body: RigidBody?) {
        body ?: return
        world.removeRigidBody(body)
    }

    fun step(dt: Float) {
        try {
            world.stepSimulation(dt, 10)
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    fun printState() {
        val numberOfObjects = world.numCollisionObjects
        val array = world.collisionObjectArray
        val transform = Transform()
        for (i in 0 until numberOfObjects) {
            val obj = array[i]
            val body = RigidBody.upcast(obj) ?: null
            val motionState = body?.motionState
            if (motionState != null) {
                println(motionState.getWorldTransform(transform))
            } else {
                println(obj.getWorldTransform(transform))
            }
        }
    }

    var lastTime = GFX.gameTime

    fun update() {
        val time = GFX.gameTime
        val delta = abs(time - lastTime)
        if (delta > 0L) {
            step(delta * 1e-9f)
        }
        lastTime = time
    }

    val dot = me.anno.objects.Transform().apply { scale.set(org.joml.Vector3f(10f)) }
    val cube = CuboidHullDistribution()
    val sphere = SphereHullDistribution()

    fun onDraw(stack: Matrix4fArrayList, data: RenderData) {

        update()

        val transform = Transform()
        val tmp = Vector3f()

        fun draw(shape: CollisionShape?, transform: Transform) {
            val origin = transform.origin
            stack.next {
                stack.translate(origin.x, origin.y, origin.z)
                val mat = transform.basis
                stack.mul(
                    Matrix4f(
                        mat.m00, mat.m10, mat.m20, 0f,
                        mat.m01, mat.m11, mat.m21, 0f,
                        mat.m02, mat.m12, mat.m22, 0f,
                        0f, 0f, 0f, 1f
                    )
                )
                when (shape) {
                    is CompoundShape -> {
                        val childTransform = Transform()
                        for (j in 0 until shape.numChildShapes) {
                            shape.getChildTransform(j, childTransform)
                            val childShape = shape.getChildShape(j)
                            draw(childShape, childTransform)
                        }
                    }
                    is BoxShape -> {
                        stack.next {
                            shape.getHalfExtentsWithMargin(tmp)
                            stack.scale(tmp.x, tmp.y, tmp.z)
                            cube.draw(stack, color)
                        }
                    }
                    is SphereShape -> {
                        stack.scale(shape.radius)
                        sphere.draw(stack, color)
                    }
                    else -> {
                        dot.draw(stack, 1.0, color)
                    }
                }
            }
        }

        val numberOfObjects = world.numCollisionObjects
        val array = world.collisionObjectArray
        for (i in 0 until numberOfObjects) {
            val obj = array[i]
            val body = RigidBody.upcast(obj) ?: null
            val shape = body?.collisionShape
            val motionState = body?.motionState
            if (motionState != null) {
                motionState.getWorldTransform(transform)
            } else {
                obj.getWorldTransform(transform)
            }
            draw(shape, transform)
        }
    }

    val color = Vector4f(0f, 1f, 1f, 1f)


}