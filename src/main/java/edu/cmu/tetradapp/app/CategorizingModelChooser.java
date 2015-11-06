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
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    public Class getSelectedModel() {
        TreePath path = this.tree.getSelectionPath();
        Object selected = path.getLastPathComponent();
        if (selected instanceof ModelWrapper) {
            return ((ModelWrapper) selected).model;
        }
        return null;
    }

    public void setTitle(String title) {
        if (title == null) {
            throw new NullPointerException("The title must not be null");
        }
        this.title = title;
    }

    public void setModelConfigs(List<SessionNodeModelConfig> configs) {
        ChooserTreeModel model = new ChooserTreeModel(configs);
        this.tree = new JTree(model);
        this.tree.setCellRenderer(new ChooserRenderer());
        this.tree.setRootVisible(false);
        this.tree.setEditable(false);
        this.tree.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        for (int i = 0; i < this.tree.getRowCount(); i++) {
            this.tree.expandRow(i);
        }
        this.tree.addTreeSelectionListener(new TreeSelectionListener(){
            public void valueChanged(TreeSelectionEvent e) {
                TreePath path = e.getPath();
                Object selected = path.getLastPathComponent();
                if(selected instanceof ModelWrapper){
                    String name = ((ModelWrapper)selected).name;
                    Preferences.userRoot().put(nodeId, name);
                }
            }
        });

        // select a default value, if one exists
        String storedModelType = getModelTypeFromSessionNode(sessionNode, model);

        if (storedModelType == null) {
            storedModelType = Preferences.userRoot().get(this.nodeId, "");
        }

        System.out.println("Stored model type = " + storedModelType);


        if (storedModelType.length() != 0) {
            for (Map.Entry<String, List<ModelWrapper>> entry : model.map.entrySet()) {
                for(ModelWrapper wrapper : entry.getValue()){
                    if(storedModelType.equals(wrapper.name)){
                        Object[] path = new Object[]{ChooserTreeModel.ROOT, entry.getKey(), wrapper};
                        this.tree.setSelectionPath(new TreePath(path));
                        break;
                    }
                }
            }
        }
    }

    private String getModelTypeFromSessionNode(SessionNode sessionNode, ChooserTreeModel model) {

        // Assumes the tree will always be of depth 2.
        Class clazz = sessionNode.getLastModelClass();
        Object root = model.getRoot();

        for (int i = 0; i < model.getChildCount(root); i++) {
            Object child = model.getChild(root, i);

            for (int j = 0; j < model.getChildCount(child); j++) {
                ModelWrapper modelWrapper = (ModelWrapper) model.getChild(child, j);
                if (modelWrapper.model == clazz) {
                    return modelWrapper.name;
                }
            }
        }

        return null;
    }

    public void setNodeId(String id) {
        if (id == null) {
            throw new NullPointerException("The given id must not be null");
        }
        this.nodeId = id;
    }

    public void setSessionNode(SessionNode sessionNode) {
        this.sessionNode = sessionNode;
        this.nodeName = sessionNode.getDisplayName();
    }

    public void setup() {
        this.setLayout(new BorderLayout());

        JButton info = new JButton("Help");

        info.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Class model = getSelectedModel();
                if (model == null) {
                    JOptionPane.showMessageDialog(CategorizingModelChooser.this, "No node selected. Select" +
                            " a node to get help for it.");
                } else {
                    SessionUtils.showPermissibleParentsDialog(model,
                            CategorizingModelChooser.this, false, false);
                }
            }
        });

        Box vBox = Box.createVerticalBox();
        vBox.add(Box.createVerticalStrut(5));

        Box box = Box.createHorizontalBox();
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

        private String name;
        private Class model;

        public ModelWrapper(String name, Class model) {
            this.name = name;
            this.model = model;
        }

    }

    private static class ChooserRenderer extends DefaultTreeCellRenderer {


        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {

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

        private Map<String, List<ModelWrapper>> map = new HashMap<String, List<ModelWrapper>>();

        private List<String> categories = new LinkedList<String>();

        public ChooserTreeModel(List<SessionNodeModelConfig> configs) {
            for (SessionNodeModelConfig config : configs) {
                String category = config.getCategory();
                if (category == null) {
                    throw new NullPointerException("No Category name associated with model: " + config.getModel());
                }

                if ("Unlisted".equals(category)) {
                    continue;
                }

                if (!this.categories.contains(category)) {
                    this.categories.add(category);
                }
                
                List<ModelWrapper> models = map.get(category);
                if (models == null) {
                    models = new LinkedList<ModelWrapper>();
                    map.put(category, models);
                }
                models.add(new ModelWrapper(config.getName(), config.getModel()));
            }
        }

        public Object getRoot() {
            return ROOT;
        }

        public Object getChild(Object parent, int index) {
            if (ROOT.equals(parent)) {
                return this.categories.get(index);
            } else if (parent instanceof String) {
                List<ModelWrapper> models = this.map.get(parent);
                return models.get(index);
            }
            return null;
        }

        public int getChildCount(Object parent) {
            if (ROOT.equals(parent)) {
                return this.categories.size();
            } else if (parent instanceof ModelWrapper) {
                return 0;
            }
            List<ModelWrapper> models = this.map.get(parent);
            return models.size();
        }

        public boolean isLeaf(Object node) {
            return node instanceof ModelWrapper;
        }

        public void valueForPathChanged(TreePath path, Object newValue) {

        }

        public int getIndexOfChild(Object parent, Object child) {
            if (ROOT.equals(parent)) {
                return this.categories.indexOf(child);
            }
            List<ModelWrapper> models = this.map.get(parent);
            return models.indexOf(child);
        }

        public void addTreeModelListener(TreeModelListener l) {

        }

        public void removeTreeModelListener(TreeModelListener l) {

        }
    }


}




