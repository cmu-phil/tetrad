/**
 *
 */
package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.util.TetradSerializableExcluded;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Jan 22, 2019 3:39:27 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class DisplayLegend extends JComponent implements TetradSerializableExcluded {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> attributes;

    public DisplayLegend(final Map<String, Object> attributes) {
        this.attributes = attributes;
        initiateUI();
    }

    private void initiateUI() {
        setLayout(new BorderLayout());
        // Header
        final JPanel headerPanel = new JPanel(new BorderLayout());
        final JLabel headerLabel = new JLabel("Graph's Attribute(s)");
        headerLabel.setFont(new Font(headerLabel.getFont().getName(), Font.BOLD, 12));
        headerPanel.add(headerLabel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        final JPanel attributesPanel = new JPanel();
        attributesPanel.setLayout(new BoxLayout(attributesPanel, BoxLayout.PAGE_AXIS));

        for (final String key : this.attributes.keySet()) {
            final Object value = this.attributes.get(key);

            final JLabel attributeLabel = new JLabel(key + ":\t" + value.toString());
            attributesPanel.add(attributeLabel);
        }

        add(attributesPanel, BorderLayout.CENTER);

        // Set the bounds of the display node.
        final Dimension dim = new Dimension(150, 20 + 15 * this.attributes.size());
        setSize(dim);

        setBorder(BorderFactory.createLineBorder(Color.red));

        revalidate();
        repaint();
    }

}
