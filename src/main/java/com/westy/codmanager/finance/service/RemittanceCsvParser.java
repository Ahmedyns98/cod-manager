package com.westy.codmanager.finance.service;

import com.westy.codmanager.common.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a carrier payout export.
 *
 * Real exports are messy: semicolons in one locale and commas in another, a
 * BOM from Excel, French or English headers, thousands separators, blank
 * trailing lines. The parser accommodates all of that, and reports bad rows
 * individually instead of rejecting the file, because one unreadable line out
 * of four hundred should not cost the seller the whole import.
 */
@Component
public class RemittanceCsvParser {

    private static final Set<String> TRACKING_HEADERS =
            Set.of("tracking", "tracking_number", "colis", "n° colis", "numero colis", "reference");

    private static final Set<String> COLLECTED_HEADERS =
            Set.of("collected", "amount", "montant", "montant encaisse", "prix", "total");

    private static final Set<String> FEE_HEADERS =
            Set.of("fee", "fees", "frais", "frais livraison", "commission", "tarif");

    public Result parse(InputStream input, String fileName) {
        List<Row> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {

            String headerLine = nextMeaningfulLine(reader);

            if (headerLine == null) {
                throw new BusinessRuleException("EMPTY_FILE", "The file has no rows");
            }

            char delimiter = detectDelimiter(headerLine);
            Map<String, Integer> columns = mapColumns(split(headerLine, delimiter));

            requireColumn(columns, "tracking");
            requireColumn(columns, "collected");

            String line;
            int number = 1;

            while ((line = reader.readLine()) != null) {
                number++;

                if (line.isBlank()) {
                    continue;
                }

                try {
                    rows.add(toRow(split(line, delimiter), columns, number));
                } catch (Exception ex) {
                    errors.add(new RowError(number, ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("UNREADABLE_FILE", "Could not read " + fileName);
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new BusinessRuleException("EMPTY_FILE", "The file has a header but no rows");
        }

        return new Result(rows, errors);
    }

    private String nextMeaningfulLine(BufferedReader reader) throws IOException {
        String line;

        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                // Excel writes a byte order mark that would corrupt the first header.
                return line.startsWith("\uFEFF") ? line.substring(1) : line;
            }
        }

        return null;
    }

    private char detectDelimiter(String header) {
        long semicolons = header.chars().filter(c -> c == ';').count();
        long commas = header.chars().filter(c -> c == ',').count();
        long tabs = header.chars().filter(c -> c == '\t').count();

        if (tabs > semicolons && tabs > commas) {
            return '\t';
        }

        return semicolons >= commas ? ';' : ',';
    }

    /** Minimal RFC 4180 handling: quoted fields may contain the delimiter. */
    private List<String> split(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delimiter && !quoted) {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        fields.add(current.toString().trim());

        return fields;
    }

    private Map<String, Integer> mapColumns(List<String> headers) {
        Map<String, Integer> columns = new HashMap<>();

        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).toLowerCase(Locale.ROOT).replace('_', ' ').trim();

            if (TRACKING_HEADERS.contains(header)) {
                columns.putIfAbsent("tracking", i);
            } else if (COLLECTED_HEADERS.contains(header)) {
                columns.putIfAbsent("collected", i);
            } else if (FEE_HEADERS.contains(header)) {
                columns.putIfAbsent("fee", i);
            }
        }

        return columns;
    }

    private void requireColumn(Map<String, Integer> columns, String name) {
        if (!columns.containsKey(name)) {
            throw new BusinessRuleException("MISSING_COLUMN",
                    "The file has no recognisable '%s' column".formatted(name));
        }
    }

    private Row toRow(List<String> fields, Map<String, Integer> columns, int number) {
        String tracking = field(fields, columns.get("tracking"));

        if (tracking == null || tracking.isBlank()) {
            throw new IllegalArgumentException("Tracking number is empty");
        }

        BigDecimal collected = amount(field(fields, columns.get("collected")));
        BigDecimal fee = columns.containsKey("fee")
                ? amount(field(fields, columns.get("fee")))
                : BigDecimal.ZERO;

        return new Row(number, tracking, collected, fee);
    }

    private String field(List<String> fields, Integer index) {
        return index == null || index >= fields.size() ? null : fields.get(index);
    }

    /** Accepts "1 500,00", "1,500.00" and "1500" alike. */
    private BigDecimal amount(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }

        String cleaned = raw.replaceAll("[^0-9,.\\-]", "");

        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')
                    ? cleaned.replace(".", "").replace(',', '.')
                    : cleaned.replace(",", "");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(',', '.');
        }

        try {
            return new BigDecimal(cleaned).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'%s' is not an amount".formatted(raw));
        }
    }

    public record Row(int number, String tracking, BigDecimal collected, BigDecimal fee) {
    }

    public record RowError(int number, String message) {
    }

    public record Result(List<Row> rows, List<RowError> errors) {
    }
}
