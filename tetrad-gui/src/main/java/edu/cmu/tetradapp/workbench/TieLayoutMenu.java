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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
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
 * A submenu for the Layout menu that says which session node the graph in the current workbench inherits its layout
 * from, its <i>reference</i>. By default the reference is the nearest session node upstream of this one that has a
 * graph sharing a node name with this one, so that a box starts with, and keeps following, the layout of its parent.
 * The user may instead pick any other session node with a graph, or "None" to inherit from nothing.
 * <p>
 * Inheriting does not undo what is done locally. Each time an editor for this session node is opened, and each time
 * a search puts a new result in it, the nodes that have been moved in this box since the last time (dragged, laid
 * out, pasted, put back by an undo) stay where they are, and the rest take the positions the reference now has,
 * matched by name; see {@link LayoutInheritance}. "Reset to Inherited Layout" discards the local changes. The
 * reference's positions are themselves resolved this way, so a box follows a layout changed two boxes upstream even
 * if the box in between has not been opened since.
 * <p>
 * Two maps are stored in the session wrapper's attribute map, and so are saved with the session. "layoutTies" maps
 * the display name of a session node to the display name of the reference chosen for it, or to the empty string for
 * "None"; a node with no entry uses the default. "layoutInherited" maps the display name of a session node to the
 * positions its nodes were given when it was last synchronized, which is what tells a local change from an inherited
 * position. Both are keyed by display name, so renaming a session node drops its entries: it goes back to the
 * default reference, and the next synchronization gives it the whole layout of that reference.
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
     * The attribute key in the session wrapper under which the positions given to the nodes of each session node's
     * graph at its last synchronization are stored.
     */
    private static final String LAYOUT_RECORDS = "layoutInherited";

    /**
     * The reference recorded for a session node that is to inherit its layout from nothing. No session node has the
     * empty string for a display name.
     */
    private static final String NO_TIE = "";

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
        super("Inherit Layout From");

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
     * Synchronizes the layout of the given layout editable with the layout of the reference of the session node
     * whose editor contains it, keeping the local changes (see the class comment). This is done at most once per
     * opening of the editor window, and is a no-op if the layout editable is not inside an editor window or the
     * session node has no reference. Runs later on the event thread, since the editor's name (from which the owning
     * session node is identified) is set only after the editor is constructed.
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void applyTieOnOpen(LayoutEditable layoutEditable) {
        TieLayoutMenu.applyTie(layoutEditable, false, false);
    }

    /**
     * Synchronizes the layout of the given layout editable with its reference even if that has already been done for
     * this opening of the editor window. This is for a workbench newly created for a search result while the editor
     * stays open (the once-per-opening guard has already fired for the window, but the workbench and its graph are
     * new), so that a finished search is laid out by its reference node without the user asking for it. The guard is
     * set afterwards, so constructing a LayoutMenu later does not synchronize again.
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void reapplyTie(LayoutEditable layoutEditable) {
        TieLayoutMenu.applyTie(layoutEditable, true, false);
    }

    /**
     * As {@link #reapplyTie(LayoutEditable)}, for a workbench newly created for a search result, saying whether the
     * result arrived without a layout. The layout record is kept by session node name, so it outlives the graph it
     * was made for. A result that arrives unpositioned is a new graph that the workbench has just laid out by
     * default; measured against the record of an earlier result, every one of its nodes would look as if it had
     * been moved locally, and none would take the reference's position. Such a result has no local changes to
     * keep, so the old record is discarded and it takes the whole layout of its reference.
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     * @param unpositioned   True if the graph had no layout when it was given to the workbench.
     */
    public static void reapplyTie(LayoutEditable layoutEditable, boolean unpositioned) {
        TieLayoutMenu.applyTie(layoutEditable, true, unpositioned);
    }

    private static void applyTie(LayoutEditable layoutEditable, boolean force, boolean reset) {
        SwingUtilities.invokeLater(() -> {
            if (!(layoutEditable instanceof Component comp)) {
                return;
            }

            JInternalFrame frame = (JInternalFrame) SwingUtilities.getAncestorOfClass(JInternalFrame.class, comp);

            if (frame == null || frame.getRootPane() == null) {
                return;
            }

            JRootPane root = frame.getRootPane();

            if (!force && Boolean.TRUE.equals(root.getClientProperty(TieLayoutMenu.TIE_APPLIED))) {
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

            TieLayoutMenu.synchronize(layoutEditable, session, ownerName, reset);
            root.putClientProperty(TieLayoutMenu.TIE_APPLIED, Boolean.TRUE);
        });
    }

    /**
     * Synchronizes the layout of the given layout editable, which shows the graph of the session node with the given
     * display name, with the layout of that session node's reference: the nodes not moved locally since the last
     * synchronization take the reference's positions, and the rest stay where they are. Does nothing if the session
     * node has no reference.
     *
     * @param reset True to discard the local changes first, so that every node the reference has takes its position.
     */
    private static void synchronize(LayoutEditable layoutEditable, SessionWrapper session, String ownerName,
                                    boolean reset) {
        Map<String, Map<String, int[]>> records = TieLayoutMenu.getLayoutRecords(session);
        SessionNodeWrapper owner = TieLayoutMenu.findNodeByName(session, ownerName);
        Graph graph = layoutEditable.getGraph();

        if (owner == null || graph == null) {
            return;
        }

        SessionNodeWrapper refWrapper = TieLayoutMenu.referenceOf(session, owner, graph);

        if (refWrapper == null) {
            return;
        }

        Set<String> visited = new HashSet<>();
        visited.add(ownerName);
        Map<String, Point> reference = TieLayoutMenu.effectivePositions(session, refWrapper, visited);

        Map<String, int[]> record = reset ? null : records.get(ownerName);
        Map<String, Point> before = LayoutInheritance.positionsOf(graph);
        Map<String, Point> resolved = LayoutInheritance.resolve(before, reference, record);
        boolean moved = !resolved.equals(before);

        if (moved) {
            Graph layoutGraph = new EdgeListGraph();

            for (Map.Entry<String, Point> entry : resolved.entrySet()) {
                Node node = new GraphNode(entry.getKey());
                node.setCenter(entry.getValue().x, entry.getValue().y);
                layoutGraph.addNode(node);
            }

            layoutEditable.layoutByGraph(layoutGraph);
        }

        // Read back rather than recording the resolved positions: the workbench moves overlapping nodes apart.
        Map<String, Point> after = LayoutInheritance.positionsOf(layoutEditable.getGraph());
        boolean firstTime = !records.containsKey(ownerName);
        records.put(ownerName, LayoutInheritance.updatedRecord(before, after, reference, record));

        if (moved || reset || firstTime) {
            session.setSessionChanged(true);
        }
    }

    /**
     * @return the session node the given session node inherits its layout from, or null if there is none: the one
     * chosen for it, if it still exists and has a graph, and otherwise, unless "None" was chosen, the nearest session
     * node upstream with a graph sharing a node name with the given graph.
     */
    private static SessionNodeWrapper referenceOf(SessionWrapper session, SessionNodeWrapper wrapper, Graph graph) {
        Object tie = null;

        if (session.getAttribute(TieLayoutMenu.LAYOUT_TIES) instanceof Map<?, ?> ties) {
            tie = ties.get(wrapper.getSessionName());
        }

        if (TieLayoutMenu.NO_TIE.equals(tie)) {
            return null;
        }

        if (tie != null) {
            SessionNodeWrapper chosen = TieLayoutMenu.findNodeByName(session, tie.toString());

            if (chosen != null && chosen != wrapper && TieLayoutMenu.graphOf(chosen) != null) {
                return chosen;
            }
        }

        return TieLayoutMenu.nearestAncestorWithGraph(session, wrapper, graph);
    }

    /**
     * @return the nearest session node upstream of the given one whose graph shares a node name with the given
     * graph, or null if there is none. Parents are considered before grandparents, and so on; among session nodes
     * at the same distance, the one sharing the most node names is taken, and among those the first by display name.
     */
    private static SessionNodeWrapper nearestAncestorWithGraph(SessionWrapper session, SessionNodeWrapper wrapper,
                                                               Graph graph) {
        if (wrapper.getSessionNode() == null) {
            return null;
        }

        Map<SessionNode, SessionNodeWrapper> wrappers = new IdentityHashMap<>();

        for (Node node : session.getNodes()) {
            if (node instanceof SessionNodeWrapper w && w.getSessionNode() != null) {
                wrappers.put(w.getSessionNode(), w);
            }
        }

        Set<String> names = new HashSet<>(graph.getNodeNames());
        Set<SessionNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(wrapper.getSessionNode());
        List<SessionNode> level = new ArrayList<>();

        for (SessionNode parent : wrapper.getSessionNode().getParents()) {
            if (visited.add(parent)) level.add(parent);
        }

        while (!level.isEmpty()) {
            SessionNodeWrapper best = null;
            int bestCount = 0;

            for (SessionNode sessionNode : level) {
                SessionNodeWrapper candidate = wrappers.get(sessionNode);
                Graph candidateGraph = candidate == null ? null : TieLayoutMenu.graphOf(candidate);

                if (candidateGraph == null || candidate.getSessionName() == null) {
                    continue;
                }

                int count = 0;

                for (String name : candidateGraph.getNodeNames()) {
                    if (names.contains(name)) count++;
                }

                if (count > bestCount || (count > 0 && count == bestCount
                                          && candidate.getSessionName().compareTo(best.getSessionName()) < 0)) {
                    best = candidate;
                    bestCount = count;
                }
            }

            if (best != null) {
                return best;
            }

            List<SessionNode> next = new ArrayList<>();

            for (SessionNode sessionNode : level) {
                for (SessionNode parent : sessionNode.getParents()) {
                    if (visited.add(parent)) next.add(parent);
                }
            }

            level = next;
        }

        return null;
    }

    /**
     * @param visited the display names of the session nodes already on the chain being resolved, to stop at a cycle
     *                of chosen references.
     * @return the positions the nodes of the given session node's graph would have if it were synchronized with its
     * reference now, by node name. Nothing is changed; this is what lets a layout be inherited through a session
     * node that has not been opened since the layout upstream of it changed.
     */
    private static Map<String, Point> effectivePositions(SessionWrapper session, SessionNodeWrapper wrapper,
                                                         Set<String> visited) {
        Graph graph = TieLayoutMenu.graphOf(wrapper);

        if (graph == null) {
            return new HashMap<>();
        }

        Map<String, Point> own = LayoutInheritance.positionsOf(graph);
        String name = wrapper.getSessionName();

        if (name == null || !visited.add(name)) {
            return own;
        }

        SessionNodeWrapper refWrapper = TieLayoutMenu.referenceOf(session, wrapper, graph);

        if (refWrapper == null) {
            return own;
        }

        Map<String, int[]> record = null;

        if (session.getAttribute(TieLayoutMenu.LAYOUT_RECORDS) instanceof Map<?, ?> records
            && records.get(name) instanceof Map<?, ?> found) {
            @SuppressWarnings("unchecked") Map<String, int[]> _record = (Map<String, int[]>) found;
            record = _record;
        }

        return LayoutInheritance.resolve(own, TieLayoutMenu.effectivePositions(session, refWrapper, visited), record);
    }

    /**
     * @return the mutable map of layout records stored in the given session wrapper, from session node display
     * names to the positions their nodes were given at their last synchronization, creating and storing it if
     * necessary. When it is created for a session that was loaded from a file, which is to say one saved before
     * layouts were inherited, every session node is given the record that leaves its layout as it is (see
     * {@link LayoutInheritance#recordAsIs(Map, Map)}), so that opening an old session does not rearrange it.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, int[]>> getLayoutRecords(SessionWrapper session) {
        Object attribute = session.getAttribute(TieLayoutMenu.LAYOUT_RECORDS);

        if (attribute instanceof Map) {
            return (Map<String, Map<String, int[]>>) attribute;
        }

        Map<String, Map<String, int[]>> records = new HashMap<>();

        if (!session.isNewSession()) {
            for (Node node : session.getNodes()) {
                if (!(node instanceof SessionNodeWrapper wrapper) || wrapper.getSessionName() == null) {
                    continue;
                }

                Graph graph = TieLayoutMenu.graphOf(wrapper);
                SessionNodeWrapper refWrapper = graph == null ? null
                        : TieLayoutMenu.referenceOf(session, wrapper, graph);
                Graph refGraph = refWrapper == null ? null : TieLayoutMenu.graphOf(refWrapper);

                if (refGraph != null) {
                    records.put(wrapper.getSessionName(), LayoutInheritance.recordAsIs(
                            LayoutInheritance.positionsOf(graph), LayoutInheritance.positionsOf(refGraph)));
                }
            }
        }

        session.addAttribute(TieLayoutMenu.LAYOUT_RECORDS, records);
        return records;
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
        if (currentRef != null && !TieLayoutMenu.NO_TIE.equals(currentRef)) {
            String finalCurrentRef = currentRef;

            if (candidates.stream().noneMatch(w -> finalCurrentRef.equals(w.getSessionName()))) {
                TieLayoutMenu.getLayoutTies(session).remove(ownerName);
                session.setSessionChanged(true);
                currentRef = null;
            }
        }

        ButtonGroup group = new ButtonGroup();
        String finalOwnerName = ownerName;

        // The default: the nearest session node upstream with a graph, named here so the user can see which it is.
        SessionNodeWrapper owner = ownerName == null ? null : TieLayoutMenu.findNodeByName(session, ownerName);
        Graph ownGraph = this.layoutEditable.getGraph();
        SessionNodeWrapper parent = owner == null || ownGraph == null ? null
                : TieLayoutMenu.nearestAncestorWithGraph(session, owner, ownGraph);

        JRadioButtonMenuItem automatic = new JRadioButtonMenuItem(parent == null
                ? "Parent Box (none has a graph)" : "Parent Box (" + parent.getSessionName() + ")");
        automatic.setSelected(currentRef == null);
        group.add(automatic);
        add(automatic);

        automatic.addActionListener(e -> {
            if (finalOwnerName != null) {
                TieLayoutMenu.getLayoutTies(session).remove(finalOwnerName);
                session.setSessionChanged(true);
                resetToInherited(session, finalOwnerName);
            }
        });

        JRadioButtonMenuItem none = new JRadioButtonMenuItem("None");
        none.setSelected(TieLayoutMenu.NO_TIE.equals(currentRef));
        group.add(none);
        add(none);

        none.addActionListener(e -> {
            if (finalOwnerName != null) {
                TieLayoutMenu.getLayoutTies(session).put(finalOwnerName, TieLayoutMenu.NO_TIE);
                session.setSessionChanged(true);
            }
        });

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
                    resetToInherited(session, finalOwnerName);
                } else {
                    // The session node this editor belongs to could not be identified, so nothing can be
                    // recorded for it; just take the layout.
                    Graph refGraph = TieLayoutMenu.graphOf(wrapper);

                    if (refGraph != null) {
                        this.layoutEditable.layoutByGraph(refGraph);
                        new CopyLayoutAction(this.layoutEditable).actionPerformed(null);
                    }
                }
            });
        }

        addSeparator();

        JMenuItem reset = new JMenuItem("Reset to Inherited Layout");
        reset.setToolTipText("Discards the layout changes made in this box and takes the layout of the box it"
                             + " inherits from");
        reset.setEnabled(finalOwnerName != null && !TieLayoutMenu.NO_TIE.equals(currentRef)
                         && (currentRef != null || parent != null));
        add(reset);

        reset.addActionListener(e -> resetToInherited(session, finalOwnerName));
    }

    /**
     * Gives this menu's layout editable the layout of its reference, discarding the local changes.
     */
    private void resetToInherited(SessionWrapper session, String ownerName) {
        TieLayoutMenu.synchronize(this.layoutEditable, session, ownerName, true);

        // Copy the laid out graph to the clipboard, as the other layout menu items do.
        new CopyLayoutAction(this.layoutEditable).actionPerformed(null);
    }
}
