package edu.cmu.tetradapp.test;

import edu.cmu.tetradapp.session.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Tests the basic function of the SessionEvent class--that is, whether events can be constructed properly and whether
 * the information can be extracted from them properly.
 *
 * @author josephramsey
 */
public class TestSessionEvent {

    /**
     * The session for the events being tested.
     */
    private final Session session = new Session("Test");

    /**
     * Tests whether an ADD_NODE event can be constructed properly.
     */
    @Test
    public void testAddNodeEvent() {

        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(this.session, node, SessionEvent.NODE_ADDED);

        assertSame(node, event.getNode());
        assertEquals(SessionEvent.NODE_ADDED, event.getType());
    }

    /**
     * Tests whether an NODE_REMOVED event can be constructed properly.
     */
    @Test
    public void testRemoveNodeEvent() {

        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(this.session, node, SessionEvent.NODE_REMOVED);

        assertSame(node, event.getNode());
        assertEquals(SessionEvent.NODE_REMOVED, event.getType());
    }

    /**
     * Tests whether an MODEL_CREATED event can be constructed properly.
     */
    @Test
    public void testModelCreatedEvent() {

        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(this.session, node, SessionEvent.MODEL_CREATED);

        assertSame(node, event.getNode());
        assertEquals(SessionEvent.MODEL_CREATED, event.getType());
    }

    /**
     * Tests whether an MODEL_DESTROYED event can be constructed properly.
     */
    @Test
    public void testModelDestroyedEvent() {
        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(this.session, node, SessionEvent.MODEL_DESTROYED);

        assertSame(node, event.getNode());
        assertEquals(SessionEvent.MODEL_DESTROYED, event.getType());
    }

    /**
     * Tests whether an MODEL_UNCLEAR event can be constructed properly.
     */
    @Test
    public void testModelUnclearEvent() {
        SessionNode node = new SessionNode(Type1.class);
        SessionEvent event =
                new SessionEvent(this.session, node, SessionEvent.MODEL_UNCLEAR);

        assertSame(node, event.getNode());
        assertEquals(SessionEvent.MODEL_UNCLEAR, event.getType());
    }

    /**
     * Tests whether a PARENT_ADDED event can be constructed properly.
     */
    @Test
    public void testParentAddedEvent() {
        SessionNode child = new SessionNode(Type1.class);
        SessionNode parent = new SessionNode(Type2.class);
        SessionEvent event = new SessionEvent(this.session, parent, child,
                SessionEvent.PARENT_ADDED);

        assertSame(child, event.getChild());
        assertSame(parent, event.getParent());
        assertEquals(SessionEvent.PARENT_ADDED, event.getType());
    }

    /**
     * Tests whether a PARENT_REMOVED event can be constructed properly.
     */
    @Test
    public void testParentRemovedEvent() {
        SessionNode child = new SessionNode(Type1.class);
        SessionNode parent = new SessionNode(Type2.class);
        SessionEvent event = new SessionEvent(this.session, parent, child,
                SessionEvent.PARENT_REMOVED);

        assertSame(child, event.getChild());
        assertSame(parent, event.getParent());
        assertEquals(SessionEvent.PARENT_REMOVED, event.getType());
    }
}





