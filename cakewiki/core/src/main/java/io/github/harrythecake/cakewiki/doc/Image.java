package io.github.harrythecake.cakewiki.doc;

/** A picture on the wiki. {@code url} is absolute; width and height are the display size from the page (0 if not given). */
public record Image(String url, int width, int height, String alt) {
	public boolean animated() {
		String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
		return path.toLowerCase(java.util.Locale.ROOT).endsWith(".gif");
	}
}
