package surfer;

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
	public String filter() {
		return "surfer";
	}

}
