package com.example.classapp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Minimal, dependency-free .xlsx reader. Reads the first worksheet and returns rows as lists of strings
 * (column positions are preserved; empty cells become ""). Handles shared strings (incl. rich text),
 * inline strings, self-closing empty cells, and numeric values.
 */
final class XlsxReader {
    private static final int MAX_ENTRY = 40 * 1024 * 1024;
    private static final int MAX_COL = 255;

    private XlsxReader() {}

    static List<List<String>> read(InputStream in) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (name.startsWith("/")) name = name.substring(1);
                boolean wanted = name.equals("xl/workbook.xml") || name.equals("xl/_rels/workbook.xml.rels")
                        || name.equals("xl/sharedStrings.xml")
                        || (name.startsWith("xl/worksheets/") && name.endsWith(".xml"));
                if (!wanted) continue;
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                int n;
                while ((n = z.read(buf)) > 0) {
                    b.write(buf, 0, n);
                    if (b.size() > MAX_ENTRY) throw new IOException("فایل Excel بیش از حد بزرگ است");
                }
                files.put(name, b.toByteArray());
            }
        }
        if (files.isEmpty()) throw new IOException("این فایل یک Excel معتبر (.xlsx) نیست");
        byte[] sheet = files.get(firstSheetPath(files));
        List<String> shared = readShared(files.get("xl/sharedStrings.xml"));
        return readSheet(sheet, shared);
    }

    // ---------- worksheet location ----------

    private static String firstSheetPath(Map<String, byte[]> f) throws Exception {
        byte[] wb = f.get("xl/workbook.xml");
        byte[] rels = f.get("xl/_rels/workbook.xml.rels");
        if (wb != null && rels != null) {
            final String[] rid = {null};
            parse(wb, new Base() {
                @Override public void startElement(String u, String l, String q, Attributes a) {
                    if (rid[0] == null && local(q).equals("sheet")) rid[0] = attrId(a);
                }
            });
            if (rid[0] != null) {
                final String[] target = {null};
                parse(rels, new Base() {
                    @Override public void startElement(String u, String l, String q, Attributes a) {
                        if (local(q).equals("Relationship") && rid[0].equals(a.getValue("Id"))) target[0] = a.getValue("Target");
                    }
                });
                if (target[0] != null) {
                    String t = target[0].replace('\\', '/');
                    String path = t.startsWith("/") ? t.substring(1) : "xl/" + t;
                    if (f.containsKey(path)) return path;
                }
            }
        }
        if (f.containsKey("xl/worksheets/sheet1.xml")) return "xl/worksheets/sheet1.xml";
        List<String> names = new ArrayList<>();
        for (String k : f.keySet()) if (k.startsWith("xl/worksheets/") && k.endsWith(".xml")) names.add(k);
        Collections.sort(names);
        if (names.isEmpty()) throw new IOException("برگه‌ای در فایل Excel پیدا نشد");
        return names.get(0);
    }

    // ---------- shared strings ----------

    private static List<String> readShared(byte[] data) throws Exception {
        final List<String> out = new ArrayList<>();
        if (data == null) return out;
        parse(data, new Base() {
            StringBuilder si;
            int ph;
            boolean inT;

            @Override public void startElement(String u, String l, String q, Attributes a) {
                String n = local(q);
                if (n.equals("si")) si = new StringBuilder();
                else if (n.equals("rPh")) ph++;
                else if (n.equals("t") && si != null && ph == 0) inT = true;
            }

            @Override public void characters(char[] c, int s, int len) {
                if (inT && si != null) si.append(c, s, len);
            }

            @Override public void endElement(String u, String l, String q) {
                String n = local(q);
                if (n.equals("t")) inT = false;
                else if (n.equals("rPh")) ph--;
                else if (n.equals("si")) {
                    out.add(si == null ? "" : si.toString());
                    si = null;
                }
            }
        });
        return out;
    }

    // ---------- worksheet ----------

    private static List<List<String>> readSheet(byte[] data, final List<String> shared) throws Exception {
        final List<List<String>> rows = new ArrayList<>();
        parse(data, new Base() {
            List<String> row;
            String type;
            int col, seq, ph;
            boolean inCell, inV, inT;
            final StringBuilder v = new StringBuilder();
            final StringBuilder is = new StringBuilder();

            @Override public void startElement(String u, String l, String q, Attributes a) {
                String n = local(q);
                if (n.equals("row")) {
                    row = new ArrayList<>();
                    seq = 0;
                } else if (n.equals("c")) {
                    inCell = true;
                    type = a.getValue("t");
                    String ref = a.getValue("r");
                    col = ref != null ? colIndex(ref) : seq;
                    v.setLength(0);
                    is.setLength(0);
                } else if (n.equals("v") && inCell) inV = true;
                else if (n.equals("rPh")) ph++;
                else if (n.equals("t") && inCell && ph == 0) inT = true;
            }

            @Override public void characters(char[] c, int s, int len) {
                if (inV) v.append(c, s, len);
                else if (inT) is.append(c, s, len);
            }

            @Override public void endElement(String u, String l, String q) {
                String n = local(q);
                if (n.equals("v")) inV = false;
                else if (n.equals("t")) inT = false;
                else if (n.equals("rPh")) ph--;
                else if (n.equals("c")) {
                    inCell = false;
                    if (row != null && col >= 0 && col <= MAX_COL) {
                        while (row.size() <= col) row.add("");
                        row.set(col, cellValue(type, v.toString(), is.toString(), shared));
                    }
                    seq = col + 1;
                } else if (n.equals("row")) {
                    if (row != null) rows.add(row);
                    row = null;
                }
            }
        });
        return rows;
    }

    private static String cellValue(String type, String v, String inline, List<String> shared) {
        if ("s".equals(type)) {
            try {
                int i = Integer.parseInt(v.trim());
                return i >= 0 && i < shared.size() ? shared.get(i) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if ("inlineStr".equals(type)) return inline;
        if ("str".equals(type)) return v;
        if ("b".equals(type)) return v.trim().equals("1") ? "TRUE" : "FALSE";
        if ("e".equals(type)) return "";
        return cleanNumber(v);
    }

    /** "1001.0" -> "1001", "9.123456789E9" -> "9123456789"; non-numbers are returned as-is. */
    static String cleanNumber(String v) {
        String s = v.trim();
        if (s.isEmpty()) return "";
        if (s.matches("[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?")) {
            try {
                return new BigDecimal(s).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return s;
    }

    static int colIndex(String ref) {
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') col = col * 26 + (c - 'A' + 1);
            else if (c >= 'a' && c <= 'z') col = col * 26 + (c - 'a' + 1);
            else break;
        }
        return col - 1;
    }

    // ---------- SAX plumbing ----------

    private static void parse(byte[] data, DefaultHandler h) throws Exception {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(false);
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
            // not supported by every parser; external entities are blocked in Base.resolveEntity anyway
        }
        f.newSAXParser().parse(new ByteArrayInputStream(data), h);
    }

    private static String local(String qName) {
        int i = qName.indexOf(':');
        return i >= 0 ? qName.substring(i + 1) : qName;
    }

    private static String attrId(Attributes a) {
        for (int i = 0; i < a.getLength(); i++) {
            String q = a.getQName(i);
            if (q.equals("id") || q.endsWith(":id")) return a.getValue(i);
        }
        return null;
    }

    private abstract static class Base extends DefaultHandler {
        @Override public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
            return new InputSource(new StringReader(""));
        }
    }
}
