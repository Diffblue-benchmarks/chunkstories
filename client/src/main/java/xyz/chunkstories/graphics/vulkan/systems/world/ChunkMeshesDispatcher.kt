package xyz.chunkstories.graphics.vulkan.systems.world

import org.joml.Vector3f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandBuffer
import xyz.chunkstories.api.util.kotlin.toVec3i
import xyz.chunkstories.api.world.chunk.Chunk
import xyz.chunkstories.api.world.region.Region
import xyz.chunkstories.client.InternalClientOptions
import xyz.chunkstories.graphics.common.FaceCullingMode
import xyz.chunkstories.graphics.common.Primitive
import xyz.chunkstories.graphics.common.shaders.compiler.ShaderCompilationParameters
import xyz.chunkstories.graphics.common.world.ChunkRenderInfo
import xyz.chunkstories.graphics.vulkan.Pipeline
import xyz.chunkstories.graphics.vulkan.VertexInputConfiguration
import xyz.chunkstories.graphics.vulkan.VulkanGraphicsBackend
import xyz.chunkstories.graphics.vulkan.buffers.VulkanBuffer
import xyz.chunkstories.graphics.vulkan.buffers.extractInterfaceBlockField
import xyz.chunkstories.graphics.vulkan.graph.VulkanFrameGraph
import xyz.chunkstories.graphics.vulkan.graph.VulkanPass
import xyz.chunkstories.graphics.vulkan.memory.MemoryUsagePattern
import xyz.chunkstories.graphics.vulkan.swapchain.Frame
import xyz.chunkstories.graphics.vulkan.systems.VulkanDispatchingSystem
import xyz.chunkstories.graphics.vulkan.textures.VulkanSampler
import xyz.chunkstories.graphics.vulkan.textures.voxels.VulkanVoxelTexturesArray
import xyz.chunkstories.world.WorldClientCommon
import xyz.chunkstories.world.chunk.CubicChunk
import xyz.chunkstories.world.storage.RegionImplementation
import java.util.*

class ChunkMeshesDispatcher(backend: VulkanGraphicsBackend) : VulkanDispatchingSystem<ChunkMeshData>(backend) {

