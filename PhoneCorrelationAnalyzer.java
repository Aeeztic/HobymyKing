import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;

public class PhoneCorrelationAnalyzer extends JFrame {
    private JTextPane resultPane;

    public PhoneCorrelationAnalyzer() {
        createUI();
    }

    private void createUI() {
        setTitle("Phone Correlation Analyzer");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);

        // Title Label
        JLabel titleLabel = new JLabel("Phone Correlation Analysis");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 25)));

        // Instructions Panel
        JTextArea instructions = new JTextArea();
        instructions.setText("This program analyzes relationships between:\n"
                + "- RAM size and phone price\n"
                + "- ROM size and phone price\n"
                + "- RAM and ROM sizes\n\n"
                + "How to use:\n"
                + "1. Prepare CSV file with phone data\n"
                + "2. Click 'Upload CSV' button\n"
                + "3. View correlation results below");
        instructions.setFont(new Font("Arial", Font.PLAIN, 16));
        instructions.setEditable(false);
        instructions.setLineWrap(true);
        instructions.setWrapStyleWord(true);
        instructions.setBackground(mainPanel.getBackground());
        JScrollPane instructionScroll = new JScrollPane(instructions);
        instructionScroll.setMaximumSize(new Dimension(800, 150));
        instructionScroll.setBorder(BorderFactory.createTitledBorder("Instructions"));
        mainPanel.add(instructionScroll);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 30)));

        // Upload Button
        JButton uploadButton = new JButton("Upload CSV File");
        uploadButton.setFont(new Font("Arial", Font.BOLD, 18));
        uploadButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        uploadButton.setPreferredSize(new Dimension(220, 45));
        uploadButton.addActionListener(e -> uploadFile());
        mainPanel.add(uploadButton);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 30)));

        // Results Panel
        resultPane = new JTextPane();
        resultPane.setContentType("text/html");
        resultPane.setEditable(false);
        resultPane.setBackground(mainPanel.getBackground());
        JScrollPane resultScroll = new JScrollPane(resultPane);
        resultScroll.setPreferredSize(new Dimension(800, 300));
        resultScroll.setBorder(BorderFactory.createTitledBorder("Analysis Results"));
        mainPanel.add(resultScroll);

        add(mainPanel);
        setVisible(true);
    }

    private void uploadFile() {
        JFileChooser fileChooser = new JFileChooser();
        int chooserResult = fileChooser.showOpenDialog(this);
        if (chooserResult == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            LoadingDialog loadingDialog = new LoadingDialog(this);

            // Create and start the SwingWorker BEFORE showing the modal dialog.
            SwingWorker<ProcessingResult, Void> worker = new SwingWorker<ProcessingResult, Void>() {
                @Override
                protected ProcessingResult doInBackground() throws Exception {
                    ProcessingResult processingResult = new ProcessingResult();
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        List<Double> ramList = new ArrayList<>();
                        List<Double> romList = new ArrayList<>();
                        List<Double> priceList = new ArrayList<>();

                        // Skip the header
                        br.readLine();
                        String line;
                        int totalRows = 0;
                        int validRows = 0;

                        System.out.println("\n=== Starting file processing ===");
                        while ((line = br.readLine()) != null) {
                            totalRows++;
                            String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                            if (parts.length < 9) {
                                System.out.println("Skipping row with insufficient columns: " + line);
                                continue;
                            }
                            try {
                                // Parse RAM
                                String ramStr = parts[parts.length - 2].trim();
                                double ram = parseStorage(ramStr);

                                // Parse ROM
                                String romStr = parts[parts.length - 1].trim();
                                if (romStr.equalsIgnoreCase("Not Known")) {
                                    System.out.println("Skipping row with unknown ROM: " + line);
                                    continue;
                                }
                                double rom = parseStorage(romStr);

                                // Parse Price
                                String priceStr = parts[3].trim().replaceAll("[^\\d.]", "");
                                if (priceStr.isEmpty()) {
                                    System.out.println("Skipping row with empty price: " + line);
                                    continue;
                                }
                                double price = Double.parseDouble(priceStr);

                                ramList.add(ram);
                                romList.add(rom);
                                priceList.add(price);
                                validRows++;
                            } catch (Exception e) {
                                System.out.println("Skipping invalid row: " + line);
                                System.out.println("Error: " + e.getMessage());
                            }
                        }

                        System.out.println("\n=== Processing Summary ===");
                        System.out.println("Total rows in file: " + totalRows);
                        System.out.println("Valid rows processed: " + validRows);
                        System.out.printf("Data retention rate: %.1f%%%n", (validRows * 100.0) / totalRows);

                        if (validRows < 10) {
                            processingResult.setErrorMessage("Insufficient valid data (minimum 10 entries required)");
                            processingResult.setTotalRows(totalRows);
                            processingResult.setValidRows(validRows);
                            return processingResult;
                        }

                        System.out.println("\n=== Data Sample ===");
                        System.out.printf("First 3 entries:%n");
                        for (int i = 0; i < Math.min(3, validRows); i++) {
                            System.out.printf("RAM: %.2fGB | ROM: %.2fGB | Price: %.2f%n",
                                    ramList.get(i), romList.get(i), priceList.get(i));
                        }

                        double ramPriceCorr = calculateCorrelation(ramList, priceList);
                        double romPriceCorr = calculateCorrelation(romList, priceList);
                        double ramRomCorr = calculateCorrelation(ramList, romList);

                        processingResult.setRamPriceCorr(ramPriceCorr);
                        processingResult.setRomPriceCorr(romPriceCorr);
                        processingResult.setRamRomCorr(ramRomCorr);
                        processingResult.setTotalRows(totalRows);
                        processingResult.setValidRows(validRows);

                    } catch (IOException ex) {
                        processingResult.setErrorMessage("Error reading file: " + ex.getMessage());
                    }
                    return processingResult;
                }

                @Override
                protected void done() {
                    // Dispose the loading dialog
                    loadingDialog.dispose();
                    try {
                        ProcessingResult result = get();
                        if (result.hasError()) {
                            showError(result.getErrorMessage());
                        } else {
                            String htmlResult = buildHtmlResult(result);
                            resultPane.setText(htmlResult);
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        showError("Error processing file: " + e.getCause().getMessage());
                    }
                }
            };
            worker.execute();
            // Now show the modal dialog. This will block until loadingDialog.dispose() is called.
            loadingDialog.setVisible(true);
        }
    }

    private String buildHtmlResult(ProcessingResult result) {
        return "<html><div style='text-align: center; font-size: 16pt; padding: 20px;'>"
                + "<h2 style='color: #2c3e50;'>Correlation Results</h2>"
                + String.format("<p>Analyzed %d devices (%.1f%% of file contents)</p>",
                        result.getValidRows(),
                        (result.getValidRows() * 100.0) / result.getTotalRows())
                + "<p style='margin: 15px 0;'><b>RAM vs Price:</b> "
                + formatCorrelation(result.getRamPriceCorr()) + "</p>"
                + "<p style='margin: 15px 0;'><b>ROM vs Price:</b> "
                + formatCorrelation(result.getRomPriceCorr()) + "</p>"
                + "<p style='margin: 15px 0;'><b>RAM vs ROM:</b> "
                + formatCorrelation(result.getRamRomCorr()) + "</p>"
                + "</div></html>";
    }

    private double parseStorage(String input) throws IllegalArgumentException {
        try {
            String cleanedInput = input.replaceAll(",", "").replaceAll("\"", "");
            Pattern pattern = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z]+)");
            Matcher matcher = pattern.matcher(cleanedInput);

            if (!matcher.find()) {
                throw new IllegalArgumentException("Invalid storage format: " + input);
            }

            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2).toUpperCase();
            double result;

            switch (unit) {
                case "KB":
                    result = value / (1024 * 1024);
                    break;
                case "MB":
                    result = value / 1024;
                    break;
                case "GB":
                    result = value;
                    break;
                case "TB":
                    result = value * 1024;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown unit: " + unit);
            }

            System.out.printf("Storage parsed: %-15s → %8.2f GB%n", input, result);
            return result;
        } catch (Exception e) {
            System.out.println("Failed to parse storage: " + input);
            throw e;
        }
    }

    private double calculateCorrelation(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.size() < 2) {
            System.out.println("Insufficient data for correlation calculation");
            return 0;
        }

        System.out.println("\n=== Correlation Calculation ===");
        System.out.println("Using " + x.size() + " data points");
        System.out.printf("First 5 X values: %s%n", x.subList(0, Math.min(5, x.size())));
        System.out.printf("First 5 Y values: %s%n", y.subList(0, Math.min(5, y.size())));

        double sumX = 0, sumY = 0, sumXY = 0;
        double sumX2 = 0, sumY2 = 0;
        int n = x.size();

        for (int i = 0; i < n; i++) {
            double xi = x.get(i);
            double yi = y.get(i);
            sumX += xi;
            sumY += yi;
            sumXY += xi * yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
        }

        double numerator = sumXY - (sumX * sumY) / n;
        double denominator = Math.sqrt((sumX2 - sumX * sumX / n) * (sumY2 - sumY * sumY / n));

        if (denominator == 0) {
            System.out.println("Undefined correlation (constant values)");
            return 0;
        }

        double correlation = numerator / denominator;
        System.out.printf("Final correlation: %.4f%n", correlation);
        return correlation;
    }

    private String formatCorrelation(double value) {
        String category;
        String color;
        if (value >= 0.7) {
            category = "Strong Positive";
            color = "#27ae60";
        } else if (value >= 0.3) {
            category = "Moderate Positive";
            color = "#2ecc71";
        } else if (value > 0) {
            category = "Weak Positive";
            color = "#3498db";
        } else if (value <= -0.7) {
            category = "Strong Negative";
            color = "#c0392b";
        } else if (value <= -0.3) {
            category = "Moderate Negative";
            color = "#e74c3c";
        } else {
            category = "Weak Negative";
            color = "#e67e22";
        }

        return String.format("<span style='color: %s'>%.2f (%s)</span>", color, value, category);
    }

    private void showError(String message) {
        String errorHtml = "<html><div style='text-align: center; color: #c0392b; font-size: 14pt; padding: 20px;'>"
                + "⚠️ " + message + "</div></html>";
        resultPane.setText(errorHtml);
    }

    // A simple modal loading dialog with an indeterminate progress bar.
    private static class LoadingDialog extends JDialog {
        public LoadingDialog(Frame parent) {
            super(parent, "Processing", true);
            setSize(300, 150);
            setLocationRelativeTo(parent);
            setLayout(new BorderLayout());
            setUndecorated(true);

            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            panel.setBackground(new Color(240, 240, 240));

            JLabel label = new JLabel("Processing file...");
            label.setFont(new Font("Arial", Font.BOLD, 14));
            label.setAlignmentX(Component.CENTER_ALIGNMENT);

            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setPreferredSize(new Dimension(200, 20));
            progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

            panel.add(label);
            panel.add(Box.createRigidArea(new Dimension(0, 15)));
            panel.add(progressBar);

            add(panel, BorderLayout.CENTER);
            getRootPane().setBorder(BorderFactory.createLineBorder(Color.GRAY));
        }
    }

    // A helper class to hold the results of the file processing.
    private static class ProcessingResult {
        private double ramPriceCorr;
        private double romPriceCorr;
        private double ramRomCorr;
        private int totalRows;
        private int validRows;
        private String errorMessage;

        public boolean hasError() { return errorMessage != null; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String msg) { errorMessage = msg; }

        public double getRamPriceCorr() { return ramPriceCorr; }
        public void setRamPriceCorr(double val) { ramPriceCorr = val; }
        public double getRomPriceCorr() { return romPriceCorr; }
        public void setRomPriceCorr(double val) { romPriceCorr = val; }
        public double getRamRomCorr() { return ramRomCorr; }
        public void setRamRomCorr(double val) { ramRomCorr = val; }
        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int val) { totalRows = val; }
        public int getValidRows() { return validRows; }
        public void setValidRows(int val) { validRows = val; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PhoneCorrelationAnalyzer());
    }
}
