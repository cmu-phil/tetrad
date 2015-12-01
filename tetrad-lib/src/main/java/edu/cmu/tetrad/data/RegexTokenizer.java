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

package edu.cmu.tetrad.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizes the given input character sequence using the type of delimiter
 * specified bythe given Pattern. Meant to function just like StringTokenizer,
 * with more control over what counts as a tokenization delimiter.
 *
 * @author Joseph Ramsey
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
     * Matcher for searching for the right-hand quote char of a pair of quote
     * chars.
     */
    private final Matcher quoteCharMatcher;

    /**
     * The position just after the last delimiter found.
     */
    private int position = 0;

    /**
     * The quote character.
     */
    private char quoteChar = '"';

    /**
     * A flag indicating that the last token has been parsed.
     */
    private boolean finalTokenParsed;

    /**
     * True iff the previous token returned was quoted.
     */
    private boolean previousTokenQuoted = false;

    /**
     * True iff the parser should be aware of quotation marks and remove
     * them from returned tokens.
     */
    private boolean quoteSensitive = true;

    /**
     * Constructs a tokenizer for the given input line, using the given Pattern
     * as delimiter.
     */
    public RegexTokenizer(CharSequence line, Pattern delimiterPattern,
                          char quoteChar) {
        chars = line;
        this.quoteChar = quoteChar;
        delimiterMatcher = delimiterPattern.matcher(line);
        quoteCharMatcher = Pattern.compile(Character.toString(quoteChar)).matcher(line);

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != ' ') {
                position = i;
                break;
            }
        }
    }

    /**
     * @return true iff more tokens exist in the line.
     */
    public final boolean hasMoreTokens() {
        return !finalTokenParsed;
    }

    /**
     * @return the next token in the line.
     */
    public final String nextToken() {
        if (position != chars.length() && quoteSensitive && chars.charAt(position) == quoteChar) {
            boolean match = quoteCharMatcher.find(position + 1);
            int end = match ? quoteCharMatcher.end() : chars.length();
            CharSequence token = chars.subSequence(position + 1, end - 1);

            match = delimiterMatcher.find(end);
            end = match ? delimiterMatcher.end() : chars.length();
            position = end;

            if (!match) {
                finalTokenParsed = true;
            }

            previousTokenQuoted = true;
            return token.toString();
        } else {
            boolean match = delimiterMatcher.find(position);
            int start = match ? delimiterMatcher.start() : chars.length();
            int end = match ? delimiterMatcher.end() : chars.length();
            CharSequence token = chars.subSequence(position, start);
            position = end;

            if (!match) {
                finalTokenParsed = true;
            }

            previousTokenQuoted = false;
            return token.toString();
        }
    }

    /**
     * True iff the parser should be aware of quotation marks and remove them
     * from returned strings.
     */
    public void setQuoteSensitive(boolean quoteSensitive) {
        this.quoteSensitive = quoteSensitive;
    }

    /**
     * True iff the parser should be aware of quotation marks and remove them
     * from returned strings.
     */
    public boolean isQuoteSensitive() {
        return quoteSensitive;
    }

    /**
     * True iff the token just returned by nextToken() is enclosed in quotes
     * (where "quote" means the provided quote mark).
     */
    public boolean isPreviousTokenQuoted() {
        return previousTokenQuoted;
    }
}



