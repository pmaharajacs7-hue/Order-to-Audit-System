package com.orderaudit.report;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Minimal RFC 4180 CSV writer. Also neutralises spreadsheet formula injection (=, +, -, @ at start of a text cell). */
final class CsvWriter {

    private CsvWriter() {}

    static byte[] write(List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿"); // BOM so Excel reads UTF-8
        sb.append(String.join(",", header)).append("\r\n");
        for (List<Object> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(cell(row.get(i)));
            }
            sb.append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String cell(Object o) {
        if (o == null) return "";
        boolean numeric = o instanceof Number;
        String s = o instanceof java.math.BigDecimal bd ? bd.toPlainString() : String.valueOf(o);
        if (!numeric && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
