package io.github.harrythecake.cakeui.model;

/** Easing curves for transitions: t in 0..1 → progress (may overshoot for back/elastic). */
public final class Easing {
	private Easing() {
	}

	public static double apply(String name, double t) {
		t = Math.max(0, Math.min(1, t));
		if (name == null) {
			return ease(t);
		}
		return switch (name) {
			case "linear" -> t;
			case "ease_in" -> t * t * t;
			case "ease_out" -> 1 - Math.pow(1 - t, 3);
			case "ease_in_out" -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
			case "back_out" -> {
				double c1 = 1.70158;
				double c3 = c1 + 1;
				yield 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
			}
			case "elastic_out" -> t == 0 || t == 1 ? t : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
			case "bounce_out" -> bounce(t);
			default -> ease(t);
		};
	}

	/** CSS "ease" approximated by a smooth start and a soft end. */
	private static double ease(double t) {
		return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
	}

	private static double bounce(double t) {
		double n1 = 7.5625;
		double d1 = 2.75;
		if (t < 1 / d1) {
			return n1 * t * t;
		} else if (t < 2 / d1) {
			t -= 1.5 / d1;
			return n1 * t * t + 0.75;
		} else if (t < 2.5 / d1) {
			t -= 2.25 / d1;
			return n1 * t * t + 0.9375;
		}
		t -= 2.625 / d1;
		return n1 * t * t + 0.984375;
	}
}
