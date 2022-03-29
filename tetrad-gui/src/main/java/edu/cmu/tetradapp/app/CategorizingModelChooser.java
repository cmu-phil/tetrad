///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.session.SessionNode;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Lets you choose models from a categorized list.
 *
 * @author Tyler Gibson
 */
public class CategorizingModelChooser extends JPanel implements ModelChooser {


    /**
     * The title of the chooser.
     */
    private String title;


    /**
     * The node name
     */
    private String nodeName;

    /**
     * The id for the node.
     */
    private String nodeId;


    /**
     * THe JTree used to display the options.
     */
    private JTree tree;

    /**
     * The session node for the getModel node.
     */
    private SessionNode sessionNode;

    //========================== public methods =========================//

    public String getTitle() {
        return this.title;
    }

    public void setTitle(final String title) {
        if (title == null) {
            throw new NullPointerException("The title must not be null");
        }
        this.title = title;
    }

    public Class getSelectedModel() {
        final TreePath path = this.tree.getSelectionPath();

        if (path == null) {
            throw new NullPointerException("I had a problem figuring out the models for this box given the parents. Maybe\n" +
                    "the parents are wrong, or maybe this isn't the box you were intending to use.");
        }

        final Object selected = path.getLastPathComponent();
        if (selected instanceof ModelWrapper) {
            return ((ModelWrapper) selected).model;
        }
        return null;
    }

    public void setModelConfigs(final List<SessionNodeModelConfig> configs) {
        final ChooserTreeModel model = new ChooserTreeModel(configs);
        this.tree = new JTree(model);
        this.tree.setCellRenderer(new ChooserRenderer());
        this.tree.setRootVisible(false);
        this.tree.setEditable(false);
        this.tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        for (int i = 0; i < this.tree.getRowCount(); i++) {
            this.tree.expandRow(i);
        }
        this.tree.addTreeSelectionListener(e -> {
            final TreePath path = e.getPath();
            final Object selected = path.getLastPathComponent();
            if (selected instanceof ModelWrapper) {
                final String name = ((ModelWrapper) selected).name;
                Preferences.userRoot().put(this.nodeId, name);
            }
        });

        // select a default value, if one exists
        String storedModelType = getModelTypeFromSessionNode(this.sessionNode, model);

        if (storedModelType == null) {
            storedModelType = Preferences.userRoot().get(this.nodeId, "");
        }

        System.out.println("Stored model type = " + storedModelType);


        if (storedModelType.length() != 0) {
            for (final Map.Entry<String, List<ModelWrapper>> entry : model.map.entrySet()) {
                for (final ModelWrapper wrapper : entry.getValue()) {
                    if (storedModelType.equals(wrapper.name)) {
                        final Object[] path = new Object[]{ChooserTreeModel.ROOT, entry.getKey(), wrapper};
                        this.tree.setSelectionPath(new TreePath(path));
                        break;
                    }
                }
            }
        }
    }

    private String getModelTypeFromSessionNode(final SessionNode sessionNode, final ChooserTreeModel model) {

        // Assumes the tree will always be of depth 2.
        final Class clazz = sessionNode.getLastModelClass();
        final Object root = model.getRoot();

        for (int i = 0; i < model.getChildCount(root); i++) {
            final Object child = model.getChild(root, i);

            for (int j = 0; j < model.getChildCount(child); j++) {
                final ModelWrapper modelWrapper = (ModelWrapper) model.getChild(child, j);
                assert modelWrapper != null;
                if (modelWrapper.model == clazz) {
                    return modelWrapper.name;
                }
            }
        }

        return null;
    }

    public void setNodeId(final String id) {
        if (id == null) {
            throw new NullPointerException("The given id must not be null");
        }
        this.nodeId = id;
    }

    public void setSessionNode(final SessionNode sessionNode) {
        this.sessionNode = sessionNode;
        this.nodeName = sessionNode.getDisplayName();
    }

