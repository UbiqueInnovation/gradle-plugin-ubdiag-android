package ch.ubique.gradle.ubdiag

import groovy.io.FileType

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class IconUtils {

	/**
	 * Icon name to search for in the app drawable folders
	 * if none can be found in the app manifest
	 */
	private static final String DEFAULT_ICON_NAME = "ic_launcher"

	private static Integer imageMagickVersion = null

	/**
	 * Retrieve the app icon from the application manifest
	 *
	 * @param manifestFile The file pointing to the AndroidManifest
	 * @return The icon name specified in the {@code <application/   >} node
	 */
	static String getIconName(File manifestFile) {
		if (manifestFile == null || manifestFile.isDirectory() || !manifestFile.exists()) {
			return null
		}

		def manifestXml = new XmlSlurper().parse(manifestFile)
		def fileName = manifestXml?.application?.@'android:icon'?.text()
		return fileName ? fileName?.split("/")[1] : null
	}

	/**
	 * Finds all icon files matching the icon specified in the given manifest.
	 *
	 * If no icon can be found in the manifest, a default of {@link IconUtils#DEFAULT_ICON_NAME} will be used
	 */
	static List<File> findIcons(List<File> resDirs, File manifest) {
		String iconName = getIconName(manifest) ?: DEFAULT_ICON_NAME

		for (File resDir : resDirs) {
			if (resDir.exists()) {
				List<File> result = new ArrayList<>()
				resDir.eachDirMatch(~/^drawable.*|^mipmap.*/) { dir ->
					dir.eachFileMatch(FileType.FILES, ~"^.*${iconName}(_foreground)?.(png|webp)") { file ->
						result.add(file)
					}
				}
				if (!result.isEmpty()) return result
			}
		}

		return Collections.emptyList()
	}

	/**
	 * Draws the given text over an image
	 *
	 * @param iconFile The image file which will be written too
	 * @param lines The lines of text to be displayed
	 */
	static void doStuff(File iconFile, String bannerLabel) {
		if (getImageMagickVersion() <= 0) {
			// ImageMagik not available
			System.err.println("LauncherIconLabelTask: Skipped launcher icon labelling because ImageMagick was not found on this system.")
			return
		}

		String iconFilePath = iconFile.absolutePath

		File target = new File(iconFilePath.replaceAll("\\.([^./]+)\$", ".tmp.\$1"))

		def adaptive = false
		if (target.name.contains("_foreground")) {
			adaptive = true
		}

		drawLabel(iconFile, target, bannerLabel, adaptive)

		iconFile.delete()
		target.renameTo(iconFile)
	}

	static void drawLabel(File sourceFile, File targetFile, String label, boolean adaptiveIcon = false) {
		int magickVersion = getImageMagickVersion()
		if (magickVersion <= 0) {
			// ImageMagik not available
			System.err.println("LauncherIconLabelTask: Skipped launcher icon labelling because ImageMagick was not found on this system.")
			return
		}

		BufferedImage img = ImageIO.read(sourceFile)
		def width = img.getWidth()

		String bannerFileName = adaptiveIcon ? "/banner_adaptive.png" : "/banner.png"

		// targetFile is our .tmp.-file which has the banner
		targetFile.delete()
		targetFile.withOutputStream { out -> out << getClass().getResourceAsStream(bannerFileName) }

		int labelFontSize = (int) (110 * Math.min(1f, 1f / label.length() * 3.5f))
		String drawSpec = "gravity center translate -256,-256 font-size " + labelFontSize + " fill #273c56 rotate -45 text 0,475 '" + label.toUpperCase() + "'"

		switch (magickVersion) {
			case 7: {
				execCmd("magick", targetFile.absolutePath, "-draw", drawSpec, targetFile.absolutePath)
				execCmd("magick", targetFile.absolutePath, "-resize", width + "x" + width, targetFile.absolutePath)
				execCmd("magick", "composite", targetFile.absolutePath, sourceFile.absolutePath, "-compose", adaptiveIcon ? "Over" : "Src_In", targetFile.absolutePath)
				execCmd("magick", "-page", "+0+0", sourceFile.absolutePath, "-page", "+0+0", targetFile.absolutePath, "-background", "transparent", "-layers", "flatten", targetFile.absolutePath)
				break
			}
			case 6: {
				execCmd("convert", targetFile.absolutePath, "-draw", drawSpec, targetFile.absolutePath)
				execCmd("convert", targetFile.absolutePath, "-resize", width + "x" + width, targetFile.absolutePath)
				execCmd("composite", targetFile.absolutePath, sourceFile.absolutePath, "-compose", adaptiveIcon ? "Over" : "Src_In", targetFile.absolutePath)
				execCmd("convert", "-page", "+0+0", sourceFile.absolutePath, "-page", "+0+0", targetFile.absolutePath, "-background", "transparent", "-layers", "flatten", targetFile.absolutePath)
				break
			}
			default: {
				System.err.println("LauncherIconLabelTask: unknown ImageMagick version $magickVersion")
			}
		}
	}

	/**
	 * Obtain a functional ImageMagik command for the running platform.
	 * @return command name, or null if not available
	 */
	private static int getImageMagickVersion() {
		if (imageMagickVersion != null) {
			return imageMagickVersion
		}

		try {
			int r = Runtime.getRuntime().exec("magick -version").waitFor()
			if (r == 0 || r == 1) {
				return (imageMagickVersion = 7)
			}
		}
		catch (Exception ignored) {
		}
		try {
			int r1 = Runtime.getRuntime().exec("convert -version").waitFor()
			int r2 = Runtime.getRuntime().exec("composite -version").waitFor()
			if ((r1 == 0 || r1 == 1) && (r2 == 0 || r2 == 1)) {
				return (imageMagickVersion = 6)
			}
		}
		catch (Exception ignored) {
		}

		return (imageMagickVersion = -1)
	}

	/**
	 * Find the largest launcher icon drawable.
	 * @return
	 */
	static File findLargestIcon(List<File> iconFiles) {
		List<File> filterIconFiles = iconFiles.stream()
				.filter({ file -> !file.name.contains("_foreground") })
				.collect()

		File iconForWebIconFallback = null

		filterIconFiles.each { iconFile ->
			if (iconForWebIconFallback == null || iconForWebIconFallback.size() < iconFile.size()) {
				// use largest icon as fallback for web; assume file size correlates with actual image size
				iconForWebIconFallback = iconFile
			}
		}

		return iconForWebIconFallback
	}

	private static void execCmd(String... cmd) {
		println("LauncherIconLabelTask: " + cmd.collect { it.contains(" ") ? "\"$it\"" : it }.join(" "))
		cmd.execute().waitForProcessOutput((OutputStream) System.out, System.err)
	}
}
