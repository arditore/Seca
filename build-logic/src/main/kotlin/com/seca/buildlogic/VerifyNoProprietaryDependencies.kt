package com.seca.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build if a proprietary Google artifact reaches an app's runtime
 * classpath.
 *
 * Inspects resolved Maven coordinates, not file paths: AARs are transformed
 * before they reach a classpath and the transformed path no longer contains
 * the group, so a path-based check cannot see them. This is a tripwire for the
 * well-known offenders, not an exhaustive scanner — F-Droid's own scanner
 * remains the authority.
 */
abstract class VerifyNoProprietaryDependencies : DefaultTask() {

    /** Wired from `incoming.resolutionResult.rootComponent`; configuration-cache safe. */
    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @TaskAction
    fun verify() {
        val offenders = resolvedModules(rootComponent.get())
            .filter { id -> ForbiddenGroups.any { id.group == it || id.group.startsWith("$it.") } }
            .map { "${it.group}:${it.module}:${it.version}" }
            .sorted()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Dépendances propriétaires sur le classpath d'exécution :\n" +
                    offenders.joinToString("\n") { "  - $it" },
            )
        }
    }

    private fun resolvedModules(root: ResolvedComponentResult): Set<ModuleComponentIdentifier> {
        val seen = mutableSetOf<ResolvedComponentResult>()
        val modules = mutableSetOf<ModuleComponentIdentifier>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            if (!seen.add(component)) continue
            (component.id as? ModuleComponentIdentifier)?.let(modules::add)
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { queue.addLast(it.selected) }
        }
        return modules
    }

    private companion object {
        val ForbiddenGroups = listOf(
            "com.google.android.gms",
            "com.google.firebase",
            "com.google.android.play",
        )
    }
}
