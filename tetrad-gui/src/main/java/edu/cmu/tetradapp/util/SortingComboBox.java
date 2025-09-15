package edu.cmu.tetradapp.util;

import javax.swing.*;

/**
 * Extends JComboBox so that the items it contains are automatically sorted as they are added.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SortingComboBox extends JComboBox {

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the addItem() method of JComboBox to automatically sort items as they are added.  Items are sorted by
     * the strings they return in their toString() methods.
     */
    public void addItem(Object anItem) {

        String name = anItem.toString();

        for (int i = 0; i < getItemCount(); i++) {
            Object o = getItemAt(i);
            String oName = o.toString();

            if (oName.compareTo(name) > 0) {
                insertItemAt(anItem, i);

                return;
            }
        }

        super.addItem(anItem);
    }
}





