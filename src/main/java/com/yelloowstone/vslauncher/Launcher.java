package com.yelloowstone.vslauncher;

public class Launcher {
	public static void main(String[] args) {
		// "search ...": the command line save search, without starting JavaFX (works headless, over SSH)
		if (args.length > 0 && args[0].equals("search")) {
			System.exit(SaveSearchCli.run(java.util.Arrays.copyOfRange(args, 1, args.length)));
		}
		// This bypasses the JavaFX runtime check
		App.main(args);
	}
}
