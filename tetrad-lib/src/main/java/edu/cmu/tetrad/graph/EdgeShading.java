///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.graph;

import java.awt.Color;

/**
 * A small color-vision-safe palette for shading graph edges by the sign and strength of an estimated effect. Hues are
 * from the Okabe-Ito palette, chosen to remain distinguishable under the common forms of color-vision deficiency.
 * Intensity in [0, 1] runs from a light but clearly colored tint of the hue to a deepened version of it. Shared by the
 * SEM IM and hybrid CG IM graph views.
 */
public final class EdgeShading {

    private EdgeShading() {
    }

    /**
     * The hue families.
     */
    public enum Hue {
        /** Positive effect (Okabe-Ito blue). */
        POSITIVE,
        /** Negative effect (Okabe-Ito vermillion). */
        NEGATIVE,
        /** Effect whose sign varies by context (Okabe-Ito reddish purple). */
        MIXED,
        /** Effect with no sign, e.g. a table effect (Okabe-Ito bluish green). */
        UNSIGNED
    }

    /**
     * Returns the shade for a hue and intensity.
     *
     * @param hue       the hue family
     * @param intensity a value in [0, 1]; values outside are clamped
     * @return the color
     */
    public static Color color(Hue hue, double intensity) {
        return color(hue, intensity, false);
    }

    /**
     * As {@link #color(Hue, double)}, for a light or a dark canvas. The hues are the same in both modes; only the
     * intensity ramp changes. On a light canvas it runs from a pale tint toward a deep shade, so strong edges are
     * dark. On a dark canvas that ramp runs backwards, deepening toward the canvas until the strongest edges are
     * the least visible, so there it runs from a muted, greyed base toward a light tint instead. Both ramps put
     * the weakest edges near 2:1 against their canvas and the strongest near 5:1 to 7:1.
     *
     * @param hue       the hue family
     * @param intensity a value in [0, 1]
     * @param dark      true for a dark canvas
     * @return the color
     */
    public static Color color(Hue hue, double intensity, boolean dark) {
        Color base = switch (hue) {
            case POSITIVE -> new Color(0x00, 0x72, 0xB2);
            case NEGATIVE -> new Color(0xD5, 0x5E, 0x00);
            case MIXED -> new Color(0xCC, 0x79, 0xA7);
            case UNSIGNED -> new Color(0x00, 0x9E, 0x73);
        };
        double t = Math.max(0.0, Math.min(1.0, intensity));
        Color pale;
        Color deep;
        if (dark) {
            pale = mix(new Color(120, 120, 120), base, 0.45);
            deep = mix(Color.WHITE, base, 0.45);
        } else {
            pale = mix(Color.WHITE, base, 0.60);
            deep = mix(Color.BLACK, base, 0.80);
        }
        return mix(pale, deep, t);
    }

    /**
     * Returns the shade for a signed value: POSITIVE for values at or above zero, NEGATIVE otherwise.
     *
     * @param value     the signed value
     * @param intensity a value in [0, 1]
     * @return the color
     */
    public static Color signed(double value, double intensity) {
        return signed(value, intensity, false);
    }

    /**
     * As {@link #signed(double, double)}, for a light or a dark canvas; see {@link #color(Hue, double, boolean)}.
     *
     * @param value     the signed value
     * @param intensity a value in [0, 1]
     * @param dark      true for a dark canvas
     * @return the color
     */
    public static Color signed(double value, double intensity, boolean dark) {
        return color(value >= 0 ? Hue.POSITIVE : Hue.NEGATIVE, intensity, dark);
    }

    private static Color mix(Color a, Color b, double t) {
        int r = (int) Math.round(a.getRed() + t * (b.getRed() - a.getRed()));
        int g = (int) Math.round(a.getGreen() + t * (b.getGreen() - a.getGreen()));
        int bl = (int) Math.round(a.getBlue() + t * (b.getBlue() - a.getBlue()));
        return new Color(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
