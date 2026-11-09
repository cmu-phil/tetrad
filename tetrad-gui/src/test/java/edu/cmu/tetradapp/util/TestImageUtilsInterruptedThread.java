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

package edu.cmu.tetradapp.util;

import org.junit.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.Assert.*;

/**
 * Regression test for session nodes displaying without an icon.
 * <p>
 * During propagation, session nodes are laid out on the WatchedProcess worker thread, and that thread's interrupt
 * flag may be set (after a stop, or after a failure). The old {@code ImageUtils.getImage} returned an asynchronous
 * {@code Toolkit} image; wrapping it in {@code ImageIcon} on an interrupted thread aborts the MediaTracker wait and
 * yields an icon of width -1, i.e. nothing is displayed. This test fails on the unpatched code (width -1) and passes
 * once images are decoded synchronously.
 */
public class TestImageUtilsInterruptedThread {

    private static final String[] ICONS = {"searchIcon.gif", "dataIcon.gif", "graphIcon.gif", "graduation_hat1.png"};

    @Test
    public void testIconHasSizeOnInterruptedThread() {
        Thread.currentThread().interrupt();
        try {
            for (String name : ICONS) {
                Image image = ImageUtils.getImage(this, name);
                assertNotNull(image);
                ImageIcon icon = new ImageIcon(image);
                assertTrue(name + ": width was " + icon.getIconWidth(), icon.getIconWidth() > 0);
                assertTrue(name + ": height was " + icon.getIconHeight(), icon.getIconHeight() > 0);
                assertEquals(name + ": load status", MediaTracker.COMPLETE, icon.getImageLoadStatus());
            }
        } finally {
            // Clear the flag so it does not leak into other tests.
            Thread.interrupted();
        }
    }

    @Test
    public void testImageIsCachedPerPath() {
        Image a = ImageUtils.getImage(this, "searchIcon.gif");
        Image b = ImageUtils.getImage(this, "searchIcon.gif");
        assertSame(a, b);
    }
}
