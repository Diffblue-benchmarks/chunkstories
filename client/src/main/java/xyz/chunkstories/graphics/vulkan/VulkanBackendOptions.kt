package xyz.chunkstories.graphics.vulkan

import xyz.chunkstories.api.util.configuration.OptionsDeclarationCtx
import xyz.chunkstories.client.InternalClientOptions

object VulkanBackendOptions {
    lateinit var shadowCascades: String private set
    lateinit var shadowMapSize: String private set
    lateinit var raytracedGI: String private set

    fun create(backend: VulkanGraphicsBackend): OptionsDeclarationCtx.() -> Unit = {
        section("client" ) {
            section("graphics") {
                shadowCascades = optionMultipleChoicesInt("shadowCascades") {
                    default = 1
                    possibleChoices = listOf(0, 1, 2, 3, 4)
                    hook {
                        backend.reloadRendergraph()
                    }
                }

                shadowMapSize = optionMultipleChoicesInt("shadowMapSize") {
                    default = 1024
                    possibleChoices = listOf(512, 1024, 2048)
                    hook {
                        backend.reloadRendergraph()
                    }
                }

                raytracedGI = optionBoolean("raytracedGI") {
                    default = false
                    hook {
                        backend.reloadRendergraph()
                    }
                }
            }
        }
    }
}