package ch.ubique.gradle.ubdiag

import groovy.io.FileType
import groovy.xml.XmlSlurper

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
					dir.eachFileMatch(FileType.FILES, ~"^.*${iconName}.(png|webp)") { file ->
						result.add(file)
					}
					dir.eachFileMatch(FileType.FILES, ~"^.*${iconName}_foreground.(png|webp|xml)") { file ->
						result.add(file)
					}
				}
				if (!result.isEmpty()) return result
			}
		}

		return Collections.emptyList()
	}

	/**
	 * Creates a layered drawable putting the label banner over the launcher icon.
	 *
	 * @param iconFile The image file which will be written too
	 * @param bannerLabel the label to draw
	 * @adaptive treat the icon as an adaptive launcher icon
	 */
	static void createLayeredLabel(File iconFile, String bannerLabel, boolean adaptive) {
		String iconName = iconFile.name.takeBefore(".")
		String iconExt = iconFile.name.takeAfter(".")
		String iconNameOverlay = iconName + "_overlay"
		File iconOverlayFile = new File(iconFile.parentFile, iconNameOverlay + ".png")
		iconOverlayFile.delete()
		String iconNameOriginal = iconName + "_original"
		File iconOriginalFile = new File(iconFile.parentFile, iconNameOriginal + "." + iconExt)
		iconOriginalFile.delete()

		String typeName = iconFile.parentFile.name
		String resType = typeName.startsWith("mipmap") ? "mipmap" : "drawable"

		// - create upper layer, transparent image with same size of iconFile
		int sourceWidth, sourceHeight
		if (iconExt.equalsIgnoreCase("xml")) {
			sourceWidth = 512
			sourceHeight = 512
		} else {
			BufferedImage img = ImageIO.read(iconFile)
			sourceWidth = img.getWidth()
			sourceHeight = img.getHeight()
		}
		BufferedImage overlayBitmap = createTransparentImage(sourceWidth, sourceHeight)
		// - draw label to upper layer
		drawLabelOnImage(overlayBitmap, bannerLabel, adaptive)
		ImageIO.write(overlayBitmap, "png", iconOverlayFile)

		// - move iconFile to iconFile-lower-layer
		iconFile.renameTo(iconOriginalFile)
		// - save $layerListXml into iconFile
		File layerListFile = new File(iconFile.parentFile, iconName + ".xml")
		String layerListXml = """\
		<?xml version="1.0" encoding="utf-8"?>
		<!-- GENERATED FILE - DO NOT EDIT -->
		<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
			<item android:drawable="@${resType}/$iconNameOriginal" />
			<item android:drawable="@${resType}/$iconNameOverlay" />
		</layer-list>
		""".stripIndent()
		layerListFile.write(layerListXml)
	}

	static void drawLabel(File sourceFile, File targetFile, String label, boolean adaptive) {
		BufferedImage img = ImageIO.read(sourceFile)
		drawLabelOnImage(img, label, adaptive)
		String fileExtension = sourceFile.name.substring(sourceFile.name.lastIndexOf('.') + 1)
		ImageIO.write(img, fileExtension, targetFile)
	}

	private static void drawLabelOnImage(BufferedImage img, String label, boolean adaptive) {
		int sourceWidth = img.getWidth()
		int sourceHeight = img.getHeight()
		double dp = img.getWidth() / 108.0

		GraphicsEnvironment.localGraphicsEnvironment.createGraphics(img).with { g ->
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

			float scale = adaptive ? 1.0 : 1.5

			// The banner center is anchored at 60% bottom right of the source image
			float anchorRel = adaptive ? 0.6 : 0.65
			int anchorX = (sourceWidth * anchorRel).intValue()
			int anchorY = (sourceHeight * anchorRel).intValue()

			// Set the rotation to 45° around the anchor point
			AffineTransform bannerTransform = new AffineTransform()
			bannerTransform.rotate(Math.toRadians(-45), anchorX, anchorY)
			g.setTransform(bannerTransform)

			int bannerHeight = (scale * sourceHeight / 5).intValue()
			Rectangle banner = new Rectangle(anchorX - sourceWidth, anchorY - (bannerHeight / 2).intValue(), sourceWidth * 2, bannerHeight)

			// Draw banner shadow to distinguish it from the background
			Rectangle shadow1 = new Rectangle(banner)
			shadow1.grow(0, (scale * 0.5 * dp).round().intValue())
			g.setColor(new Color(0, 0, 0, 58))
			g.fill(shadow1)

			Rectangle shadow2 = new Rectangle(banner)
			shadow2.setSize(shadow2.width.round().intValue(), (shadow2.height + scale * 1 * dp).round().intValue())
			g.setColor(new Color(0, 0, 0, 58))
			g.fill(shadow2)

			// Draw banner
			g.setColor(Color.WHITE)
			g.fill(banner)

			// Set font and calculate its size
			int labelFontSize = (scale * sourceHeight / 7).intValue()
			Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, labelFontSize.intValue())
			FontMetrics fontMetrics = g.getFontMetrics(labelFont)
			int labelHeight = fontMetrics.ascent - fontMetrics.descent
			int labelWidth = fontMetrics.stringWidth(label.toUpperCase())

			// Draw label
			g.setFont(labelFont)
			g.setColor(Color.decode("#273c56"))
			g.drawString(label.toUpperCase(), anchorX - (labelWidth / 2), anchorY + (labelHeight / 2))
		}
	}

	private static BufferedImage createTransparentImage(int width, int height) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
		GraphicsEnvironment.localGraphicsEnvironment.createGraphics(img).with { g ->
			g.setBackground(new Color(0, true))
			g.clearRect(0, 0, width, height)
			g.dispose()
		}
		return img
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
