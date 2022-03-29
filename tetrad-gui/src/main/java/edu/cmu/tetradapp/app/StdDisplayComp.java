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

import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.workbench.DisplayNodeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Appearance of session nodes for standard nodes.
 *
 * @author Joseph Ramsey
 */
public class StdDisplayComp extends JComponent implements SessionDisplayComp {

    /**
     * The color of this node when there is no underlying model that it
     * represents.
     */
    private static final Color NO_MODEL_COLOR = new Color(214, 214, 214);

    /**
     * The color of this node when there is an underlying model that it
     * represents.
     */
    private static final Color HAS_MODEL_COLOR = DisplayNodeUtils.getNodeFillColor();

    /**
     * The color this node when unselected; depends on whether there is a model
     * or not.
     */
    private Color unselectedColor = StdDisplayComp.NO_MODEL_COLOR;

    /**
     * Font used to render text.
     */
    private static final Font SMALL_FONT = new Font("Dialog", Font.BOLD, 10);

    /**
     * A label displaying the name of this node--for instance, "Graph1".
     */
    private final JLabel nameLabel;

    /**
     * A label displaying the acronym for this node--for instance, "Lag Graph".
     */
    private final JLabel acronymLabel;

    /**
     * Whether the node is selected.
     */
    private boolean selected;

    /**
     * The image that's displayed in the node.
     */
    private final Image image;


    public StdDisplayComp(final String imagePath) {
        this.nameLabel = new JLabel(" ");
        this.acronymLabel = new JLabel("No model");
        this.image = ImageUtils.getImage(this, imagePath);
        layoutComponents();
    }

    /**
     * Sets the selection status of the node.
     *
     * @param selected the selection status of the node (true or false).
     */
    public void setSelected(final boolean selected) {
        this.selected = selected;
        setBorder(null);
        repaint();
    }

    private boolean isSelected() {
        return this.selected;
    }

    public void setName(final String name) {
        super.setName(name);
        this.nameLabel.setText(name);
    }

    public void setAcronym(final String acronym) {
        this.acronymLabel.setText(acronym);
        layoutComponents();
    }

    private Shape getShape() {
        return new Rectangle2D.Double(0, 0, getSize().width - 1,
                getSize().height - 1);
    }

    public boolean contains(final int x, final int y) {
        return getShape().contains(x, y);
    }

    public void setHasModel(final boolean hasModel) {
        if (hasModel) {
            this.unselectedColor = StdDisplayComp.HAS_MODEL_COLOR;
        } else {
            this.unselectedColor = StdDisplayComp.NO_MODEL_COLOR;
        }
    }


    /**
     * Paints the background of the component (since it has to be a
     * JComponent).
     */
    public void paint(final Graphics g) {
        final Graphics2D g2 = (Graphics2D) g;
        g2.setColor(isSelected() ? DisplayNodeUtils.getNodeSelectedFillColor() : this.unselectedColor);
        g2.fill(getShape());
        g2.setColor(isSelected() ? DisplayNodeUtils.getNodeSelectedEdgeColor() : DisplayNodeUtils.getNodeEdgeColor());
        g2.draw(getShape());

        super.paint(g);
    }

    private void layoutComponents() {
        removeAll();
        setLayout(new BorderLayout());
        setBackground(DisplayNodeUtils.getNodeFillColor());
        setFont(DisplayNodeUtils.getFont());

        final Box b = Box.createVerticalBox();

        // Add icon to name label.
        final Box b1 = Box.createHorizontalBox();
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel(new ImageIcon(this.image)));
        b1.add(Box.createHorizontalGlue());
        b.add(b1);

        // Construct name label.
        final Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        b2.add(Box.createHorizontalStrut(5));
        b2.add(this.nameLabel);
        b2.add(Box.createHorizontalStrut(5));
        b2.add(Box.createHorizontalGlue());
        b.add(b2);

        // Construct acronym label.
        final Box b3 = Box.createHorizontalBox();
        b3.add(Box.createHorizontalGlue());
        b3.add(Box.createHorizontalStrut(5));
        this.acronymLabel.setFont(StdDisplayComp.SMALL_FONT);
        b3.add(this.acronymLabel);
        b3.add(Box.createHorizontalStrut(5));
        b3.add(Box.createHorizontalGlue());
        b.add(b3);

        b.add(Box.createRigidArea(new Dimension(60, 0)));

        add(b, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

}




