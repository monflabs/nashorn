/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.monflabs.nashorn.debugger.ui.cdp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON, as the protocol needs it and no more. Adapted from the debugger
 * module’s internal {@code org.monflabs.nashorn.debugger.json.Json}, kept in
 * sync by hand: that package is not exported, so a client in another module
 * copies it rather than depending on it.
 *
 * <p>Objects are {@link Map}s in
 * insertion order, arrays are {@link List}s, numbers are {@link Long} when
 * integral and {@link Double} otherwise, and the rest are what one expects.
 */
public final class Json {
    private Json() {
    }

    /**
     * Parses a document.
     * @param text the text
     * @return the value
     * @throws IllegalArgumentException if the text is not JSON
     */
    public static Object parse(final String text) {
        final Parser p = new Parser(text);
        final Object value = p.value();
        p.skipWhitespace();
        if (p.pos != text.length()) {
            throw p.error("trailing characters");
        }
        return value;
    }

    /**
     * Writes a value.
     * @param value the value: a Map, a List, an array, a String, a Number, a Boolean or null
     * @return the text
     */
    public static String write(final Object value) {
        final StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    /**
     * A small builder for objects.
     * @return an empty object
     */
    public static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    /**
     * An object with the given keys and values, alternating.
     * @param keysAndValues keys and values
     * @return the object
     */
    public static Map<String, Object> object(final Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String)keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static void write(final StringBuilder sb, final Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            sb.append(value);
        } else if (value instanceof Number n) {
            final double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long)d);
            } else {
                sb.append(d);
            }
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (final Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (final Object o : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else if (value instanceof Object[] array) {
            write(sb, List.of(array));
        } else if (value instanceof CharSequence cs) {
            writeString(sb, cs.toString());
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeString(final StringBuilder sb, final String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
            case '"' -> sb.append("\\\"");
            case '\\' -> sb.append("\\\\");
            case '\n' -> sb.append("\\n");
            case '\r' -> sb.append("\\r");
            case '\t' -> sb.append("\\t");
            case '\b' -> sb.append("\\b");
            case '\f' -> sb.append("\\f");
            default -> {
                if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                    sb.append(String.format("\\u%04x", (int)c));
                } else {
                    sb.append(c);
                }
            }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(final String text) {
            this.text = text;
        }

        IllegalArgumentException error(final String what) {
            return new IllegalArgumentException("bad JSON at " + pos + ": " + what);
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                final char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object value() {
            skipWhitespace();
            if (pos >= text.length()) {
                throw error("unexpected end");
            }
            final char c = text.charAt(pos);
            switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("unexpected character '" + c + "'");
            }
        }

        private Object literal(final String word, final Object value) {
            if (!text.startsWith(word, pos)) {
                throw error("expected " + word);
            }
            pos += word.length();
            return value;
        }

        private Object number() {
            final int start = pos;
            if (text.charAt(pos) == '-') {
                pos++;
            }
            boolean integral = true;
            while (pos < text.length()) {
                final char c = text.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    integral = false;
                    pos++;
                } else {
                    break;
                }
            }
            final String s = text.substring(start, pos);
            try {
                if (integral) {
                    return Long.parseLong(s);
                }
                return Double.parseDouble(s);
            } catch (final NumberFormatException e) {
                throw error("bad number " + s);
            }
        }

        private String string() {
            pos++; // opening quote
            final StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw error("unterminated string");
                }
                final char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= text.length()) {
                    throw error("unterminated escape");
                }
                final char e = text.charAt(pos++);
                switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("bad unicode escape");
                    }
                    sb.append((char)Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw error("bad escape \\" + e);
                }
            }
        }

        private Map<String, Object> object() {
            pos++; // {
            final Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("expected a key");
                }
                final String key = string();
                skipWhitespace();
                if (peek() != ':') {
                    throw error("expected ':'");
                }
                pos++;
                map.put(key, value());
                skipWhitespace();
                final char c = peek();
                pos++;
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> array() {
            pos++; // [
            final List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                final char c = peek();
                pos++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw error("unexpected end");
            }
            return text.charAt(pos);
        }
    }
}
