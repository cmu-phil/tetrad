///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableExcluded;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.session.SessionNode;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Regression test for the stuck "Edit Parameters..." popup.
 * <p>
 * When an upstream data set is changed, the downstream box's model is destroyed while its parents still have models.
 * For a data-manipulation box there are then several constructible model classes, and the old code called
 * {@code determineTheModelClass}, which opens a modal model chooser, from inside {@code mousePressed} and from the
 * popup's {@code popupMenuWillBecomeVisible} listener. That is what left the popup menu stuck. The fix routes popup
 * construction through {@link SessionEditorNode#quietModelClass(SessionNode)}, which must never need a dialog: it
 * falls back on the last model class the box held, and returns null (rather than prompting) only when the class is
 * truly ambiguous.
 * <p>
 * This test reproduces the exact session-node state that triggered the bug and pins the quiet resolver's behavior.
 * On the unpatched code the method does not exist, so this fails to compile there; the behavioral claims it pins are
 * the ones the popup code now depends on.
 */
public class TestSessionEditorNodeQuietModelClass {

    /**
     * Stands in for the upstream data box's model.
     */
    public static class Source implements SessionModel, TetradSerializableExcluded {
        private static final long serialVersionUID = 23L;
        private String name;

        public Source(Parameters parameters) {
        }

        public static Source serializableInstance() {
            return new Source(new Parameters());
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * One of two competing downstream model classes (e.g. DataSubsetModel).
     */
    public static class SubsetA implements SessionModel, TetradSerializableExcluded {
        private static final long serialVersionUID = 23L;
        private String name;

        public SubsetA(Source source, Parameters parameters) {
        }

        public static SubsetA serializableInstance() {
            return new SubsetA(Source.serializableInstance(), new Parameters());
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * The other competing downstream model class (e.g. DiscretizationWrapper).
     */
    public static class SubsetB implements SessionModel, TetradSerializableExcluded {
        private static final long serialVersionUID = 23L;
        private String name;

        public SubsetB(Source source, Parameters parameters) {
        }

        public static SubsetB serializableInstance() {
            return new SubsetB(Source.serializableInstance(), new Parameters());
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static SessionNode[] buildParentAndChild() throws Exception {
        SessionNode parent = new SessionNode(new Class[]{Source.class});
        SessionNode child = new SessionNode(new Class[]{SubsetA.class, SubsetB.class});
        assertTrue(child.addParent(parent));
        parent.putParam(Source.class, new Parameters());
        child.putParam(SubsetA.class, new Parameters());
        child.putParam(SubsetB.class, new Parameters());
        parent.createModel(Source.class, false);
        return new SessionNode[]{parent, child};
    }

    /**
     * The trigger state: child model destroyed, parent still populated, more than one constructible class. The old
     * code would have prompted here. The quiet resolver must instead return the last model class.
     */
    @Test
    public void testDestroyedModelFallsBackOnLastModelClassWithoutPrompting() throws Exception {
        SessionNode[] nodes = buildParentAndChild();
        SessionNode child = nodes[1];

        child.createModel(SubsetA.class, false);
        assertNotNull(child.getModel());
        assertEquals(SubsetA.class, SessionEditorNode.quietModelClass(child));

        // Simulate the upstream data set changing: the downstream model is destroyed.
        child.destroyModel();
        assertNull(child.getModel());

        // This is the condition under which determineTheModelClass() opens a dialog.
        Class[] consistent = child.getConsistentModelClasses(true);
        assertTrue("Test setup must be ambiguous to exercise the bug", consistent.length > 1);

        // The quiet resolver settles it from the last model class instead.
        assertEquals(SubsetA.class, SessionEditorNode.quietModelClass(child));
    }

    /**
     * If the box has never held a model and the class is ambiguous, the resolver must return null rather than guess;
     * the caller (the menu action, after the popup has closed) is the one allowed to prompt.
     */
    @Test
    public void testAmbiguousWithNoHistoryReturnsNull() throws Exception {
        SessionNode[] nodes = buildParentAndChild();
        SessionNode child = nodes[1];

        assertNull(child.getModel());
        assertNull(child.getLastModelClass());
        assertTrue(child.getConsistentModelClasses(true).length > 1);

        assertNull(SessionEditorNode.quietModelClass(child));
    }

    /**
     * With exactly one constructible class and no history, the resolver picks it.
     */
    @Test
    public void testUniqueConsistentClassIsReturned() throws Exception {
        SessionNode parent = new SessionNode(new Class[]{Source.class});
        SessionNode child = new SessionNode(new Class[]{SubsetA.class});
        assertTrue(child.addParent(parent));
        parent.putParam(Source.class, new Parameters());
        parent.createModel(Source.class, false);

        assertNull(child.getLastModelClass());
        assertEquals(SubsetA.class, SessionEditorNode.quietModelClass(child));
    }

    /**
     * If the parent has no model, nothing is constructible and the resolver returns null.
     */
    @Test
    public void testNoParentModelReturnsNull() {
        SessionNode parent = new SessionNode(new Class[]{Source.class});
        SessionNode child = new SessionNode(new Class[]{SubsetA.class, SubsetB.class});
        assertTrue(child.addParent(parent));

        assertNull(SessionEditorNode.quietModelClass(child));
    }
}
