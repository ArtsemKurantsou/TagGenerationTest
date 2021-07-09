package com.kurantsov.tagplugin

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import javassist.ClassPool
import javassist.CtField
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import java.io.File

class TagGenerator(private val logger: Logger) : Transform() {

    private val pool = ClassPool.getDefault()

    override fun getName(): String {
        return "TagGenerator"
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return mutableSetOf(QualifiedContent.DefaultContentType.CLASSES)
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return mutableSetOf(QualifiedContent.Scope.PROJECT)
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun transform(transformInvocation: TransformInvocation?) {
        super.transform(transformInvocation)
        transformInvocation ?: return
        transformInvocation.outputProvider.deleteAll()

        transformInvocation.inputs.forEach { transformInput ->
            transformInput.directoryInputs.forEach { directoryInput ->
                val inputFile = directoryInput.file

                val destFolder = transformInvocation.outputProvider.getContentLocation(
                    directoryInput.name,
                    directoryInput.contentTypes,
                    directoryInput.scopes,
                    Format.DIRECTORY
                )
                logger.log(LogLevel.WARN, "Processing contentLocation - ${inputFile.absoluteFile}")
                pool.appendClassPath(inputFile.absolutePath)
                transformDir(inputFile, destFolder, inputFile)
            }

            transformInput.jarInputs.forEach { jarInput ->
                val inputFile = jarInput.file

                val destFolder = transformInvocation.outputProvider.getContentLocation(
                    jarInput.name,
                    jarInput.contentTypes,
                    jarInput.scopes,
                    Format.JAR
                )
                inputFile.copyTo(destFolder, true)
            }
        }
    }

    private fun transformDir(input: File, dest: File, location: File) {
        if (dest.exists()) {
            dest.deleteRecursively()
        }
        dest.mkdirs()
        val srcDirPath = input.absolutePath
        val destDirPath = dest.absolutePath
        for (file in input.listFiles()) {
            val destFilePath = file.absolutePath.replace(srcDirPath, destDirPath)
            val destFile = File(destFilePath)
            if (file.isDirectory) {
                transformDir(file, destFile, location)
            } else if (file.isFile) {
                if (file.name.endsWith(".class")
                    && !file.name.endsWith("R.class")
                    && !file.name.endsWith("BuildConfig.class")
                    && !file.name.contains("R\$")
                ) {
                    transformSingleFile(file, destFile, location)
                } else {
                    file.copyTo(destFile)
                }
            }
        }
    }

    private fun transformSingleFile(input: File, output: File, location: File) {
        val className = input.relativeTo(location).path.replace(File.separator, ".").replace(".class", "")
        logger.log(LogLevel.WARN, "Processing $input ($className) -> $output")
        val c = pool.getCtClass(className)
        if (c.isFrozen) {
            c.defrost()
        }
        if (c.fields.none { it.name == "TAG" }) {
            val field = CtField.make("public static final String TAG = \"${c.simpleName}\";", c)
            c.addField(field)
        }
        output.writeBytes(c.toBytecode())
        c.detach()
    }
}