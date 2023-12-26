package link.infra.packwiz.installer;

import javax.swing.*;
import java.util.Arrays;

public class RequiresBootstrap {

	public static void main(String[] args) {
		// Very small CLI implementation, because Commons CLI complains on unexpected
		// options
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
