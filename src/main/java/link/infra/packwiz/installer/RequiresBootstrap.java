package link.infra.packwiz.installer;

import de.comahe.i18n4k.I18n4kKt;
import de.comahe.i18n4k.config.I18n4kConfigDefault;

import javax.swing.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class RequiresBootstrap {

	public static void main(String[] args) {
		// Very small CLI implementation, because Commons CLI complains on unexpected
		// options

		// Set up i18n4k
		I18n4kConfigDefault i18nConfig = new I18n4kConfigDefault();
		I18n4kKt.setI18n4k(i18nConfig);
		Map<String, Locale> availLanguages = Msgs.INSTANCE.getLocales()
				.stream().collect(Collectors.toMap(Locale::toString, locale -> locale));
		String preferLanguage = Locale.getDefault().toString();
		while (!availLanguages.containsKey(preferLanguage) && preferLanguage.contains("_")) {
			preferLanguage = preferLanguage.substring(0, preferLanguage.lastIndexOf('_'));
		}
        i18nConfig.setLocale(availLanguages.getOrDefault(preferLanguage, Locale.ENGLISH));

		if (Arrays.stream(args).map(str -> {
			if (str == null) return "";
			if (str.startsWith("--")) {
				return str.substring(2);
			}
			if (str.startsWith("-")) {
				return str.substring(1);
			}
			return "";
		}).anyMatch(str -> str.equals("g") || str.equals("no-gui"))) {
			System.out.println(
					Msgs.getRequireBootstrap().invoke());
			System.exit(1);
		} else {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				// Ignore the exceptions, just continue using the ugly L&F
			}
			JOptionPane.showMessageDialog(null,
					Msgs.getRequireBootstrap().invoke(),
					"packwiz-installer", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

}
