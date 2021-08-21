package ch.ubique.gradle.ubdiag

import com.android.build.api.dsl.AndroidSourceDirectorySet
import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.ApplicationVariant
import com.android.builder.model.ProductFlavor
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
			//empty generated icon res folder
			File generatedResDir = new File("${project.buildDir}/generated/res/icon/")

			JsonSlurper jsonSlurper = new JsonSlurper()
			File timestampFile = new File(generatedResDir, "timestampFile")
			Map<String, Object> timestampMap

			if (timestampFile.exists()) {
				timestampMap = jsonSlurper.parse(timestampFile)
			} else {
				timestampMap = new LinkedHashMap<>()
				generatedResDir.deleteDir()
			}

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

			println("$taskName: resource directories: " + resDirs)

			List<File> allIcons = IconUtils.findIcons(resDirs, manifestFile)

			if (targetWebIcon != null) {
				targetWebIcon.delete()

				// search for web icon source
				File moduleDir = new File(project.rootDir, project.name)
				File webIconSource = ((new File(moduleDir, "src/${variant.flavorName}").listFiles() ?: new File[0]) +
						(new File(moduleDir, "src/main").listFiles() ?: new File[0]) +
						(moduleDir.listFiles() ?: new File[0])).find {
					it.name.matches(".*(web|playstore|512)\\.png")
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
					IconUtils.drawLabel(webIconSource, targetWebIcon, bannerLabel)
				}
			}

			if (bannerLabel == null || bannerLabel.empty) {
				// no label
				println("$taskName: skipped icon labelling")
				return
			}

			String buildType = variant.buildType.name
			String highestPriorityResFolder = variant.flavorName + "/" + buildType

			AndroidSourceSet sourceSet = android.sourceSets.maybeCreate("${variant.flavorName}${buildType.capitalize()}")
			sourceSet.res { AndroidSourceDirectorySet res ->
				res.srcDirs += new File("${project.buildDir}/generated/res/icon/${variant.flavorName}/$buildType/")
			}

			def updateTimestampJSON = false
			List<String> touchedIcons = new ArrayList<>()

			allIcons.each { File icon ->
				String typeName = icon.parentFile.name

				String key = "${variant.flavorName}-$typeName-${icon.name.substring(0, icon.name.indexOf("."))}"
				touchedIcons.add(key)

				Map savedObject = timestampMap.get(key)
				if (savedObject != null) {
					Long lastModified = (Long) savedObject.get("lastmodified")
					if (icon.lastModified() == lastModified) return
					return
				}

				println("$taskName: found modified icon: " + icon.absolutePath)

				File copy = new File("${generatedResDir.toString()}/$highestPriorityResFolder/$typeName/${icon.name}")
				copy.parentFile.mkdirs()
				Files.copy(icon.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)

				IconUtils.doStuff(copy, bannerLabel)

				savedObject = new LinkedHashMap<String, Object>()
				savedObject.put("lastmodified", icon.lastModified())
				savedObject.put("path", copy.absolutePath)
				timestampMap.put(key, savedObject)
				updateTimestampJSON = true
			}

			def iterator = timestampMap.entrySet().iterator()
			while (iterator.hasNext()) {
				def iconObj = iterator.next()
				def key = iconObj.key
				if (!touchedIcons.contains(key)) {
					Map savedObject = iconObj.getValue()
					File aFile = new File((String) savedObject.get("path"))
					if (aFile.exists()) aFile.delete()

					iterator.remove()
					updateTimestampJSON = true
				}
			}

			if (updateTimestampJSON) {
				timestampFile.write(JsonOutput.toJson(timestampMap))
			}
		}
	}

}
