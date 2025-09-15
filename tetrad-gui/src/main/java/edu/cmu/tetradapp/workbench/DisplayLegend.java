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

import edu.cmu.tetrad.util.TetradSerializableExcluded;

import javax.swing.*;
import java.awt.*;
import java.io.Serial;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;

/**
 * Jan 22, 2019 3:39:27 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @version $Id: $Id
 */
public class DisplayLegend extends JComponent implements TetradSerializableExcluded {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The attributes of this node.
     */
    private final Map<String, Object> attributes;

    /**
     * <p>Constructor for DisplayLegend.</p>
     *
     * @param attributes a {@link java.util.Map} object
     */
    public DisplayLegend(Map<String, Object> attributes) {
        this.attributes = attributes;
        initiateUI();
    }

    private void initiateUI() {
        setLayout(new BorderLayout());
        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel headerLabel = new JLabel("Graph's Attribute(s)");
        headerLabel.setFont(new Font(headerLabel.getFont().getName(), Font.BOLD, 12));
        headerPanel.add(headerLabel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        JPanel attributesPanel = new JPanel();
        attributesPanel.setLayout(new BoxLayout(attributesPanel, BoxLayout.PAGE_AXIS));

        for (String key : this.attributes.keySet()) {
            Object value = this.attributes.get(key);
            NumberFormat nf = new DecimalFormat("0.00");

            String _value;
            if (value instanceof Double) _value = nf.format(value);
            else _value = value.toString();

            JLabel attributeLabel = new JLabel(key + ":\t" + _value);
            attributesPanel.add(attributeLabel);
        }

        add(attributesPanel, BorderLayout.CENTER);

        // Set the bounds of the display node.
        Dimension dim = new Dimension(150, 20 + 15 * this.attributes.size());
        setSize(dim);

        setBorder(BorderFactory.createLineBorder(Color.red));

        revalidate();
        repaint();
    }

}

