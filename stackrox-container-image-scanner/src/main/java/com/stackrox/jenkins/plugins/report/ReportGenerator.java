package com.stackrox.jenkins.plugins.report;

import com.google.common.base.Strings;

import com.stackrox.jenkins.plugins.data.CVE;
import com.stackrox.jenkins.plugins.data.ImageCheckResults;
import com.stackrox.jenkins.plugins.data.PolicyViolation;

import hudson.AbortException;
import hudson.FilePath;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class ReportGenerator {

    private static final String[] CVES_HEADER = {"COMPONENT", "VERSION", "CVE", "FIXABLE", "SEVERITY", "CVSS SCORE", "SCORE TYPE", "LINK"};
    private static final String[] VIOLATED_POLICIES_HEADER = {"POLICY", "SEVERITY", "DESCRIPTION", "VIOLATION", "REMEDIATION", "ENFORCED"};
    private static final String CVES_FILENAME = "cves.csv";
    private static final String POLICY_VIOLATIONS_FILENAME = "policyViolations.csv";
    private static final String NOT_AVAILABLE = "-";
    private static final String NO_REMEDIATION_ACTIONS = "No remediation actions documented.";

    public static void generateBuildReport(List<ImageCheckResults> results, FilePath reportsDir) throws AbortException {
        try {
            for (ImageCheckResults result : results) {
                generateReport(reportsDir, result);
            }
        } catch (IOException | InterruptedException e) {
            throw new AbortException(String.format("Failed to write image scan results. Error: %s", e.getMessage()));
        }
    }

    private static void generateReport(FilePath reportsDir, ImageCheckResults result) throws IOException, InterruptedException {
        FilePath imageResultDir = new FilePath(reportsDir, result.getImageName().replace(":", "."));
        imageResultDir.mkdirs();

        if (!result.getCves().isEmpty()) {
            try (OutputStream outputStream = new FilePath(imageResultDir, CVES_FILENAME).write();
                 CSVPrinter printer = openCsv(outputStream, CVES_HEADER)) {
                for (CVE cve : result.getCves()) {
                    printer.printRecord(nullIfEmpty(
                            cve.getPackageName(),
                            cve.getPackageVersion(),
                            cve.getId(),
                            cve.getSeverity(),
                            cve.isFixable(),
                            cve.getCvssScore(),
                            cve.getScoreType(),
                            cve.getLink()
                    ));
                }
            }
        }

        if (!result.getViolatedPolicies().isEmpty()) {
            try (OutputStream outputStream = new FilePath(imageResultDir, POLICY_VIOLATIONS_FILENAME).write();
                 CSVPrinter printer = openCsv(outputStream, VIOLATED_POLICIES_HEADER)) {
                for (PolicyViolation policy : result.getViolatedPolicies()) {
                    printer.printRecord(nullIfEmpty(
                            policy.getName(),
                            policy.getSeverity(),
                            policy.getDescription(),
                            policy.getViolations(),
                            prettyRemediation(policy.getRemediation()),
                            policy.isBuildEnforced() ? "X" : "-"
                    ));
                }
            }
        }
    }

    private static Object[] nullIfEmpty(Object... values) {
        return Arrays.stream(values).sequential().map(ReportGenerator::nullIfEmpty).toArray();
    }

    private static Object nullIfEmpty(Object s) {
        if (s == null) {
            return null;
        }
        if (s.getClass() != String.class) {
            return s;
        }
        return Strings.isNullOrEmpty(s.toString()) ? null : s;
    }

    private static String prettyRemediation(String remediation) {
        return Strings.isNullOrEmpty(remediation) ? NO_REMEDIATION_ACTIONS : remediation;
    }

    private static CSVPrinter openCsv(OutputStream outputStream, String[] header) throws IOException {
        return new CSVPrinter(new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)),
                CSVFormat.Builder.create()
                        .setQuoteMode(QuoteMode.MINIMAL)
                        .setNullString(NOT_AVAILABLE)
                        .setRecordSeparator('\n')
                        .setHeader(header)
                        .build());
    }
}
