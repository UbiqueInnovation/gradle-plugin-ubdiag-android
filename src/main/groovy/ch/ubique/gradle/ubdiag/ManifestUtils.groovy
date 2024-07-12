package ch.ubique.gradle.ubdiag

import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Project

class ManifestUtils {

	/**
	 * returns File with merged manifest
	 */
	static File getMergedManifestFile(Project project, ApplicationVariant variant) {
		String variantName = variant.flavorName + variant.buildType.name.capitalize()
		return new File(
				project.layout.buildDirectory.asFile.get(),
				"intermediates/merged_manifests/${variantName}/process${variantName}Manifest/AndroidManifest.xml"
		)
	}

}
