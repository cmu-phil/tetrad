package edu.cmu.tetradapp.util;

import org.junit.Test;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Specifies ArrowKeyNavigation: plain arrows bound in the ancestor map, modified arrows in the
 * window map, selection stepping with wrap-around, a -1 (no selection) start going to 0,
 * combo-box action listeners firing on a step, and idempotent installation. Runs headless.
 */
public class TestArrowKeyNavigation {

    private static void fire(JComponent host, int condition, int keyCode, int modifiers) {
        KeyStroke ks = KeyStroke.getKeyStroke(keyCode, modifiers);
        Object key = host.getInputMap(condition).get(ks);
        assertNotNull("no binding for " + ks + " under condition " + condition, key);
        Action a = host.getActionMap().get(key);
        assertNotNull(a);
        a.actionPerformed(new ActionEvent(host, ActionEvent.ACTION_PERFORMED, ""));
    }

    private static JTabbedPane tabs(int n) {
        JTabbedPane t = new JTabbedPane(SwingConstants.LEFT);
        for (int i = 0; i < n; i++) t.addTab("t" + i, new JPanel());
        ArrowKeyNavigation.install(t);
        return t;
    }

    @Test
    public void testTabsStepAndWrap() {
        JTabbedPane t = tabs(3);
        t.setSelectedIndex(0);
        fire(t, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_DOWN, 0);
        assertEquals(1, t.getSelectedIndex());
        fire(t, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_DOWN, 0);
        fire(t, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_DOWN, 0);
        assertEquals("wraps to first", 0, t.getSelectedIndex());
        fire(t, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_UP, 0);
        assertEquals("wraps to last", 2, t.getSelectedIndex());
    }

    @Test
    public void testModifiedArrowsBoundInWindowMap() {
        JTabbedPane t = tabs(3);
        t.setSelectedIndex(1);
        int menu = KeyEvent.CTRL_DOWN_MASK;
        try {
            menu = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException ignored) {
        }
        fire(t, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyEvent.VK_DOWN, menu);
        assertEquals(2, t.getSelectedIndex());
        // Plain arrows are deliberately NOT in the window map: a focused table must keep them.
        assertNull(t.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
    }

    @Test
    public void testNoSelectionGoesToFirst() {
        JTabbedPane t = tabs(3);
        t.setSelectedIndex(-1);
        fire(t, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_DOWN, 0);
        assertEquals(0, t.getSelectedIndex());
    }

    @Test
    public void testSingleTabIsInert() {
        JTabbedPane t = tabs(1);
        fire(t, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_DOWN, 0);
        assertEquals(0, t.getSelectedIndex());
    }

    @Test
    public void testComboStepFiresListeners() {
        JPanel host = new JPanel();
        JComboBox<Integer> combo = new JComboBox<>(new Integer[]{1, 2, 3});
        AtomicInteger seen = new AtomicInteger(-1);
        combo.addActionListener(e -> seen.set(combo.getSelectedIndex()));
        ArrowKeyNavigation.install(host, combo);

        combo.setSelectedIndex(0);
        fire(host, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_DOWN, 0);
        assertEquals(1, combo.getSelectedIndex());
        assertEquals("action listener saw the step", 1, seen.get());
        fire(host, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_UP, 0);
        fire(host, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, KeyEvent.VK_UP, 0);
        assertEquals("wraps", 2, combo.getSelectedIndex());
    }

    @Test
    public void testInstallIsIdempotent() {
        JTabbedPane t = tabs(2);
        Action first = t.getActionMap().get(t.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
        ArrowKeyNavigation.install(t);
        Action second = t.getActionMap().get(t.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
        assertSame(first, second);
    }

    public static void main(String[] args) {
        TestArrowKeyNavigation t = new TestArrowKeyNavigation();
        t.testTabsStepAndWrap();
        t.testModifiedArrowsBoundInWindowMap();
        t.testNoSelectionGoesToFirst();
        t.testSingleTabIsInert();
        t.testComboStepFiresListeners();
        t.testInstallIsIdempotent();
        System.out.println("All arrow-key navigation checks passed.");
    }
}
