package xyz.chunkstories.graphics.vulkan.world

import org.joml.Vector4d
import xyz.chunkstories.api.entity.traits.serializable.TraitControllable
import xyz.chunkstories.api.graphics.TextureFormat
import xyz.chunkstories.api.graphics.rendergraph.PassOutput
import xyz.chunkstories.api.graphics.rendergraph.RenderGraphDeclarationScript
import xyz.chunkstories.api.graphics.structs.Camera
import xyz.chunkstories.api.graphics.systems.dispatching.ChunksRenderer
import xyz.chunkstories.api.graphics.systems.dispatching.ModelsRenderer
import xyz.chunkstories.api.graphics.systems.dispatching.SpritesRenderer
import xyz.chunkstories.api.gui.GuiDrawer
import xyz.chunkstories.graphics.common.WorldRenderer
import xyz.chunkstories.graphics.vulkan.VulkanBackendOptions
import xyz.chunkstories.graphics.vulkan.VulkanGraphicsBackend
import xyz.chunkstories.graphics.vulkan.systems.SkyDrawer
import xyz.chunkstories.graphics.vulkan.systems.Vulkan3DVoxelRaytracer
import xyz.chunkstories.graphics.vulkan.systems.VulkanFullscreenQuadDrawer
import xyz.chunkstories.graphics.vulkan.systems.world.ChunkRepresentationsProvider
import xyz.chunkstories.graphics.vulkan.systems.world.getConditions
import xyz.chunkstories.graphics.vulkan.world.entities.EntitiesRepresentationsProvider
import xyz.chunkstories.world.WorldClientCommon

class VulkanWorldRenderer(val backend: VulkanGraphicsBackend, world: WorldClientCommon) : WorldRenderer(world) {
    val chunksRepresentationsProvider = ChunkRepresentationsProvider(backend, world)
    val entitiesProvider = EntitiesRepresentationsProvider(world)

    val client = world.gameContext

    init {
        backend.graphicsEngine.loadRenderGraph(createInstructions())

        backend.graphicsEngine.representationsProviders.registerProvider(chunksRepresentationsProvider)
        backend.graphicsEngine.representationsProviders.registerProvider(entitiesProvider)
    }

    override fun cleanup() {
        backend.graphicsEngine.representationsProviders.unregisterProvider(chunksRepresentationsProvider)
        backend.graphicsEngine.representationsProviders.unregisterProvider(entitiesProvider)
        //chunksRepresentationsProvider.cleanup()
    }

