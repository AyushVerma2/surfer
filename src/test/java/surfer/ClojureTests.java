package surfer;

import java.util.Arrays;
import java.util.List;

import mikera.cljunit.ClojureTest;

/**
 * This class implements a JUnit test interface enabling the Clojure test suite
 * to be run using standard JUnit tooling.
 *
 * @author Mike
 *
 */
public class ClojureTests extends ClojureTest {

	@Override
	// public String filter() {
	// 	return "surfer";
	// }
	public List<String> namespaces() {
		return Arrays.asList(new String[] {
			"surfer.test-handler",
			"surfer.test-schemas",
			"surfer.test-storage",
			"surfer.test-users",
			"surfer.test-utils"
		});
	}
}
