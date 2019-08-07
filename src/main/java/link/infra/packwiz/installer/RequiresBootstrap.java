package link.infra.packwiz.installer;

import javax.swing.*;
import java.util.Arrays;

public class RequiresBootstrap {

	public static void main(String[] args) {
		// Very small CLI implementation, because Commons CLI complains on unexpected
		// options
		// Also so that Commons CLI can be excluded from the shaded JAR, as it is
		// included in the bootstrap
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
					"This program must be run through packwiz-installer-bootstrap. Use --bootstrap-no-update to disable updating.");
			System.exit(1);
		} else {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e) {
				// Ignore the exceptions, just continue using the ugly L&F
			}
			JOptionPane.showMessageDialog(null,
					"This program must be run through packwiz-installer-bootstrap. Use --bootstrap-no-update to disable updating.",
					"packwiz-installer", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}

}
