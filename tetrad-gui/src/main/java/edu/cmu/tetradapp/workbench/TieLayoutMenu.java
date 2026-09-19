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

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.app.SessionEditor;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.model.GraphSource;
import edu.cmu.tetradapp.model.MultipleGraphSource;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.session.SessionNode;
import edu.cmu.tetradapp.util.CopyLayoutAction;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.LayoutEditable;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * A submenu for the Layout menu that lets the user tie the layout of the current graph workbench to the layout of the
 * graph in another session node. Selecting a session node from the submenu immediately lays out the current graph using
 * the node positions of that node's graph (matching nodes by name), and the choice is remembered in the session, so
 * that the layout is reapplied from the reference node each time an editor for this session node is reopened.
 * Selecting "None" removes the tie.
 * <p>
 * Ties are stored in the session wrapper's attribute map under the key "layoutTies", as a map from the display name of
 * the tied session node to the display name of its reference session node. Since the attribute map is serialized with
 * the session, ties persist across save and load. Ties are keyed by display name, so renaming or deleting a session
 * node silently drops any tie involving it.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TieLayoutMenu extends JMenu {

    /**
     * The attribute key in the session wrapper under which layout ties are stored.
     */
    private static final String LAYOUT_TIES = "layoutTies";

    /**
     * Client property key on the editor window's root pane recording that the tie layout has already been applied for
     * this opening of the editor, so that reconstructing a LayoutMenu (e.g., for a right-click popup) does not undo
     * manual adjustments by reapplying the tie.
     */
    private static final String TIE_APPLIED = "tieLayoutApplied";

    /**
     * The layout editable object this menu lays out.
     */
    private final LayoutEditable layoutEditable;

    /**
     * Constructs the submenu for the given layout editable. The menu items are rebuilt each time the menu is selected,
     * so that the list of session nodes with graphs is current.
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public TieLayoutMenu(LayoutEditable layoutEditable) {
        super("Tie Layout To");

        if (layoutEditable == null) {
            throw new NullPointerException();
        }

        this.layoutEditable = layoutEditable;

        addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent e) {
                rebuild();
            }

            public void menuDeselected(MenuEvent e) {
            }

            public void menuCanceled(MenuEvent e) {
            }
        });

        // Placeholder so the submenu shows an arrow before it is first selected.
        rebuild();
    }

    /**
     * If a layout tie has been recorded in the session for the session node whose editor contains the given layout
     * editable, applies the layout of the reference node's graph to the layout editable. This is applied at most once
     * per opening of the editor window, and is a no-op if the layout editable is not inside an editor window, no tie
     * is recorded, or the reference node no longer exists or has no graph. Runs later on the event thread, since the
     * editor's name (from which the owning session node is identified) is set only after the editor is constructed.
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void applyTieOnOpen(LayoutEditable layoutEditable) {
        SwingUtilities.invokeLater(() -> {
            if (!(layoutEditable instanceof Component comp)) {
                return;
            }

            JInternalFrame frame = (JInternalFrame) SwingUtilities.getAncestorOfClass(JInternalFrame.class, comp);

            if (frame == null || frame.getRootPane() == null) {
                return;
            }

            JRootPane root = frame.getRootPane();

            if (Boolean.TRUE.equals(root.getClientProperty(TieLayoutMenu.TIE_APPLIED))) {
                return;
            }

            SessionWrapper session = TieLayoutMenu.getFrontmostSessionWrapper();

            if (session == null) {
                return;
            }

            String ownerName = TieLayoutMenu.getOwnerNodeName(comp, session);

            if (ownerName == null) {
                return;
            }

            Object attribute = session.getAttribute(TieLayoutMenu.LAYOUT_TIES);

            if (!(attribute instanceof Map<?, ?> ties)) {
                return;
            }

            Object refName = ties.get(ownerName);

            if (refName == null) {
                return;
            }

            SessionNodeWrapper refWrapper = TieLayoutMenu.findNodeByName(session, refName.toString());
            Graph refGraph = refWrapper == null ? null : TieLayoutMenu.graphOf(refWrapper);

            if (refGraph == null) {
                return;
            }

            layoutEditable.layoutByGraph(refGraph);
            root.putClientProperty(TieLayoutMenu.TIE_APPLIED, Boolean.TRUE);
        });
    }

    /**
     * @return the session wrapper of the frontmost session editor, or null if there is none (e.g., outside the Tetrad
     * desktop).
     */
    private static SessionWrapper getFrontmostSessionWrapper() {
        try {
            if (!(DesktopController.getInstance() instanceof TetradDesktop desktop)) {
                return null;
            }

            SessionEditor sessionEditor = desktop.getFrontmostSessionEditor();

            if (sessionEditor == null || sessionEditor.getSessionWorkbench() == null) {
                return null;
            }

            return sessionEditor.getSessionWorkbench().getSessionWrapper();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Identifies the session node whose editor contains the given component, by matching the title of the enclosing
     * editor window (of the form "NodeName (Model Description)") against the display names of the session nodes.
     *
     * @return the display name of the owning session node, or null if it cannot be determined.
     */
    private static String getOwnerNodeName(Component comp, SessionWrapper session) {
        JInternalFrame frame = (JInternalFrame) SwingUtilities.getAncestorOfClass(JInternalFrame.class, comp);

        if (frame == null || frame.getTitle() == null) {
            return null;
        }

        String title = frame.getTitle();
        String best = null;

        for (Node node : session.getNodes()) {
            if (!(node instanceof SessionNodeWrapper wrapper)) {
                continue;
            }

            String name = wrapper.getSessionName();

            if (name == null) {
                continue;
            }

            if ((title.equals(name) || title.startsWith(name + " ("))
                && (best == null || name.length() > best.length())) {
                best = name;
            }
        }

        return best;
    }

    /**
     * @return the session node wrapper in the given session with the given display name, or null if there is none.
     */
    private static SessionNodeWrapper findNodeByName(SessionWrapper session, String name) {
        for (Node node : session.getNodes()) {
            if (node instanceof SessionNodeWrapper wrapper && name.equals(wrapper.getSessionName())) {
                return wrapper;
            }
        }

        return null;
    }

    /**
     * @return a graph of the given session node's model for layout purposes, and null if the model supplies no
     * nonempty graph. For a model with multiple graphs (e.g., a Simulation with one graph per run), the first graph
     * is used; this is checked before GraphSource.getGraph(), since some models (Simulation among them) throw from
     * getGraph() when they hold more than one distinct graph. Layout is matched by node name, so when the graphs
     * share their node names, as simulation runs do, any of them gives the same layout.
     */
    private static Graph graphOf(SessionNodeWrapper wrapper) {
        SessionNode sessionNode = wrapper.getSessionNode();

        if (sessionNode == null) {
            return null;
        }

        SessionModel model = sessionNode.getModel();

        try {
            if (model instanceof MultipleGraphSource multi) {
                List<Graph> graphs = multi.getGraphs();

                if (graphs != null && !graphs.isEmpty()) {
                    Graph graph = graphs.getFirst();
                    return graph == null || graph.getNumNodes() == 0 ? null : graph;
                }
            }

            if (model instanceof GraphSource source) {
                Graph graph = source.getGraph();
                return graph == null || graph.getNumNodes() == 0 ? null : graph;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return the mutable map of layout ties stored in the given session wrapper, creating and storing it if
     * necessary. The map is from tied node display names to reference node display names.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> getLayoutTies(SessionWrapper session) {
        Object attribute = session.getAttribute(TieLayoutMenu.LAYOUT_TIES);

        if (attribute instanceof Map) {
            return (Map<String, String>) attribute;
        }

        Map<String, String> ties = new HashMap<>();
        session.addAttribute(TieLayoutMenu.LAYOUT_TIES, ties);
        return ties;
    }

    /**
     * Rebuilds the menu items from the current state of the frontmost session: a "None" item plus one radio button
     * item for each other session node whose model supplies a graph.
     */
    private void rebuild() {
        removeAll();

        SessionWrapper session = TieLayoutMenu.getFrontmostSessionWrapper();

        if (session == null) {
            JMenuItem item = new JMenuItem("(No session available)");
            item.setEnabled(false);
            add(item);
            return;
        }

        String ownerName = this.layoutEditable instanceof Component comp
                ? TieLayoutMenu.getOwnerNodeName(comp, session) : null;

        List<SessionNodeWrapper> candidates = new ArrayList<>();

        for (Node node : session.getNodes()) {
            if (!(node instanceof SessionNodeWrapper wrapper)) {
                continue;
            }

            String name = wrapper.getSessionName();

            if (name == null || name.equals(ownerName)) {
                continue;
            }

            if (TieLayoutMenu.graphOf(wrapper) != null) {
                candidates.add(wrapper);
            }
        }

        candidates.sort(Comparator.comparing(SessionNodeWrapper::getSessionName));

        String currentRef = null;

        if (ownerName != null) {
            Object attribute = session.getAttribute(TieLayoutMenu.LAYOUT_TIES);

            if (attribute instanceof Map<?, ?> ties) {
                Object ref = ties.get(ownerName);
                currentRef = ref == null ? null : ref.toString();
            }
        }

        // Drop a stale tie whose reference node no longer exists or no longer has a graph.
        if (currentRef != null) {
            String finalCurrentRef = currentRef;

            if (candidates.stream().noneMatch(w -> finalCurrentRef.equals(w.getSessionName()))) {
                TieLayoutMenu.getLayoutTies(session).remove(ownerName);
                session.setSessionChanged(true);
                currentRef = null;
            }
        }

        ButtonGroup group = new ButtonGroup();
        String finalOwnerName = ownerName;

        JRadioButtonMenuItem none = new JRadioButtonMenuItem("None");
        none.setSelected(currentRef == null);
        group.add(none);
        add(none);

        none.addActionListener(e -> {
            if (finalOwnerName != null) {
                TieLayoutMenu.getLayoutTies(session).remove(finalOwnerName);
                session.setSessionChanged(true);
            }
        });

        if (candidates.isEmpty()) {
            JMenuItem item = new JMenuItem("(No other session nodes with graphs)");
            item.setEnabled(false);
            add(item);
            return;
        }

        for (SessionNodeWrapper wrapper : candidates) {
            String refName = wrapper.getSessionName();

            JRadioButtonMenuItem item = new JRadioButtonMenuItem(refName);
            item.setSelected(refName.equals(currentRef));
            group.add(item);
            add(item);

            item.addActionListener(e -> {
                if (finalOwnerName != null) {
                    TieLayoutMenu.getLayoutTies(session).put(finalOwnerName, refName);
                    session.setSessionChanged(true);
                }

                Graph refGraph = TieLayoutMenu.graphOf(wrapper);

                if (refGraph != null) {
                    this.layoutEditable.layoutByGraph(refGraph);

                    // Copy the laid out graph to the clipboard, as the other layout menu items do.
                    new CopyLayoutAction(this.layoutEditable).actionPerformed(null);
                }
            });
        }
    }
}
