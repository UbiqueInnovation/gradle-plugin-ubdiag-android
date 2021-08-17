package ch.ubique.gradle.ubdiag

import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Project

class ManifestUtils {

	/**
	 * returns File with merged manifest
	 */
	static File getMergedManifestFile(Project project, ApplicationVariant variant) {
		return new File(
				project.buildDir,
				"intermediates/merged_manifests/${variant.flavorName}${variant.buildType.name.capitalize()}/AndroidManifest.xml"
		)
	}

}
