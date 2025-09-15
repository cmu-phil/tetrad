package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.session.Session;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the basic functionality of the SessionEditorWorkbench.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class TestSessionEditorWorkbench extends TestCase {

    /**
     * The session being tested.
     */
    private SessionEditorWorkbench workbench;

    /**
     * Standard constructor for JUnit test cases.
     *
     * @param name a {@link java.lang.String} object
     */
    public TestSessionEditorWorkbench(String name) {
        super(name);
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     *
     * @return a {@link junit.framework.Test} object
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSessionEditorWorkbench.class);
    }

    /**
     * <p>setUp.</p>
     */
    public void setUp() {

        Session session = new Session("Test");
        SessionWrapper sessionWrapper = new SessionWrapper(session);

        this.workbench = new SessionEditorWorkbench(sessionWrapper);
    }

    /**
     * <p>testAddNodes.</p>
     */
    public void testAddNodes() {
        this.workbench.setNextButtonType("Graph");

    }
}





