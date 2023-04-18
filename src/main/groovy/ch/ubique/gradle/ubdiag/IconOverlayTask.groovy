package ch.ubique.gradle.ubdiag

import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.ApplicationVariant
import com.android.builder.model.ProductFlavor
import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ProjectDependency

import java.nio.file.Files
import java.nio.file.StandardCopyOption

class IconOverlayTask {

	static Task create(Project project, AppExtension android, ApplicationVariant variant, File targetWebIcon) {

		String taskName = "overlayIcon${variant.name.capitalize()}"

		return project.task(taskName).doFirst {
			File moduleDir = new File(project.rootDir, project.name)

			long gradleLastModified = Math.max(
					new File(moduleDir, "build.gradle").lastModified(),
					new File(project.rootDir, "build.gradle").lastModified()
			)

			File generatedResDir = new File("${project.buildDir}/generated/res/launcher-icon/")

			// get banner label
			ProductFlavor flavor = variant.productFlavors[0]
			Boolean defaultLabelEnabled = android.defaultConfig.launcherIconLabelEnabled
			Boolean flavorLabelEnabled = flavor.launcherIconLabelEnabled
			String bannerLabel
			if (flavorLabelEnabled
					|| flavorLabelEnabled == null && defaultLabelEnabled
					|| flavorLabelEnabled == null && defaultLabelEnabled == null && !flavor.name.startsWith("prod")
			) {
				if (flavor.launcherIconLabel != null) {
					bannerLabel = flavor.launcherIconLabel
				} else {
					bannerLabel = variant.flavorName
				}
			} else {
				bannerLabel = null
			}

			File manifestFile = ManifestUtils.getMergedManifestFile(project, variant)

			List<BaseExtension> androidModules = project.configurations*.dependencies
					*.findAll { it instanceof ProjectDependency }
					.flatten()
					.collect { it.dependencyProject }
					.unique()
					.collect { it.extensions.findByType(BaseExtension) }
					.findAll { it != null }

			List<File> resDirs = androidModules
					.collect { [it.sourceSets.findByName(variant.flavorName), it.sourceSets.findByName("main")] }
					.flatten()
					.findAll { it != null }
					.collect { AndroidSourceSet ass -> ass.res.srcDirs }
					.flatten()
					.findAll { File file -> !file.path.contains("generated") }

			println("$taskName: resource directories: " + resDirs)

			List<File> allIcons = IconUtils.findIcons(resDirs, manifestFile)

			if (targetWebIcon != null) {
				targetWebIcon.delete()

				// search for web icon source
				File webIconSource = ((new File(moduleDir, "src/${variant.flavorName}").listFiles() ?: new File[0]) +
						(new File(moduleDir, "src/main").listFiles() ?: new File[0]) +
						(moduleDir.listFiles() ?: new File[0])).find {
					it.name.matches(".*(web|playstore|512)\\.(png|webp)")
				}

				if (webIconSource == null) {
					// set fallbackWebIcon
					webIconSource = IconUtils.findLargestIcon(allIcons)
				}

				if (webIconSource == null) {
					println("$taskName: web icon source not found")
				} else if (bannerLabel == null || bannerLabel.empty) {
					// no label so we only copy the sourceIcon and use this
					println("$taskName: web icon: $webIconSource")
					Files.copy(webIconSource.toPath(), targetWebIcon.toPath())
				} else {
					println("$taskName: web icon: $webIconSource")
					IconUtils.drawLabel(webIconSource, targetWebIcon, bannerLabel, false)
				}
			}

			if (bannerLabel == null || bannerLabel.empty) {
				// no label
				println("$taskName: skipped icon labelling")
				return
			}

			allIcons.each { File original ->
				String resTypeName = original.parentFile.name
				String originalBaseName = original.name.takeBefore(".")
				File targetDir = new File("${generatedResDir.toString()}/${variant.flavorName}/${variant.buildType.name}/$resTypeName")
				File modified = targetDir.listFiles({ File file -> file.name.matches("${originalBaseName}\\.[^.]+") } as FileFilter)?.find() as File
				if (modified != null && original.lastModified() <= modified.lastModified() && gradleLastModified <= modified.lastModified()) return
				println("$taskName: found modified launcher icon: " + original.absolutePath)
				File target = new File(targetDir, original.name)
				targetDir.mkdirs()
				Files.copy(original.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
				IconUtils.createLayeredLabel(target, bannerLabel, originalBaseName.endsWith("_foreground"))
			}
		}
	}

}
