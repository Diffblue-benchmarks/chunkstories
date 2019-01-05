//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.world.generator

import io.xol.chunkstories.api.workers.Task
import io.xol.chunkstories.api.workers.TaskExecutor
import io.xol.chunkstories.api.world.World
import io.xol.chunkstories.api.world.WorldUser
import io.xol.chunkstories.api.world.heightmap.Heightmap
import io.xol.chunkstories.api.world.region.Region
import io.xol.chunkstories.world.WorldImplementation
import io.xol.chunkstories.world.heightmap.HeightmapImplementation
import io.xol.chunkstories.world.storage.RegionImplementation

/**
 * Generates a world 'slice' (the voxel getCell data represented by a heightmap) using smaller tasks
 */
class TaskGenerateWorldSlice(private val world: WorldImplementation, val heightmap: HeightmapImplementation, private val directionX: Int, private val directionZ: Int) : Task(), WorldUser {
    private lateinit var regions: Array<RegionImplementation?>

    private var wave = 0
    private var tasks: Array<Task?>? = null

    private var initYet = false

    private val isWorkDone: Boolean
        get() {
            if(tasks == null)
                return true

            for (i in 0..7) {
                if (tasks!![i]!!.state == Task.State.CANCELLED)
                    throw RuntimeException("oh boi no")
                if (tasks!![i]!!.state != Task.State.DONE)
                    return false
            }

            return true
        }

    override fun task(taskExecutor: TaskExecutor): Boolean {
        if (!initYet) {
            val heightInRegions = world.worldInfo.size.heightInChunks / 8
            this.regions = arrayOfNulls(heightInRegions)
            for (ry in 0 until heightInRegions) {
                regions[ry] = world.acquireRegion(this, heightmap.regionX, ry, heightmap.regionZ)
            }
            initYet = true
        }

        if (heightmap.state !is Heightmap.State.Generating) {
            throw RuntimeException("We only generate world slices when the heightmap data is in the 'Generating' state ! (state=" + heightmap.state + ")")
        }

        if (wave == 8) {
            if (!isWorkDone)
            // not QUITE done yet!
                return false

            heightmap.recomputeMetadata()
            heightmap.eventGenerationFinished()

            for (region in regions!!) {
                region!!.unregisterUser(this)
                region!!.eventGeneratingFinishes()
            }

            return true
        }

        if (isWorkDone) {
            var directed_relative_chunkX: Int
            var directed_relative_chunkZ: Int

            tasks = arrayOfNulls(8)
            for (relative_chunkZ in 0..7) {
                if (directionX < 0) {
                    directed_relative_chunkX = 7 - wave
                } else {
                    directed_relative_chunkX = wave
                }

                if (directionZ < 0) {
                    directed_relative_chunkZ = 7 - relative_chunkZ
                } else {
                    directed_relative_chunkZ = relative_chunkZ
                }

                val task = TaskGenerateWorldThinSlice(world,
                        heightmap.regionX * 8 + directed_relative_chunkX,
                        heightmap.regionZ * 8 + directed_relative_chunkZ, heightmap)
                world.gameContext.tasks.scheduleTask(task)
                tasks!![relative_chunkZ] = task
            }
            wave++
        }

        return false
    }

}