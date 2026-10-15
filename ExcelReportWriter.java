
package com.cibc.main;

import com.cibc.main.TestResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class ExcelReportWriter {

    private static final String[] MEASUREMENT_HEADERS = {
            "Run ID", "Timestamp", "Operation", "Mode", "Batch Size",
            "Record ID", "SQL Duration (ms)", "Redis Propagation (ms)",
            "Total Duration (ms)", "Success", "Timeout", "Error"
    };

    private static final String[] SUMMARY_HEADERS = {
            "Run ID", "Operation", "Mode", "Batch Size", "Records Attempted", "Records Successful",
            "Records Failed", "Success Rate (%)", "SQL Average (ms)", "SQL P50 (ms)",
            "SQL P95 (ms)", "SQL P99 (ms)", "SQL Maximum (ms)", "SQL Samples",
            "Redis Average (ms)", "Redis P50 (ms)", "Redis P95 (ms)", "Redis P99 (ms)",
            "Redis Maximum (ms)", "Redis Samples"
    };

    public void write(Path output, List<TestResult> results) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle header = headerStyle(workbook);

            Sheet measurements = workbook.createSheet("Measurements");
            writeHeaders(measurements, MEASUREMENT_HEADERS, header);

            for (TestResult result : results) {
                writeMeasurement(measurements, result);
            }

            autosize(measurements, MEASUREMENT_HEADERS.length);

            Sheet summary = workbook.createSheet("Run Summary");
            writeHeaders(summary, SUMMARY_HEADERS, header);
            writeSummary(summary, results);
            autosize(summary, SUMMARY_HEADERS.length);

            try (OutputStream out = Files.newOutputStream(output)) {
                workbook.write(out);
            }
        }
    }

    private static void writeMeasurement(Sheet sheet, TestResult r) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        int c = 0;

        row.createCell(c++).setCellValue(r.runId());
        row.createCell(c++).setCellValue(r.timestamp().toString());
        row.createCell(c++).setCellValue(r.operation());
        row.createCell(c++).setCellValue(r.mode());
        row.createCell(c++).setCellValue(r.batchSize());
        row.createCell(c++).setCellValue(r.recordId());
        row.createCell(c++).setCellValue(r.sqlDurationMillis());
        row.createCell(c++).setCellValue(r.redisPropagationMillis());
        row.createCell(c++).setCellValue(r.totalDurationMillis());
        row.createCell(c++).setCellValue(r.success());
        row.createCell(c++).setCellValue(r.timeout());
        row.createCell(c).setCellValue(r.error() == null ? "" : r.error());
    }

    private static void writeSummary(Sheet sheet, List<TestResult> all) {
        Map<String, List<TestResult>> groups = all.stream().collect(Collectors.groupingBy(
                r -> r.operation() + "|" + r.mode() + "|" + r.batchSize(),
                TreeMap::new,
                Collectors.toList()
        ));

        for (List<TestResult> group : groups.values()) {
            TestResult first = group.get(0);

            List<Double> sql = group.stream()
                    .map(TestResult::sqlDurationMillis)
                    .filter(v -> v > 0)
                    .sorted()
                    .toList();

            List<Double> redis = group.stream()
                    .filter(TestResult::success)
                    .map(TestResult::redisPropagationMillis)
                    .sorted()
                    .toList();

            long successes = group.stream().filter(TestResult::success).count();
            int attempted = group.size();

            Row row = sheet.createRow(sheet.getLastRowNum() + 1);
            int c = 0;

            row.createCell(c++).setCellValue(first.runId());
            row.createCell(c++).setCellValue(first.operation());
            row.createCell(c++).setCellValue(first.mode());
            row.createCell(c++).setCellValue(first.batchSize());
            row.createCell(c++).setCellValue(attempted);
            row.createCell(c++).setCellValue(successes);
            row.createCell(c++).setCellValue(attempted - successes);
            row.createCell(c++).setCellValue(attempted == 0 ? 0 : 100.0 * successes / attempted);

            row.createCell(c++).setCellValue(avg(sql));
            row.createCell(c++).setCellValue(percentile(sql, 50));
            row.createCell(c++).setCellValue(percentile(sql, 95));
            row.createCell(c++).setCellValue(percentile(sql, 99));
            row.createCell(c++).setCellValue(max(sql));
            row.createCell(c++).setCellValue(sql.size());

            row.createCell(c++).setCellValue(avg(redis));
            row.createCell(c++).setCellValue(percentile(redis, 50));
            row.createCell(c++).setCellValue(percentile(redis, 95));
            row.createCell(c++).setCellValue(percentile(redis, 99));
            row.createCell(c++).setCellValue(max(redis));
            row.createCell(c).setCellValue(redis.size());
        }
    }

    private static double avg(List<Double> values) {
        return values.isEmpty()
                ? 0
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double max(List<Double> values) {
        return values.isEmpty() ? 0 : values.get(values.size() - 1);
    }

    // Nearest-rank percentile; values must already be sorted.
    private static double percentile(List<Double> sorted, int percentile) {
        if (sorted.isEmpty()) return 0;

        int index = Math.max(
                0,
                (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1
        );

        return sorted.get(index);
    }

    private static void writeHeaders(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(
                new org.apache.poi.ss.util.CellRangeAddress(
                        0, 0, 0, headers.length - 1
                )
        );
    }

    private static CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);

        return style;
    }

    private static void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