    fun createInstructions(): RenderGraphDeclarationScript = {
        renderTask {
            name = "main"

            finalPassName = "gui"

            renderBuffers {
                renderBuffer {
                    name = "depthBuffer"

                    format = TextureFormat.DEPTH_32
                    size = viewportSize
                }

                renderBuffer {
                    name = "colorBuffer"

                    format = TextureFormat.RGBA_8
                    size = viewportSize
                }

                renderBuffer {
                    name = "normalBuffer"

                    format = TextureFormat.RGBA_8
                    size = viewportSize
                }

                renderBuffer {
                    name = "shadedBuffer"

                    format = TextureFormat.RGB_HDR
                    size = viewportSize
                }

                renderBuffer {
                    name = "finalBuffer"

                    format = TextureFormat.RGBA_8
                    size = viewportSize
                }

                val shadowCascades = client.configuration.getIntValue(VulkanBackendOptions.shadowCascades)
                val shadowResolution = client.configuration.getIntValue(VulkanBackendOptions.shadowMapSize)
                for(i in 0 until shadowCascades) {
                    renderBuffer {
                        name = "shadowBuffer$i"

                        format = TextureFormat.DEPTH_32
                        size = shadowResolution by shadowResolution
                    }
                }
            }

            passes {
                pass {
                    name = "sky"

                    draws {
                        system(SkyDrawer::class)
                    }

                    outputs {
                        output {
                            name = "shadedBuffer"
                            clear = true
                            clearColor = Vector4d(0.0, 0.5, 1.0, 1.0)
                        }
                    }
                }

                pass {
                    name = "opaque"

                    dependsOn("sky")

                    draws {
                        system(ChunksRenderer::class) {
                            shader = "cubes"
                            materialTag = "opaque"
                        }
                        system(ModelsRenderer::class) {
                            shader = "models"
                            materialTag = "opaque"
                            supportsAnimations = true
                        }
                        system(SpritesRenderer::class) {
                            shader = "sprites"
                            materialTag = "opaque"
                        }
                    }

                    outputs {
                        output {
                            name = "colorBuffer"
                            clear = true
                            clearColor = Vector4d(0.0, 0.0, 0.0, 0.0)
                            blending = PassOutput.BlendMode.OVERWRITE
                        }

                        output {
                            name = "normalBuffer"
                            clear = true
                            clearColor = Vector4d(0.0, 0.0, 0.0, 0.0)
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }

                    depth {
                        enabled = true
                        depthBuffer = renderBuffer("depthBuffer")
                        clear = true
                    }
                }

                pass {
                    name = "deferredShading"

                    dependsOn("opaque")

                    inputs {
                        imageInput {
                            name = "colorBuffer"
                            source = renderBuffer("colorBuffer")
                        }

                        imageInput {
                            name = "normalBuffer"
                            source = renderBuffer("normalBuffer")
                        }

                        imageInput {
                            name = "depthBuffer"
                            source = renderBuffer("depthBuffer")
                        }
                    }

                    draws {

                        if (client.configuration.getBooleanValue(VulkanBackendOptions.raytracedGI)) {
                            system(Vulkan3DVoxelRaytracer::class)
                        } else {
                            system(VulkanFullscreenQuadDrawer::class) {
                                shaderBindings {
                                    val camera = client.player.controlledEntity?.traits?.get(TraitControllable::class)?.camera
                                            ?: Camera()
                                    it.bindUBO("camera", camera)
                                    it.bindUBO("world", client.world.getConditions())
                                }

                                //TODO hacky api, plz fix
                                doShadowMap = true
                            }
                        }
                    }

                    outputs {
                        output {
                            name = "shadedBuffer"
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }
                }

                pass {
                    name = "forward"

                    dependsOn("deferredShading")

                    draws {
                        system(ChunksRenderer::class) {
                            shader = "water"
                            materialTag = "water"
                        }
                    }

                    outputs {
                        output {
                            name = "shadedBuffer"
                            blending = PassOutput.BlendMode.MIX
                        }
                    }

                    depth {
                        enabled = true
                        depthBuffer = renderBuffer("depthBuffer")
                    }
                }

                pass {
                    name = "postprocess"

                    dependsOn("deferredShading", "forward")

                    inputs {
                        imageInput {
                            name = "shadedBuffer"
                            source = renderBuffer("shadedBuffer")
                        }
                    }

                    draws {
                        fullscreenQuad()
                    }

                    outputs {
                        output {
                            name = "finalBuffer"
                            blending = PassOutput.BlendMode.OVERWRITE
                        }
                    }
                }

                pass {
                    name = "gui"

                    dependsOn("postprocess")

                    draws {
                        system(GuiDrawer::class)
                    }

                    outputs {
                        output {
                            name = "finalBuffer"

                            //clear = true
                            blending = PassOutput.BlendMode.MIX
                        }
                    }
                }
            }
        }

        renderTask {
            name = "sunShadow"

            finalPassName = "cubes"

            renderBuffers {
                /*renderBuffer {
                    name = "shadowBuffer"

                    format = DEPTH_32
                    size = 1024 by 1024
                }*/
            }

            taskInputs {
                input {
                    name = "shadowBuffer"
                    format = TextureFormat.DEPTH_32
                }
            }

            passes {
                pass {
                    name = "cubes"

                    draws {
                        system(ChunksRenderer::class) {
                            shader = "cubes"
                            materialTag = "opaque"
                        }
                        system(ModelsRenderer::class) {
                            shader = "models"
                            materialTag = "opaque"
                            supportsAnimations = true
                        }
                    }

                    outputs {

                    }

                    depth {
                        enabled = true
                        depthBuffer = taskInput("shadowBuffer")
                        clear = true
                    }
                }
            }
        }
    }
}