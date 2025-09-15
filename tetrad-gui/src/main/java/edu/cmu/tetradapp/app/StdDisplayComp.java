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

import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.workbench.DisplayNodeUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Appearance of session nodes for standard nodes.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class StdDisplayComp extends JComponent implements SessionDisplayComp {

    /**
     * The color of this node when there is no underlying model that it represents.
     */
    private static final Color NO_MODEL_COLOR = new Color(214, 214, 214);

    /**
     * The color of this node when there is an underlying model that it represents.
     */
    private static final Color HAS_MODEL_COLOR = DisplayNodeUtils.getNodeFillColor();
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
     * The image that's displayed in the node.
     */
    private final Image image;
    /**
     * The color this node when unselected; depends on whether there is a model or not.
     */
    private Color unselectedColor = StdDisplayComp.NO_MODEL_COLOR;
    /**
     * Whether the node is selected.
     */
    private boolean selected;


    /**
     * <p>Constructor for StdDisplayComp.</p>
     *
     * @param imagePath a {@link java.lang.String} object
     */
    public StdDisplayComp(String imagePath) {
        this.nameLabel = new JLabel(" ");
        this.acronymLabel = new JLabel("No model");
        this.image = ImageUtils.getImage(this, imagePath);
        layoutComponents();
    }

    private boolean isSelected() {
        return this.selected;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the selection status of the node.
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
        setBorder(null);
        repaint();
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        super.setName(name);
        this.nameLabel.setText(name);
    }

    /**
     * {@inheritDoc}
     */
    public void setAcronym(String acronym) {
        this.acronymLabel.setText(acronym);
        layoutComponents();
    }

    private Shape getShape() {
        return new RoundRectangle2D.Double(0, 0, getSize().width - 1,
                getSize().height - 1, 10, 10);
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(int x, int y) {
        return getShape().contains(x, y);
    }

    /**
     * {@inheritDoc}
     */
    public void setHasModel(boolean hasModel) {
        if (hasModel) {
            this.unselectedColor = StdDisplayComp.HAS_MODEL_COLOR;
        } else {
            this.unselectedColor = StdDisplayComp.NO_MODEL_COLOR;
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * Paints the background of the component (since it has to be a JComponent).
     */
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
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

        Box b = Box.createVerticalBox();

        // Add icon to name label.
        Box b1 = Box.createHorizontalBox();
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel(new ImageIcon(this.image)));
        b1.add(Box.createHorizontalGlue());
        b.add(b1);

        // Construct name label.
        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        b2.add(Box.createHorizontalStrut(5));
        b2.add(this.nameLabel);
        b2.add(Box.createHorizontalStrut(5));
        b2.add(Box.createHorizontalGlue());
        b.add(b2);

        // Construct acronym label.
        Box b3 = Box.createHorizontalBox();
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





