package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.util.TetradSerializableExcluded;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
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
