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

import edu.cmu.tetradapp.model.UnlistedSessionModel;
import edu.cmu.tetradapp.session.SessionNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * THe default model chooser.
 *
 * @author Tyler Gibson
 */
class DefaultModelChooser extends JComponent implements ModelChooser {

    /**
     * Combo box used to allow user to select a model.
     */
    private JComboBox modelClassesBox;


    /**
     * The title of the chooser;
     */
    private String title;


    /**
     * THe name of the node
     */
    private String nodeName;


    /**
     * The id for the node, that this is a chooser for.
     */
    private String id;


    /**
     * Constructs the chooser.
     */
    public DefaultModelChooser() {

    }

    /**
     * <p>Getter for the field <code>title</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Sets the title to be used for the chooser.
     *
     * @param title The title to use for the chooser.
     * @throws NullPointerException if the given title is null.
     */
    public void setTitle(String title) {
        if (title == null) {
            throw new NullPointerException("The given title must not be null");
        }
        this.title = title;
    }

    /**
     * <p>getSelectedModel.</p>
     *
     * @return a {@link java.lang.Class<?>} object
     */
    public Class<?> getSelectedModel() {
        ClassWrapper wrapper = (ClassWrapper) this.modelClassesBox.getSelectedItem();
        return wrapper.getWrappedClass();
    }

    /**
     * Sets the model configurations for the chooser.
     *
     * @param configs the models that this chooser should display
     */
    public void setModelConfigs(List<SessionNodeModelConfig> configs) {
        List<ClassWrapper> wrapperList = new LinkedList<>();

        for (SessionNodeModelConfig config : configs) {
            Class<?> modelClass = config.model();
            if (!(UnlistedSessionModel.class.isAssignableFrom(modelClass))) {
                wrapperList.add(new ClassWrapper(modelClass, config.name()));
            }
        }

        ClassWrapper[] wrappers = wrapperList.toArray(new ClassWrapper[0]);
        this.modelClassesBox = new JComboBox(wrappers);

        this.modelClassesBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    int selectedIndex = DefaultModelChooser.this.modelClassesBox.getSelectedIndex();
                    String selectedModelType = DefaultModelChooser.this.modelClassesBox.getItemAt(
                            selectedIndex).toString();
                    Preferences.userRoot().put(DefaultModelChooser.this.id, selectedModelType);
                }
            }
        });

        String storedModelType = Preferences.userRoot().get(this.id, "");
        for (int i = 0; i < this.modelClassesBox.getItemCount(); i++) {
            String currModelType = this.modelClassesBox.getItemAt(i).toString();
            if (storedModelType.equals(currModelType)) {
                this.modelClassesBox.setSelectedIndex(i);
            }
        }
    }

    /**
     * Sets the id for the node.
     *
     * @param id the id for the node. Cannot be null.
     * @throws NullPointerException if the given id is null.
     */
    public void setNodeId(String id) {
        if (id == null) {
            throw new NullPointerException("The given id must not be null");
        }
        this.id = id;
    }

    /**
     * Sets the SessionNode for the getModel node.
     *
     * @param sessionNode the SessionNode for the getModel node.
     */
    public void setSessionNode(SessionNode sessionNode) {
        /*(
      The session node for the getModel node.
     */
        this.nodeName = sessionNode.getDisplayName();
    }

    /**
     * <p>setup.</p>
     */
    public void setup() {
        JButton info = new JButton("Help");

        info.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SessionUtils.showPermissibleParentsDialog(getSelectedModel(),
                        DefaultModelChooser.this.modelClassesBox, false, false);
            }
        });

        JLabel l1 = new JLabel("Node name: " + this.nodeName);
        l1.setForeground(Color.black);

        setLayout(new BorderLayout());

        Box b1 = Box.createVerticalBox();
        Box b2 = Box.createHorizontalBox();

        b2.add(l1);
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        Box b3 = Box.createHorizontalBox();

        b3.add(this.modelClassesBox);
        Font font = this.modelClassesBox.getFont();

        l1.setFont(font);
        b3.add(Box.createGlue());
        b3.add(info);
        b1.add(b3);
        add(b1, BorderLayout.CENTER);
    }

    /**
     * Basic wrapper class.
     */
    private static final class ClassWrapper {
        private final Class<?> wrappedClass;
        private final String name;

        public ClassWrapper(Class<?> wrappedClass, String name) {
            this.wrappedClass = wrappedClass;
            this.name = name;
        }

        public Class<?> getWrappedClass() {
            return this.wrappedClass;
        }

        public String toString() {
            return this.name;
        }
    }
}





