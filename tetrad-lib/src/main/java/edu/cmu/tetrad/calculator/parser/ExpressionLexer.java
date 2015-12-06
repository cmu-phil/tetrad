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

package edu.cmu.tetrad.calculator.parser;

import edu.cmu.tetrad.calculator.expression.ExpressionDescriptor;
import edu.cmu.tetrad.calculator.expression.ExpressionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the tokens of an expression.
 *
 * @author Tyler Gibson
 */
public class ExpressionLexer {

    /**
     * The previous offset--before the getModel token was read.
     */
    private int currentOffset = 0;


    /**
     * The getModel position of the lexer.
     */
    private int nextOffset = 0;


    /**
     * Mapping between tokens to their matchers.
     */
    private Map<Token, Matcher> matchers = new HashMap<>();


    /**
     * The last matcher.
     */
    private Matcher lastMatcher;


    /**
     * The car sequenced being lexed.
     */
    private CharSequence charSequence;


    /**
     * The tokens.
     */
    private Token[] tokens = new Token[]{Token.WHITESPACE, Token.COMMA, Token.LPAREN,
            Token.NUMBER, Token.OPERATOR, Token.RPAREN, Token.PARAMETER,
            Token.EQUATION, Token.STRING
    };


    /**
     * Cached PATTERNS.
     */
    private static Map<Token, Pattern> PATTERNS;


    public ExpressionLexer(CharSequence seq) {
        if (seq == null) {
            throw new NullPointerException("CharSequence must not be null.");
        }
        if (PATTERNS == null) {
            PATTERNS = createPatterns();
        }
        this.charSequence = seq;
        this.matchers = createMatchers(PATTERNS, seq);
    }

    //=================================== Public Methods =====================================//

    /**
     * @return the type of the next token. For words and quoted charSequence
     * tokens, the charSequence that the token represents can be fetched by
     * calling the getString method.
     */
    public final Token nextToken() {
        readToken(Token.WHITESPACE);
        for (Token token : tokens) {
            if (readToken(token)) {
                return token;
            }
        }
        if (this.charSequence.length() <= nextOffset) {
            return Token.EOF;
        }

        return Token.UNKNOWN;
    }

    public final Token nextTokenIncludingWhitespace() {
        for (Token token : tokens) {
            if (readToken(token)) {
                return token;
            }
        }
        if (this.charSequence.length() <= nextOffset) {
            return Token.EOF;
        }

        return Token.UNKNOWN;
    }

    /**
     * @return the string corresponding to the last token lexed.
     */
    public String getTokenString() {
        if (this.lastMatcher == null) {
            return null;
        }
        try {
            return this.lastMatcher.group();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return the previous offset, before the getModel token was read (i.e. the offset of the getModel token.
     */
    public int getCurrentOffset() {
        return this.currentOffset;
    }

    /**
     * @return the getModel offset.
     */
    public int getNextOffset() {
        return this.nextOffset;
    }

    //=================================== Private Methods ====================================//


    private boolean readToken(Token token) {
        Matcher matcher = this.matchers.get(token);
        boolean found = matcher.find(this.nextOffset);
        if (found) {

            // I hate to put a gratuitous string creation here, but I see no other way. \G doesn't force the
            // match to begin at the offset if there are nonword characters are the offset, like @$##@@!. But
            // I can't eliminate all nonword characters.
            if (matcher.end() - matcher.group().length() != this.nextOffset) {
                this.currentOffset = this.nextOffset;
                this.nextOffset = matcher.end();
                return false;
            }

            this.currentOffset = this.nextOffset;
            this.nextOffset = matcher.end();
            this.lastMatcher = matcher;

        }

        return found;
    }


    /**
     * Creates a map from tokens to regex Matchers for the given CharSequence,
     * given a map from tokens to regex Patterns (and the CharSequence).
     */
    private static Map<Token, Matcher> createMatchers(Map<Token, Pattern> patterns, CharSequence charSequence) {
        Map<Token, Matcher> matchers = new HashMap<>();
        for (Token token : patterns.keySet()) {
            Pattern pattern = patterns.get(token);
            Matcher matcher = pattern.matcher(charSequence);
            matchers.put(token, matcher);
        }
        return matchers;
    }


    private static Map<Token, Pattern> createPatterns() {
        Map<Token, Pattern> map = new HashMap<>();
        Map<Token, String> regex = new HashMap<>();

        regex.put(Token.WHITESPACE, "\\s+");
        regex.put(Token.LPAREN, "\\(");
        regex.put(Token.RPAREN, "\\)");
        regex.put(Token.COMMA, ",");
        regex.put(Token.NUMBER, "-?[\\d\\.]+(e-?\\d+)?");
        regex.put(Token.OPERATOR, getExpressionRegex());
        regex.put(Token.PARAMETER, "\\$|(([a-zA-Z]{1})([a-zA-Z0-9-_/:\\.]*))");
        regex.put(Token.EQUATION, "\\=");
        regex.put(Token.STRING, "\\\".*\\\"");


        for (Token token : regex.keySet()) {
            map.put(token, Pattern.compile("\\G" + regex.get(token)));
        }

        return map;
    }


    /**
     * Builds a regex that can identify expressions.
     */
    private static String getExpressionRegex() {
        String str = "(";
        List<ExpressionDescriptor> descriptors = ExpressionManager.getInstance().getDescriptors();
        for (int i = 0; i < descriptors.size(); i++) {
            ExpressionDescriptor exp = descriptors.get(i);
            str += "(" + exp.getToken() + ")";
            if (i < descriptors.size() - 1) {
                str += "|";
            }
        }
        // replace meta characters where necessary.
        str = str.replace("+", "\\+");
        str = str.replace("*", "\\*");
        str = str.replace("^", "\\^");

        return str + ")";
    }


}




