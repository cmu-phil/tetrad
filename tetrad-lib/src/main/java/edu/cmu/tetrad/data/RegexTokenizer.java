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

package edu.cmu.tetrad.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizes the given input character sequence using the type of delimiter specified bythe given CPDAG. Meant to
 * function just like StringTokenizer, with more control over what counts as a tokenization delimiter.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class RegexTokenizer {

    /**
     * The character sequence being tokenized.
     */
    private final CharSequence chars;

    /**
     * The matcher being used to search for delimiters.
     */
    private final Matcher delimiterMatcher;

    /**
     * Matcher for searching for the right-hand quote char of a pair of quote chars.
     */
    private final Matcher quoteCharMatcher;
    /**
     * The quote character.
     */
    private final char quoteChar;
    /**
     * The position just after the last delimiter found.
     */
    private int position;
    /**
     * A flag indicating that the last token has been parsed.
     */
    private boolean finalTokenParsed;

    /**
     * True iff the parser should be aware of quotation marks and remove them from returned tokens.
     */
    private boolean quoteSensitive = true;

    /**
     * Constructs a tokenizer for the given input line, using the given Pattern as delimiter.
     *
     * @param line             a {@link java.lang.CharSequence} object
     * @param delimiterPattern a {@link java.util.regex.Pattern} object
     * @param quoteChar        a char
     */
    public RegexTokenizer(CharSequence line, Pattern delimiterPattern,
                          char quoteChar) {
        this.chars = line;
        this.quoteChar = quoteChar;
        this.delimiterMatcher = delimiterPattern.matcher(line);
        this.quoteCharMatcher = Pattern.compile(Character.toString(quoteChar)).matcher(line);

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != ' ') {
                this.position = i;
                break;
            }
        }
    }

    /**
     * <p>hasMoreTokens.</p>
     *
     * @return true iff more tokens exist in the line.
     */
    public boolean hasMoreTokens() {
        return !this.finalTokenParsed;
    }

    /**
     * <p>nextToken.</p>
     *
     * @return the next token in the line.
     */
    public String nextToken() {
        if (this.position != this.chars.length() && this.quoteSensitive && this.chars.charAt(this.position) == this.quoteChar) {
            boolean match = this.quoteCharMatcher.find(this.position + 1);
            int end = match ? this.quoteCharMatcher.end() : this.chars.length();
            CharSequence token = this.chars.subSequence(this.position + 1, end - 1);

            match = this.delimiterMatcher.find(end);
            end = match ? this.delimiterMatcher.end() : this.chars.length();
            this.position = end;

            if (!match) {
                this.finalTokenParsed = true;
            }

            return token.toString();
        } else {
            boolean match = this.delimiterMatcher.find(this.position);
            int start = match ? this.delimiterMatcher.start() : this.chars.length();
            int end = match ? this.delimiterMatcher.end() : this.chars.length();
            CharSequence token = this.chars.subSequence(this.position, start);
            this.position = end;

            if (!match) {
                this.finalTokenParsed = true;
            }

            return token.toString();
        }
    }

    /**
     * True iff the parser should be aware of quotation marks and remove them from returned strings.
     *
     * @param quoteSensitive a boolean
     */
    public void setQuoteSensitive(boolean quoteSensitive) {
        this.quoteSensitive = quoteSensitive;
    }

}



