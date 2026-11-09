/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins the text side of the error dialog (added 2026-8-24): the summary must never read "null", the innermost cause
 * is what gets summarized, the details carry the full trace, and interruptions are recognized through wrapping.
 */
public class TestErrorDialogs {

    @Test
    public void summaryNeverSaysNull() {
        String s = ErrorDialogs.summaryLine(new IllegalStateException());
        assertEquals("IllegalStateException (no message was provided)", s);
        assertFalse(s.contains("null"));
        assertEquals("IllegalStateException (no message was provided)",
                ErrorDialogs.summaryLine(new IllegalStateException("   ")));
    }

    @Test
    public void summaryUsesRootCause() {
        Throwable t = new RuntimeException("outer", new IllegalArgumentException("Variable 'X' not found."));
        assertEquals("IllegalArgumentException: Variable 'X' not found.", ErrorDialogs.summaryLine(t));
    }

    @Test
    public void rootCauseSurvivesCyclicChains() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        Throwable root = ErrorDialogs.rootCause(b);
        assertTrue(root == a || root == b);
    }

    @Test
    public void detailsContainVersionSummaryAndTrace() {
        Throwable t = new RuntimeException("wrap", new NullPointerException("inner npe"));
        String d = ErrorDialogs.detailsText(t);
        assertTrue(d.startsWith("Tetrad "));
        assertTrue(d.contains("Java "));
        assertTrue(d.contains("NullPointerException: inner npe"));
        assertTrue(d.contains("Caused by: java.lang.NullPointerException"));
        assertTrue(d.contains("TestErrorDialogs.detailsContainVersionSummaryAndTrace"));
    }

    @Test
    public void interruptionIsRecognizedThroughWrapping() {
        assertTrue(ErrorDialogs.isInterruption(new InterruptedException()));
        assertTrue(ErrorDialogs.isInterruption(new RuntimeException(new InterruptedException())));
        assertTrue(ErrorDialogs.isInterruption(
                new RuntimeException("x", new IllegalStateException(new InterruptedException()))));
        assertFalse(ErrorDialogs.isInterruption(new RuntimeException("x")));
        assertFalse(ErrorDialogs.isInterruption(new OutOfMemoryError()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void showErrorRejectsNull() {
        ErrorDialogs.showError(null, "t", "c", null);
    }
}
