package ch.ubique.gradle.ubdiag

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.builder.model.ProductFlavor
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class BuildPlugin implements Plugin<Project> {

	private static final String METADATA_KEY_BUILDID = "ub_buildid"
	private static final String METADATA_KEY_BUILDNUMBER = "ub_buildnumber"
	private static final String METADATA_KEY_BRANCH = "ub_branch"
	private static final String METADATA_KEY_FLAVOR = "ub_flavor"

	private Project project

	private String buildId
	private String buildNumber
	private String buildBranch
	private String buildFlavor


	void apply(Project project) {
		this.project = project

		buildId = project.hasProperty("buildid") ? project.property("buildid") :
				project.hasProperty("ubappid") ? project.property("ubappid") : "localbuild"
		buildNumber = project.hasProperty("buildnumber") ? project.property("buildnumber") : "0"
		buildBranch = project.hasProperty("branch") ? project.property("branch") : GitUtils.obtainBranch()
		String buildDir = project.hasProperty("buildDir") ? project.property("buildDir") : project.buildDir
		File targetWebIcon = project.hasProperty("webicon") ? new File(project.property("webicon").toString()) : null

		project.buildDir = buildDir

		// get android extension
		AppExtension android = getAndroidExtension()

		// BuildConfig fields
		android.defaultConfig { ProductFlavor flavor ->
			flavor.ext.set("launcherIconLabel", "")
			flavor.ext.set("launcherIconLabelEnabled", (Boolean) null)
		}

		android.productFlavors.whenObjectAdded { ProductFlavor flavor ->
			// Add the property 'launcherIconLabel' to each product flavor and set the default value to its name
			flavor.ext.set("launcherIconLabel", flavor.name)
			flavor.ext.set("launcherIconLabelEnabled", (Boolean) null)
		}

		project.afterEvaluate {
			// setup manifest manipulation task
			android.applicationVariants.all { ApplicationVariant variant ->
				variant.outputs.each { BaseVariantOutput output ->
					output.processManifestProvider.get().doLast {
						buildFlavor = variant.flavorName
						File manifestFile = ManifestUtils.getMergedManifestFile(project, variant)
						if (manifestFile.exists()) {
							manipulateManifestFile(manifestFile)
						}
					}
				}
			}

			// launcher icon manipulation task
			android.applicationVariants.all { ApplicationVariant variant ->
				variant.outputs.each { BaseVariantOutput output ->
					def overlayIconTask = IconOverlayTask.create(project, android, variant, targetWebIcon)

					/* hook overlayIconTask into android build chain */
					overlayIconTask.dependsOn output.processManifestProvider.get()
					variant.mergeResourcesProvider.get().dependsOn overlayIconTask
				}
			}
		}
	}

	/**
	 * Get the Android plugin extension.
	 * @return
	 */
	private AppExtension getAndroidExtension() {
		AppExtension ext = project.extensions.findByType(AppExtension)
		if (!ext) {
			throw new GradleException('Android gradle plugin extension has not been applied before')
		}
		return ext
	}

	/**
	 * Add custom meta data to manifest.
	 * @param manifestFile
	 */
	private void manipulateManifestFile(File manifestFile) {
		// read manifest file
		String manifestContent = manifestFile.getText('UTF-8')

		// inject meta-data tags into the manifest
		manifestContent = addMetaData(manifestContent, METADATA_KEY_BUILDID, buildId)
		manifestContent = addMetaData(manifestContent, METADATA_KEY_BUILDNUMBER, buildNumber)
		manifestContent = addMetaData(manifestContent, METADATA_KEY_BRANCH, buildBranch)
		manifestContent = addMetaData(manifestContent, METADATA_KEY_FLAVOR, buildFlavor)

		// store modified manifest
		manifestFile.write(manifestContent, 'UTF-8')
	}

	/**
	 * Inject a <meta-data> into the manifest XML.
	 * @param manifest content
	 * @param metaName meta-data key
	 * @param metaValue meta-data value
	 * @return
	 */
	private static String addMetaData(String manifest, String metaName, String metaValue) {
		String xmlAppClosingTag = "</application>"
		String metaTag = "<meta-data android:name=\"${metaName}\" android:value=\"${metaValue}\" />"
		return manifest.replace("${xmlAppClosingTag}", "    ${metaTag}\n    ${xmlAppClosingTag}")
	}

}
