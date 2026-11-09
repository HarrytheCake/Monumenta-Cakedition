package io.github.harrythecake.cakemap.platform;

import java.util.ServiceLoader;

/** The loader's {@link Platform}, found through META-INF/services. */
public final class Services {
	public static final Platform PLATFORM = load(Platform.class);

	private Services() {
	}

	static <T> T load(Class<T> type) {
		return ServiceLoader.load(type, Services.class.getClassLoader()).findFirst()
				.orElseThrow(() -> new IllegalStateException("No " + type.getName() + " for this mod loader"));
	}
}
