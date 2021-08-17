package ch.ubique.gradle.ubdiag

import groovy.io.FileType

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class IconUtils {

	/**
	 * Icon name to search for in the app drawable folders
	 * if none can be found in the app manifest
	 */
	static final String DEFAULT_ICON_NAME = "ic_launcher";

	static String imageMagikCmd = getImageMagickCmd()

	/**
	 * Retrieve the app icon from the application manifest
	 *
	 * @param manifestFile The file pointing to the AndroidManifest
	 * @return The icon name specified in the {@code <application/   >} node
	 */
	static String getIconName(File manifestFile) {
		if (manifestFile == null || manifestFile.isDirectory() || !manifestFile.exists()) {
			return null;
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
					dir.eachFileMatch(FileType.FILES, ~"^.*${iconName}(_foreground)?.png") { file ->
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
		if (getImageMagickCmd() == null) {
			// ImageMagik not available
			System.err.println("LauncherIconLabelTask: Skipped launcher icon labelling because ImageMagick was not found on this system.")
			return
		}

		String iconFilePath = iconFile.absolutePath;

		File target = new File(iconFilePath.replaceAll("\\.([^./]+)\$", ".tmp.\$1"));

		def adaptive = false
		if (target.name.contains("_foreground")) {
			adaptive = true
		}

		drawLabel(iconFile, target, bannerLabel, adaptive);

		iconFile.delete();
		target.renameTo(iconFile);
	}

	static void drawLabel(File sourceFile, File targetFile, String label, boolean adaptiveIcon = false) {
		String magickCmd = getImageMagickCmd();
		if (magickCmd == null) {
			// ImageMagik not available
			System.err.println("LauncherIconLabelTask: Skipped launcher icon labelling because ImageMagick was not found on this system.")
			return
		}

		BufferedImage img = ImageIO.read(sourceFile);
		def width = img.getWidth();

		String bannerFileName = adaptiveIcon ? "/banner_adaptive.png" : "/banner.png";

		// targetFile is our .tmp.-file which has the banner
		targetFile.delete();
		targetFile.withOutputStream { out -> out << getClass().getResourceAsStream(bannerFileName) };

		String[] cmd;
		int labelFontSize = (int) (110 * Math.min(1f, 1f / label.length() * 3.5f))
		String drawCall = "gravity center translate -256,-256 font-size " + labelFontSize + " fill #273c56 rotate -45 text 0,475 '" + label.toUpperCase() + "'";
		cmd = [magickCmd, targetFile.absolutePath, "-draw", drawCall, targetFile.absolutePath];
		println(cmd.join(" "));
		cmd.execute().waitForProcessOutput((OutputStream) System.out, System.err);
		cmd = [magickCmd, targetFile.absolutePath, "-resize", width + "x" + width, targetFile.absolutePath];
		println(cmd.join(" "));
		cmd.execute().waitForProcessOutput((OutputStream) System.out, System.err);
		cmd = [magickCmd, "composite", targetFile.absolutePath, sourceFile.absolutePath, "-compose", adaptiveIcon ? "Over" : "Src_In", targetFile.absolutePath];
		println(cmd.join(" "));
		cmd.execute().waitForProcessOutput((OutputStream) System.out, System.err);
		cmd = [magickCmd, "-page", "+0+0", sourceFile.absolutePath, "-page", "+0+0", targetFile.absolutePath, "-background", "transparent", "-layers", "flatten", targetFile.absolutePath];
		println(cmd.join(" "));
		cmd.execute().waitForProcessOutput((OutputStream) System.out, System.err);
	}

	/**
	 * Obtain a functional ImageMagik command for the running platform.
	 * @return command name, or null if not available
	 */
	private static String getImageMagickCmd() {
		if (imageMagikCmd == null) {
			String[] magickCmds = ["magick"];
			for (String cmd : magickCmds) {
				try {
					Runtime rt = Runtime.getRuntime();
					Process proc = rt.exec("$cmd -version");
					int exitValue = proc.waitFor();
					if (exitValue == 0 || exitValue == 1) {
						// command exists
						return (imageMagikCmd = cmd)
					}
					// else try next command
				}
				catch (Exception ignored) {
					// try next command
					ignored.printStackTrace();
				}
			}
		}
		return imageMagikCmd
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
}
