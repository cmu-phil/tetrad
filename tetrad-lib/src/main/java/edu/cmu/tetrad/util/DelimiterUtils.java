/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.util;

import edu.pitt.dbmi.data.Delimiter;

/**
 *
 * Jun 20, 2017 12:09:05 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DelimiterUtils {

    private DelimiterUtils() {
    }

    /**
     * Get the enum delimiter corresponding to char delimiter: tab, space,
     * comma, colon, semicolon, pipe.
     *
     * @param delimiter
     * @return corresponding to enum delimiter, whitespace enum will be return
     * if char does not match any listed above. if
     */
    public static Delimiter toDelimiter(char delimiter) {
        switch (delimiter) {
            case '\t':
                return Delimiter.TAB;
            case ' ':
                return Delimiter.SPACE;
            case ',':
                return Delimiter.COMMA;
            case ':':
                return Delimiter.COLON;
            case ';':
                return Delimiter.SEMICOLON;
            case '|':
                return Delimiter.PIPE;
            default:
                return Delimiter.WHITESPACE;
        }
    }

}