    public void setup() {
        this.setLayout(new BorderLayout());

        final JButton info = new JButton("Help");

        info.addActionListener(e -> {
            final Class model = getSelectedModel();
            if (model == null) {
                JOptionPane.showMessageDialog(this, "No node selected. Select" +
                        " a node to get help for it.");
            } else {
                SessionUtils.showPermissibleParentsDialog(model,
                        this, false, false);
            }
        });

        final Box vBox = Box.createVerticalBox();
        vBox.add(Box.createVerticalStrut(5));

        final Box box = Box.createHorizontalBox();
        box.add(new JLabel(" Name of node: " + this.nodeName));
        box.add(Box.createHorizontalGlue());
        box.add(info);
        box.add(Box.createHorizontalStrut(5));

        vBox.add(box);
        vBox.add(Box.createVerticalStrut(5));

        this.add(vBox, BorderLayout.NORTH);
        this.add(new JScrollPane(this.tree), BorderLayout.CENTER);
    }

    //================================= Inner classes ============================//

    /**
     * Wraps a model class and the name.
     */
    private static class ModelWrapper {

        private final String name;
        private final Class model;

        public ModelWrapper(final String name, final Class model) {
            this.name = name;
            this.model = model;
        }

    }

    private static class ChooserRenderer extends DefaultTreeCellRenderer {


        public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean selected, final boolean expanded,
                                                      final boolean leaf, final int row, final boolean hasFocus) {

            if (value == null) {
                this.setText("");
            } else if (value instanceof ModelWrapper) {
                this.setText(((ModelWrapper) value).name);
            } else {
                this.setText((String) value);
            }

            if (leaf) {
                setIcon(null);
            } else if (expanded) {
                setIcon(getOpenIcon());
            } else {
                setIcon(getClosedIcon());
            }
            if (selected) {
                this.setForeground(this.getTextSelectionColor());
            } else {
                this.setForeground(this.getTextNonSelectionColor());
            }

            this.selected = selected;
            return this;
        }

    }


    /**
     * Model for the chooser's tree.
     */
    private static class ChooserTreeModel implements TreeModel {

        private static final String ROOT = "Root";

        private final Map<String, List<ModelWrapper>> map = new HashMap<>();

        private final List<String> categories = new LinkedList<>();

        public ChooserTreeModel(final List<SessionNodeModelConfig> configs) {
            for (final SessionNodeModelConfig config : configs) {
                final String category = config.getCategory();
                if (category == null) {
                    throw new NullPointerException("No Category name associated with model: " + config.getModel());
                }

                if ("Unlisted".equals(category)) {
                    continue;
                }

                if (!this.categories.contains(category)) {
                    this.categories.add(category);
                }

                final List<ModelWrapper> models = this.map.computeIfAbsent(category, k -> new LinkedList<>());
                models.add(new ModelWrapper(config.getName(), config.getModel()));
            }
        }

        public Object getRoot() {
            return ChooserTreeModel.ROOT;
        }

        public Object getChild(final Object parent, final int index) {
            if (ChooserTreeModel.ROOT.equals(parent)) {
                return this.categories.get(index);
            } else if (parent instanceof String) {
                final List<ModelWrapper> models = this.map.get(parent);
                return models.get(index);
            }
            return null;
        }

        public int getChildCount(final Object parent) {
            if (ChooserTreeModel.ROOT.equals(parent)) {
                return this.categories.size();
            } else if (parent instanceof ModelWrapper) {
                return 0;
            }
            final List<ModelWrapper> models = this.map.get(parent);
            return models.size();
        }

        public boolean isLeaf(final Object node) {
            return node instanceof ModelWrapper;
        }

        public void valueForPathChanged(final TreePath path, final Object newValue) {

        }

        public int getIndexOfChild(final Object parent, final Object child) {
            if (ChooserTreeModel.ROOT.equals(parent)) {
                return this.categories.indexOf(child);
            }
            final List<ModelWrapper> models = this.map.get(parent);
            return models.indexOf(child);
        }

        public void addTreeModelListener(final TreeModelListener l) {

        }

        public void removeTreeModelListener(final TreeModelListener l) {

        }
    }


}




