///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Binds the up and down arrow keys to step through a selector that switches between several
 * data sets or graphs: the left-hand tab strip of a multi-data-set data editor, a simulation's
 * true graphs, or a search's per-data-set results, and the "Using model N" combo box of the
 * graph and model editors.
 * <p>
 * Two bindings are installed for each direction. The plain arrow key is bound in the
 * WHEN_ANCESTOR_OF_FOCUSED_COMPONENT map of the host, so it acts whenever keyboard focus is
 * somewhere inside the host that does not itself consume arrow keys - a graph workbench, for
 * instance. Components that do consume them keep them: a focused JTable uses the arrows for
 * cell navigation and its own bindings are consulted first. For those cases the arrow with the
 * platform menu shortcut modifier (Ctrl on Windows and Linux, Cmd on macOS) is bound in the
 * WHEN_IN_FOCUSED_WINDOW map, so Ctrl/Cmd + arrow steps the selection from anywhere in the
 * window while the host is showing, including from inside a data table.
 * <p>
 * Selection wraps at the ends. Installation is idempotent per host.
 *
 * @author josephramsey
 */
public final class ArrowKeyNavigation {

    private static final String INSTALLED = "edu.cmu.tetradapp.util.ArrowKeyNavigation.installed";
    private static final String UP = "ArrowKeyNavigation.up";
    private static final String DOWN = "ArrowKeyNavigation.down";

    private ArrowKeyNavigation() {
    }

    /**
     * Binds the arrow keys to step the selected tab of the given tabbed pane. The pane is its
     * own host: the plain arrows act when focus is inside one of its tabs.
     *
     * @param tabs the tabbed pane whose tabs are the alternatives.
     */
    public static void install(JTabbedPane tabs) {
        install(tabs, tabs::getTabCount, tabs::getSelectedIndex, tabs::setSelectedIndex);
    }

    /**
     * Binds the arrow keys, on the given host, to step the selected item of the given combo
     * box, firing its action listeners exactly as a mouse selection would.
     *
     * @param host  the component inside which the plain arrows should act; must be an ancestor
     *              of the area that takes focus (typically the editor panel).
     * @param combo the combo box whose items are the alternatives.
     */
    public static void install(JComponent host, JComboBox<?> combo) {
        install(host, combo::getItemCount, combo::getSelectedIndex, combo::setSelectedIndex);
    }

    private static void install(JComponent host, java.util.function.IntSupplier count,
                                java.util.function.IntSupplier selected,
                                java.util.function.IntConsumer select) {
        if (Boolean.TRUE.equals(host.getClientProperty(INSTALLED))) return;
        host.putClientProperty(INSTALLED, Boolean.TRUE);

        Action up = step(count, selected, select, -1);
        Action down = step(count, selected, select, +1);

        ActionMap actions = host.getActionMap();
        actions.put(UP, up);
        actions.put(DOWN, down);

        InputMap ancestor = host.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ancestor.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), UP);
        ancestor.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), DOWN);

        int menu;
        try {
            menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException e) {
            menu = KeyEvent.CTRL_DOWN_MASK;
        }
        InputMap window = host.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        window.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, menu), UP);
        window.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, menu), DOWN);
    }

    private static Action step(java.util.function.IntSupplier count,
                               java.util.function.IntSupplier selected,
                               java.util.function.IntConsumer select, int delta) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int n = count.getAsInt();
                if (n < 2) return;
                int i = selected.getAsInt();
                int next = i < 0 ? 0 : Math.floorMod(i + delta, n);
                if (next != i) select.accept(next);
            }
        };
    }
}
