package xyz.chunkstories.graphics.vulkan.systems

import org.lwjgl.vulkan.VkCommandBuffer
import xyz.chunkstories.api.graphics.representation.Representation
import xyz.chunkstories.api.graphics.systems.dispatching.DispatchingSystem
import xyz.chunkstories.graphics.vulkan.VulkanGraphicsBackend
import xyz.chunkstories.graphics.vulkan.graph.VulkanFrameGraph
import xyz.chunkstories.graphics.vulkan.graph.VulkanPass
import xyz.chunkstories.graphics.common.Cleanable
import xyz.chunkstories.graphics.vulkan.swapchain.Frame

abstract class VulkanDispatchingSystem<R: Representation>(val backend: VulkanGraphicsBackend) : /*DispatchingSystem<T>, */Cleanable {

    abstract val representationName: String

    /** The drawer's job is to draw "things". The term is deliberatly loose: the drawer might directly work with the representations
     * the parent system is fed, or it might operate on a different type, perhaps a component of the bigger Representation like it's done
     * for ModelInstances or ChunkRepresentations*/
    abstract class Drawer<T>(val pass: VulkanPass) : Cleanable, DispatchingSystem {
        abstract val system: VulkanDispatchingSystem<*>

        override val representationName: String
            get() = system.representationName

        abstract fun registerDrawingCommands(frame : Frame, context: VulkanFrameGraph.FrameGraphNode.PassNode, commandBuffer: VkCommandBuffer, work: Sequence<T>)

        open fun registerAdditionalRenderTasks(passContext: VulkanFrameGraph.FrameGraphNode.PassNode) {
            // Does nothing by default
        }
    }

    abstract fun createDrawerForPass(pass: VulkanPass, drawerInitCode: Drawer<*>.() -> Unit) : Drawer<*>

    val drawersInstances = mutableListOf<Drawer<*>>()

    //abstract fun <T> sort(representation: R, drawers: Array<Drawer<T>>, outputs: List<MutableList<T>>)
    abstract fun sort(representation: R, drawers: Array<Drawer<*>>, outputs: List<MutableList<Any>>)
}