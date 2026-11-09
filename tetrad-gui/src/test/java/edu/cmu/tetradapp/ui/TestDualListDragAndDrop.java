package edu.cmu.tetradapp.ui;

import org.junit.Test;

import javax.swing.*;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * The model-level half of a drop between two lists, or within one, which is where the index bookkeeping lives. Runs
 * without a display.
 */
public class TestDualListDragAndDrop {

    private static DefaultListModel<String> model(String... items) {
        DefaultListModel<String> m = new DefaultListModel<>();
        for (String s : items) m.addElement(s);
        return m;
    }

    private static List<String> contents(DefaultListModel<String> m) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < m.getSize(); i++) out.add(m.get(i));
        return out;
    }

    /**
     * Dropping on the other list at an index removes from the source and inserts there, in order.
     */
    @Test
    public void testMoveAcrossAtIndex() {
        DefaultListModel<String> a = model("A", "B", "C", "D");
        DefaultListModel<String> b = model("X", "Y");
        String bItem = a.get(1), dItem = a.get(3);

        int[] placed = DualListDragAndDrop.moveItems(a, b, List.of(bItem, dItem), 1);

        assertEquals(List.of("A", "C"), contents(a));
        assertEquals(List.of("X", "B", "D", "Y"), contents(b));
        assertArrayEquals(new int[]{1, 2}, placed);
    }

    /**
     * A negative or out-of-range index means the end.
     */
    @Test
    public void testMoveAcrossToEnd() {
        DefaultListModel<String> a = model("A", "B");
        DefaultListModel<String> b = model("X");
        int[] placed = DualListDragAndDrop.moveItems(a, b, List.of(a.get(0)), -1);
        assertEquals(List.of("X", "A"), contents(b));
        assertArrayEquals(new int[]{1}, placed);
        placed = DualListDragAndDrop.moveItems(a, b, List.of(a.get(0)), 99);
        assertEquals(List.of("X", "A", "B"), contents(b));
        assertArrayEquals(new int[]{2}, placed);
        assertEquals(0, a.getSize());
    }

    /**
     * An item already in the target (same object) is removed from the source but not duplicated.
     */
    @Test
    public void testNoDuplicates() {
        String shared = "S";
        DefaultListModel<String> a = model("A");
        a.addElement(shared);
        DefaultListModel<String> b = model("X");
        b.addElement(shared);
        int[] placed = DualListDragAndDrop.moveItems(a, b, List.of(a.get(0), shared), 0);
        assertEquals(List.of(), contents(a));
        assertEquals(List.of("A", "X", "S"), contents(b));
        assertArrayEquals(new int[]{0}, placed);
    }

    /**
     * Reordering within one list: dropping below the selection's own gap is a no-op, dropping at the top moves the
     * items to the top, dropping at the end moves them to the end, and a non-contiguous selection lands contiguous
     * at the drop point.
     */
    @Test
    public void testReorderWithinList() {
        DefaultListModel<String> m = model("A", "B", "C", "D", "E");

        // Dragging B and dropping on the gap right after B (index 2): unchanged, B at 1.
        int[] placed = DualListDragAndDrop.moveItems(m, m, List.of(m.get(1)), 2);
        assertEquals(List.of("A", "B", "C", "D", "E"), contents(m));
        assertArrayEquals(new int[]{1}, placed);

        // Dragging D to the top.
        placed = DualListDragAndDrop.moveItems(m, m, List.of(m.get(3)), 0);
        assertEquals(List.of("D", "A", "B", "C", "E"), contents(m));
        assertArrayEquals(new int[]{0}, placed);

        // Dragging D and B (non-contiguous) to the end: they land together at the end, in drag order.
        placed = DualListDragAndDrop.moveItems(m, m, List.of(m.get(0), m.get(2)), 5);
        assertEquals(List.of("A", "C", "E", "D", "B"), contents(m));
        assertArrayEquals(new int[]{3, 4}, placed);

        // Dragging A and C to the gap between E and D (index 3 before removal): the two removed items were above
        // the gap, so it moves up to 1, and they land at 1 and 2.
        placed = DualListDragAndDrop.moveItems(m, m, List.of(m.get(0), m.get(1)), 3);
        assertEquals(List.of("E", "A", "C", "D", "B"), contents(m));
        assertArrayEquals(new int[]{1, 2}, placed);
    }

    /**
     * Installing refuses lists without a DefaultListModel; with a display, it gives both lists the insert drop mode
     * and one shared handler. (JList.setDragEnabled throws HeadlessException without a display, so that half runs
     * only where there is one.)
     */
    @Test
    public void testInstall() {
        JList<String> a = new JList<>(model("A"));
        JList<String> b = new JList<>(model("B"));
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            DualListDragAndDrop.install(a, b, null);
            assertEquals(DropMode.INSERT, a.getDropMode());
            assertEquals(a.getTransferHandler(), b.getTransferHandler());
        }
        try {
            DualListDragAndDrop.install(new JList<>(new String[]{"x"}), b, null);
            org.junit.Assert.fail("expected refusal");
        } catch (IllegalArgumentException expected) {
            // wanted
        }
    }
}