    private val meshesVertexInputCfg = VertexInputConfiguration {
        var offset = 0

        attribute {
            binding(0)
            location(program.vertexInputs.find { it.name == "vertexIn" }!!.location)
            format(VK10.VK_FORMAT_R8G8B8A8_UINT)
            offset(offset)
        }
        offset += 4

        attribute {
            binding(0)
            location(program.vertexInputs.find { it.name == "colorIn" }!!.location)
            format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
            offset(offset)
        }
        offset += 4

        attribute {
            binding(0)
            location(program.vertexInputs.find { it.name == "normalIn" }!!.location)
            format(VK10.VK_FORMAT_R8G8B8A8_SNORM)
            offset(offset)
        }
        offset += 4

        attribute {
            binding(0)
            location(program.vertexInputs.find { it.name == "texCoordIn" }!!.location)
            format(VK10.VK_FORMAT_R16G16_UNORM)
            offset(offset)
        }
        offset += 4

        attribute {
            binding(0)
            location(program.vertexInputs.find { it.name == "textureIdIn" }!!.location)
            format(VK10.VK_FORMAT_R32_UINT)
            offset(offset)
        }
        offset += 4

        binding {
            binding(0)
            stride(offset)
            inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX)
        }
    }

    val sampler = VulkanSampler(backend, false)

    inner class Drawer(pass: VulkanPass) : VulkanDispatchingSystem.Drawer(pass) {
        val cubesProgram = backend.shaderFactory.createProgram(if(pass.declaration.name == "water") "water" else "cubes", ShaderCompilationParameters(outputs = pass.declaration.outputs))
        private val meshesPipeline = Pipeline(backend, cubesProgram, pass, meshesVertexInputCfg, Primitive.TRIANGLES, FaceCullingMode.CULL_BACK)

        val chunkInfoID = cubesProgram.glslProgram.instancedInputs.find { it.name == "chunkInfo" }!!
        val structSize = chunkInfoID.struct.size
        val sizeAligned16 = if(structSize % 16 == 0) structSize else (structSize / 16 * 16) + 16

        val maxChunksRendered = 4096
        val ssboBufferSize = (sizeAligned16 * maxChunksRendered).toLong()

        override fun registerDrawingCommands(frame: Frame, commandBuffer: VkCommandBuffer, passContext: VulkanFrameGraph.FrameGraphNode.PassNode) {
            val client = backend.window.client.ingame ?: return

            MemoryStack.stackPush()

            val bindingContext = backend.descriptorMegapool.getBindingContext(meshesPipeline)

            val camera = passContext.context.camera
            val world = client.world as WorldClientCommon

            val camPos = camera.position

            bindingContext.bindUBO("camera", camera)
            bindingContext.bindUBO("world", world.getConditions())

            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, meshesPipeline.handle)

            if(backend.logicalDevice.enableMagicTexturing)
                VK10.vkCmdBindDescriptorSets(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, meshesPipeline.pipelineLayout, 0, MemoryStack.stackLongs(backend.textures.magicTexturing!!.theSet), null)

            val camChunk = camPos.toVec3i()
            camChunk.x /= 32
            camChunk.y /= 32
            camChunk.z /= 32

            val drawDistance = world.client.configuration.getIntValue(InternalClientOptions.viewDistance) / 32
            val drawDistanceH = 6

            val usedData = mutableListOf<ChunkMeshData>()

            //TODO pool those
            val ssboDataTest = VulkanBuffer(backend, ssboBufferSize, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, MemoryUsagePattern.DYNAMIC)

            val ssboStuff = MemoryUtil.memAlloc(ssboDataTest.bufferSize.toInt())
            var instance = 0
            val voxelTexturesArray = client.content.voxels().textures() as VulkanVoxelTexturesArray
            bindingContext.bindTextureAndSampler("albedoTextures", voxelTexturesArray.albedoOnionTexture, sampler)
            bindingContext.bindSSBO("chunkInfo", ssboDataTest)
            bindingContext.preDraw(commandBuffer)

            fun renderChunk(chunk: CubicChunk) {

                if (chunk.meshData is ChunkVkMeshProperty) {
                    val block = (chunk.meshData as ChunkVkMeshProperty).get()
                    if (block != null)
                        usedData.add(block)

                    val section = block?.sections?.get(pass.declaration.name)

                    if (section != null) {
                        VK10.vkCmdBindVertexBuffers(commandBuffer, 0, MemoryStack.stackLongs(section.buffer.handle), MemoryStack.stackLongs(0))

                        ssboStuff.position(instance * sizeAligned16)
                        val chunkRenderInfo = ChunkRenderInfo().apply {
                            chunkX = chunk.chunkX
                            chunkY = chunk.chunkY
                            chunkZ = chunk.chunkZ
                        }

                        for (field in chunkInfoID.struct.fields) {
                            ssboStuff.position(instance * sizeAligned16 + field.offset)
                            extractInterfaceBlockField(field, ssboStuff, chunkRenderInfo)
                        }

                        VK10.vkCmdDraw(commandBuffer, section.count, 1, 0, instance++)

                        frame.stats.totalVerticesDrawn += section.count
                        frame.stats.totalDrawcalls++
                    }
                } else {
                    // This avoids the condition where the meshData is created after the chunk is destroyed
                    chunk.chunkDestructionSemaphore.acquireUninterruptibly()
                    if (!chunk.isDestroyed)
                        chunk.meshData = ChunkVkMeshProperty(backend, chunk)
                    chunk.chunkDestructionSemaphore.release()
                }
            }

            val boxCenter = Vector3f(0f)
            val boxSize = Vector3f(32f, 32f, 32f)
            val boxSize2 = Vector3f(256f, 256f, 256f)

            val sortedChunks = ArrayList<CubicChunk>()

            val visibleRegions = arrayOfNulls<RegionImplementation>(1024)
            var visibleRegionsCount = 0

            var rc = 0
            for (region in world.allLoadedRegions) {
                boxCenter.x = region.regionX * 256.0f + 128.0f
                boxCenter.y = region.regionY * 256.0f + 128.0f
                boxCenter.z = region.regionZ * 256.0f + 128.0f

                rc++

                if(camera.frustrum.isBoxInFrustrum(boxCenter, boxSize2)) {
                    visibleRegions[visibleRegionsCount++] = region as RegionImplementation
                }
            }

            Arrays.sort(visibleRegions, 0, visibleRegionsCount) { a, b ->
                fun distSquared(r: Region) : Float {
                    val rcx = r.regionX * 256.0f + 128.0f
                    val rcy = r.regionY * 256.0f + 128.0f
                    val rcz = r.regionZ * 256.0f + 128.0f

                    val dx = camPos.x() - rcx
                    val dy = camPos.y() - rcy
                    val dz = camPos.z() - rcz

                    return dx * dx + dy * dy + dz * dz
                }

                (distSquared(a!!) - distSquared(b!!)).toInt()
            }

            val visibleRegionChunks = arrayOfNulls<CubicChunk>(8 * 8 * 8)
            var visibleRegionChunksCount : Int

            val visibilityRangeX = (camChunk.x - drawDistance)..(camChunk.x + drawDistance)
            val visibilityRangeY = (camChunk.y - drawDistanceH)..(camChunk.y + drawDistanceH)
            val visibilityRangeZ = (camChunk.z - drawDistance)..(camChunk.z + drawDistance)

            for(i in 0 until visibleRegionsCount) {
                val region = visibleRegions[i]!!

                visibleRegionChunksCount = 0
                for (chunk in region.loadedChunks) {
                    boxCenter.x = chunk.chunkX * 32.0f + 16.0f
                    boxCenter.y = chunk.chunkY * 32.0f + 16.0f
                    boxCenter.z = chunk.chunkZ * 32.0f + 16.0f

                    if(!chunk.isAirChunk) {
                        if(chunk.chunkX in visibilityRangeX && chunk.chunkY in visibilityRangeY && chunk.chunkZ in visibilityRangeZ) {

                            if (camera.frustrum.isBoxInFrustrum(boxCenter, boxSize)) {
                                visibleRegionChunks[visibleRegionChunksCount++] = chunk
                                //sortedChunks.add(chunk)
                            }
                        }
                    }
                }

                Arrays.sort(visibleRegionChunks, 0, visibleRegionChunksCount) { a, b ->
                    fun distSquared(c: Chunk) : Float {
                        val ccx = c.chunkX * 32.0f + 16.0f
                        val ccy = c.chunkY * 32.0f + 16.0f
                        val ccz = c.chunkZ * 32.0f + 16.0f

                        val dx = camPos.x() - ccx
                        val dy = camPos.y() - ccy
                        val dz = camPos.z() - ccz

                        return dx * dx + dy * dy + dz * dz
                    }

                    (distSquared(a!!) - distSquared(b!!)).toInt()
                }

                for(j in 0 until visibleRegionChunksCount) {
                    renderChunk(visibleRegionChunks[j]!!)
                }
            }

            ssboStuff.flip()
            ssboDataTest.upload(ssboStuff)
            MemoryUtil.memFree(ssboStuff)

            frame.recyclingTasks.add {
                usedData.forEach(ChunkMeshData::release)
                bindingContext.recycle()
                ssboDataTest.cleanup()//TODO recycle don't destroy!
            }

            MemoryStack.stackPop()
        }

        override fun cleanup() {
            cubesProgram.cleanup()
        }
    }

    override fun createDrawerForPass(pass: VulkanPass): Drawer  = Drawer(pass)

    override fun cleanup() {
        sampler.cleanup()
    }

}