package xyz.chunkstories.graphics.vulkan.graph

import org.joml.Vector2i
import xyz.chunkstories.api.graphics.TextureFormat
import xyz.chunkstories.graphics.vulkan.VulkanGraphicsBackend
import xyz.chunkstories.graphics.common.Cleanable
import xyz.chunkstories.graphics.vulkan.textures.VulkanTexture2D
import org.lwjgl.system.MemoryStack.stackPop
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.slf4j.LoggerFactory
import xyz.chunkstories.api.graphics.rendergraph.RenderBufferDeclaration
import xyz.chunkstories.api.graphics.rendergraph.RenderBufferSize
import kotlin.math.roundToInt

class VulkanRenderBuffer(val backend: VulkanGraphicsBackend, val declaration: RenderBufferDeclaration) : Cleanable {
    lateinit var texture: VulkanTexture2D
    var size: Vector2i

    /** Set late by the RenderGraph */
    lateinit var layoutPerStage: Map<VulkanPass, Int>

    val attachementType: AttachementType

    fun AttachementType.usageBits() = when(this) {
            AttachementType.DEPTH -> VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
            AttachementType.COLOR -> VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
        } or VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_SRC_BIT // In all cases it'll be sampled and transferred !

    init {
        attachementType = when(declaration.format) {
            TextureFormat.DEPTH_24, TextureFormat.DEPTH_32 -> AttachementType.DEPTH
            else -> AttachementType.COLOR
        }

        size = declaration.size.actual

        if(declaration.name != "_swapchain")
            texture = VulkanTexture2D(backend, declaration.format, size.x, size.y, attachementType.usageBits())
    }

    val RenderBufferSize.actual: Vector2i
        get() = when(this) {
            is RenderBufferSize.FixedSize -> Vector2i(width, height)
            is RenderBufferSize.ViewportRelativeSize -> Vector2i((backend.window.width * scaleHorizontal).roundToInt(), (backend.window.height * scaleVertical).roundToInt())
        }

    fun resize() {
        val newSize = declaration.size.actual
        if(newSize != size) {
            logger.debug("Resizing render buffer ${declaration.name}")
            size = newSize
            texture.cleanup()
            texture = VulkanTexture2D(backend, declaration.format, size.x, size.y, attachementType.usageBits())
        }
    }
    fun transitionUsage(commandBuffer: VkCommandBuffer, previousUsage: UsageType, newUsage: UsageType) {
        stackPush()
        val imageBarrier = VkImageMemoryBarrier.callocStack(1).sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER).apply {
            oldLayout(getLayoutForStateAndType(previousUsage, attachementType))
            newLayout(getLayoutForStateAndType(newUsage, attachementType))

            srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            image(texture.imageHandle)

            subresourceRange().apply {
                aspectMask(attachementType.aspectMask())
                baseMipLevel(0)
                levelCount(1)
                baseArrayLayer(0)
                layerCount(1)
            }

            srcAccessMask(previousUsage.accessMask())
            dstAccessMask(newUsage.accessMask())
        }

        val srcStageMask = when(previousUsage) {
            UsageType.INPUT -> VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            UsageType.OUTPUT -> VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            UsageType.NONE -> VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
        }

        val dstStageMask = when(newUsage) {
            UsageType.INPUT -> VK_PIPELINE_STAGE_VERTEX_SHADER_BIT
            UsageType.OUTPUT -> VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            UsageType.NONE -> VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
        }

        vkCmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, 0, null, null, imageBarrier)
        stackPop()
    }

    override fun cleanup() {
        texture.cleanup()
    }

    override fun toString(): String {
        return "VulkanRenderBuffer(name=${declaration.name}, format=${declaration.format}, size=$size)"
    }

    companion object {
        val logger = LoggerFactory.getLogger("client.vulkan")
    }
}


