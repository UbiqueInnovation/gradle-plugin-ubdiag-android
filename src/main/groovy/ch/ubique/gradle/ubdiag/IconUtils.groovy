package ch.ubique.gradle.ubdiag

import groovy.io.FileType

import javax.imageio.ImageIO
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.List

class IconUtils {

	/**
	 * Icon name to search for in the app drawable folders
	 * if none can be found in the app manifest
	 */
	private static final String DEFAULT_ICON_NAME = "ic_launcher"

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
		String iconFilePath = iconFile.absolutePath

		File target = new File(iconFilePath.replaceAll("\\.([^./]+)\$", ".tmp.\$1"))

		drawLabel(iconFile, target, bannerLabel)

		iconFile.delete()
		target.renameTo(iconFile)
	}

	static void drawLabel(File sourceFile, File targetFile, String label) {
		BufferedImage img = ImageIO.read(sourceFile)
		int sourceWidth = img.getWidth()
		int sourceHeight = img.getHeight()

		GraphicsEnvironment.localGraphicsEnvironment.createGraphics(img).with { g ->
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

			// The banner center is anchored at 60% bottom right of the source image
			int anchorX = (sourceWidth * 0.6).intValue()
			int anchorY = (sourceHeight * 0.6).intValue()

			// Set the rotation to 45° around the anchor point
			AffineTransform bannerTransform = new AffineTransform()
			bannerTransform.rotate(Math.toRadians(-45), anchorX, anchorY)
			g.setTransform(bannerTransform)

			int bannerHeight = (sourceHeight / 5).intValue()
			Rectangle banner = new Rectangle(anchorX - sourceWidth, anchorY - (bannerHeight / 2).intValue(), sourceWidth * 2, bannerHeight)

			// Draw banner border to distinguish it from the background
			Rectangle shadow = new Rectangle(banner)
			shadow.grow(1, 1)
			g.setColor(Color.GRAY)
			g.fill(shadow)

			// Draw banner
			g.setColor(Color.WHITE)
			g.fill(banner)

			// Set font and calculate its size
			int labelFontSize = (sourceHeight / 7).intValue()
			Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, labelFontSize.intValue())
			FontMetrics fontMetrics = g.getFontMetrics(labelFont)
			int labelHeight = fontMetrics.ascent - fontMetrics.descent
			int labelWidth = fontMetrics.stringWidth(label.toUpperCase())

			// Draw label
			g.setFont(labelFont)
			g.setColor(Color.decode("#273c56"))
			g.drawString(label.toUpperCase(), anchorX - (labelWidth / 2), anchorY + (labelHeight / 2))
		}

		String fileExtension = sourceFile.name.substring(sourceFile.name.lastIndexOf('.') + 1)
		ImageIO.write(img, fileExtension, targetFile)
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
