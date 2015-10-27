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

package edu.cmu.tetradapp.util;


import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonListener;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;


/**
 * @author Tyler Gibson
 */
public class TetradButtonUI extends BasicButtonUI {


    /**
     * Singleton instance of the UI, its not based on the component so this works.
     */
    private final static TetradButtonUI TETRAD_BUTTON_UI = new TetradButtonUI();


    //TODO - Finish with the correct colors and such (i.e., have some else tell me what good colors to use are)


//    protected Color focusColor;
//    protected Color selectColor;
//    protected Color disabledTextColor;


    public static ButtonUI createUI(){
        return TETRAD_BUTTON_UI;
    }

    public static ComponentUI createUI(JComponent c) {
        return TETRAD_BUTTON_UI;
    }


    public void installDefaults(AbstractButton b) {
        super.installDefaults(b);
    }

    public void uninstallDefaults(AbstractButton b) {
        super.uninstallDefaults(b);
    }

    // ********************************
    //         Create Listeners
    // ********************************
    protected BasicButtonListener createButtonListener(AbstractButton b) {
        return super.createButtonListener(b);
    }



    /**
     * If necessary paints the background of the component, then
     * invokes <code>paint</code>.
     *
     * @param g Graphics to paint to
     * @param c JComponent painting on
     * @throws NullPointerException if <code>g</code> or <code>c</code> is
     *                              null
     * @see javax.swing.plaf.ComponentUI#update
     * @see javax.swing.plaf.ComponentUI#paint
     * @since 1.5
     */
    public void update(Graphics g, JComponent c) {
 //       AbstractButton button = (AbstractButton) c;
//        if ((c.getBackground() instanceof UIResource) &&
//                  button.isContentAreaFilled() && c.isEnabled()) {
//            ButtonModel model = button.getModel();
//            if (!MetalUtils.isToolBarButton(c)) {
//                if (!model.isArmed() && !model.isPressed() &&
//                        MetalUtils .drawGradient(
//                        c, g, "Button.gradient", 0, 0, c.width(),
//                        c.height(), true)) {
//                    paint(g, c);
//                    return;
//                }
//            }
//            else if (model.isRollover() && MetalUtils.drawGradient(
//                        c, g, "Button.gradient", 0, 0, c.width(),
//                        c.height(), true)) {
//                paint(g, c);
//                return;
//            }
//        }
        super.update(g, c);
    }

    protected void paintButtonPressed(Graphics g, AbstractButton b) {
        if (b.isContentAreaFilled()) {
            Dimension size = b.getSize();
      //      g.setColor(getSelectColor());
            g.fillRect(0, 0, size.width, size.height);
        }
    }

    protected void paintFocus(Graphics g, AbstractButton b,
                              Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {

        Rectangle focusRect = new Rectangle();
        String text = b.getText();
        boolean isIcon = b.getIcon() != null;

        // If there is text
        if (text != null && !text.equals("")) {
            if (!isIcon) {
                focusRect.setBounds(textRect);
            } else {
                focusRect.setBounds(iconRect.union(textRect));
            }
        }
        // If there is an icon and no text
        else if (isIcon) {
            focusRect.setBounds(iconRect);
        }

 //       g.setColor(getFocusColor());
        g.drawRect((focusRect.x - 1), (focusRect.y - 1),
                focusRect.width + 1, focusRect.height + 1);

    }


    protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
//        AbstractButton b = (AbstractButton) c;
//        ButtonModel model = b.getModel();
//        FontMetrics fm = SwingUtilities2.getFontMetrics(c, g);
//        int mnemIndex = b.getDisplayedMnemonicIndex();
//
//        /* Draw the Text */
//        if (model.isEnabled()) {
//            /*** paint the text normally */
//            g.setColor(b.getForeground());
//        } else {
//            /*** paint the text disabled ***/
//         //   g.setColor(getDisabledTextColor());
//        }
//        SwingUtilities2.drawStringUnderlineCharAt(c, g, text, mnemIndex,
//                textRect.x, textRect.y + fm.getAscent());
    }


}




