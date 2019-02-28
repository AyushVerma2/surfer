package surfer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class CommandLineTests {

	@Test public void testCommandLine() throws IOException, InterruptedException {
		List<String> commands = new ArrayList<String>();
		commands.add("./test/bin/cli-test");
		ProcessBuilder pb = new ProcessBuilder(commands);
		File cwd = new File(System.getProperty("user.dir"));
		pb.directory(cwd);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		StringBuilder out = new StringBuilder();
		BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line = null, previous = null;
		while ((line = br.readLine()) != null) {
			previous = line;
			out.append(line).append('\n');
		}

		if (process.waitFor() == 0) {
			assertEquals("0 failures",previous);
		} else {
			fail("command line tests failed:\n" + out.toString());
		}
	}
}
