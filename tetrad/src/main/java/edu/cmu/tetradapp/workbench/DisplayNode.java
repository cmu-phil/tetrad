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

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.data.AbstractVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.TetradSerializableExcluded;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * This class represents a node in a workbench; it is an abstract class, but
 * extensions of it represent measured and latent variables.
 *
 * @author Joseph Ramsey
 */
public class DisplayNode extends JComponent implements Node, TetradSerializableExcluded {

    /**
     * The model node which this display node depicts.
     */
    private Node modelNode;

    /**
     * True iff this display node is selected.
     */
    private boolean selected = false;

    /**
     * The component that displays.
     */
    private DisplayComp displayComp;
    private int uniqueId = AbstractVariable.LAST_ID++;

    //===========================CONSTRUCTORS==============================//

    public DisplayNode() {
        setName("");
    }

    //===========================PUBLIC METHODS============================//

    public final void setModelNode(Node modelNode) {
        if (modelNode == null) {
            throw new NullPointerException();
        }

        this.modelNode = modelNode;
        setName(modelNode.getName());

        modelNode.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("name".equals(evt.getPropertyName())) {
                    setName((String) (evt.getNewValue()));
                }
            }
        });
    }

    /**
     * @return the model node corresponding to this workbench node. May be
     * null.
     */
    public final Node getModelNode() {
        return modelNode;
    }

    /**
     * Sets the name of the node.
     */
    public final void setName(String name) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }

        super.setName(name);

        if (displayComp != null) {
            displayComp.setName(name);
        }

        repaint();
    }

    /**
     * Sets the selection status of the node.
     */
    public void setSelected(boolean selected) {
        boolean oldSelected = this.selected;
        this.selected = selected;
        firePropertyChange("selected", oldSelected, selected);

        if (displayComp != null) {
            displayComp.setSelected(selected);
        }

        repaint();
    }

    /**
     * @return true if the node is selected, false if not.
     */
    public final boolean isSelected() {
        return this.selected;
    }

    public final void setLocation(int x, int y) {
        super.setLocation(x, y);

        if (getModelNode() != null) {
            getModelNode().setCenter(x + getWidth() / 2, y + getHeight() / 2);
        }
    }

    /**
     * @return the center point for this node.
     */
    public final Point getCenterPoint() {
        Rectangle bounds = getBounds();
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        return new Point(centerX, centerY);
    }

    public boolean contains(int x, int y) {
        if (getDisplayComp() != null) {
            return getDisplayComp().contains(x, y);
        }

        return super.contains(x, y);
    }

    public void doDoubleClickAction() {
    }

    public void doDoubleClickAction(Graph graph) {
    }

    public DisplayComp getDisplayComp() {
        return displayComp;
    }

    public void setDisplayComp(DisplayComp displayComp) {
        this.displayComp = displayComp;

        removeAll();
        setLayout(new BorderLayout());
        add((JComponent) displayComp, BorderLayout.CENTER);
    }

    public NodeType getNodeType() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setNodeType(NodeType nodeType) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getCenterX() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setCenterX(int centerX) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getCenterY() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setCenterY(int centerY) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setCenter(int centerX, int centerY) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Node like(String name) {
        DisplayNode node = new DisplayNode();
        node.setName(name);
        node.setNodeType(getNodeType());
        return node;
    }

    public int compareTo(Object o) {
        Node node = (Node) o;
        final String name = getName();
        String[] tokens1 = name.split(":");
        final String _name = node.getName();
        String[] tokens2 = _name.split(":");

        if (tokens1.length == 1) {
            tokens1 = new String[]{tokens1[0], "0"};
        }

        if (tokens2.length == 1) {
            tokens2 = new String[]{tokens2[0], "0"};
        }

        int i1 = tokens1[1].compareTo(tokens2[1]);
        int i2 = tokens1[0].compareTo(tokens2[0]);

        if (i1 == 0) {
            return i2;
        }
        else {
            return i1;
        }
    }
}






