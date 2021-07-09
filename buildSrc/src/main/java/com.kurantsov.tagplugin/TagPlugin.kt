package com.kurantsov.tagplugin

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class TagPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            extensions.findByType(BaseExtension::class.java)?.let {
                val generator = TagGenerator(logger)
                it.registerTransform(generator)
            }
        }
    }
}