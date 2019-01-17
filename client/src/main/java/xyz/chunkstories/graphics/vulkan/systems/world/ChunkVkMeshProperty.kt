package xyz.chunkstories.graphics.vulkan.systems.world

import xyz.chunkstories.api.world.chunk.Chunk
import xyz.chunkstories.graphics.vulkan.VulkanGraphicsBackend
import xyz.chunkstories.graphics.vulkan.buffers.VulkanVertexBuffer
import xyz.chunkstories.graphics.vulkan.resources.DescriptorSetsMegapool
import xyz.chunkstories.graphics.vulkan.textures.VirtualTexturing
import xyz.chunkstories.world.chunk.CubicChunk
import xyz.chunkstories.world.chunk.deriveddata.AutoRebuildingProperty

class ChunkVkMeshProperty(val backend: VulkanGraphicsBackend, val chunk: CubicChunk) : AutoRebuildingProperty(chunk.world.gameContext, true), Chunk.ChunkMesh {
    val property = RefCountedProperty<ChunkVulkanMeshData>()

    init {
        requestUpdate()
    }

    fun get(): ChunkVulkanMeshData? {
        try {
            lock.lock()
            val value = property.get()
            if (value == null && task == null)
                this.requestUpdate()
            return value
        } finally {
            lock.unlock()
        }
    }

    fun acceptNewData(vertexBuffer: VulkanVertexBuffer?, virtualTexturingContext: VirtualTexturing.VirtualTexturingContext?, count: Int) {
        val data = ChunkVulkanMeshData(vertexBuffer, virtualTexturingContext, count, property)
        property.set(data)
    }

    inner class ChunkVulkanMeshData(
            val vertexBuffer: VulkanVertexBuffer?,
            val virtualTexturingContext: VirtualTexturing.VirtualTexturingContext?,
            val count: Int, property: RefCountedProperty<*>
    ) : RefCountedRecyclable(property) {

        var perChunkBindings: DescriptorSetsMegapool.ShaderBindingContext? = null

        override fun cleanup() {
            perChunkBindings?.recycle()
            vertexBuffer?.cleanup()
            virtualTexturingContext?.returnToPool()
        }
    }

    override fun createTask(updatesToConsider: Int): UpdateTask = TaskCreateChunkMesh(backend, chunk, this, updatesToConsider)

    override fun cleanup() {
        //task?.tryCancel()
        property.data?.release()
    }
}