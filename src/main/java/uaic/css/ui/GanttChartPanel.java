package uaic.css.ui;

import uaic.css.model.simulation.SimulationResult;
import uaic.css.model.simulation.ExecutionLogEntry;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

public class GanttChartPanel extends JPanel {

    private static final int ROW_HEIGHT = 70;
    private static final int HEADER_WIDTH = 120;
    private static final int TIME_UNIT_WIDTH = 30;
    private static final int TOP_MARGIN = 40;
    private static final int BOTTOM_MARGIN = 40;
    private static final int RIGHT_MARGIN = 20;

    private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 12);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 10);
    private static final Font TIME_FONT = new Font("SansSerif", Font.PLAIN, 9);

    private final SimulationResult result;
    private final int processorCount;
    private final Map<String, Color> processColors;

    public GanttChartPanel(SimulationResult result, int processorCount) {
        this.result = result;
        this.processorCount = processorCount;
        this.processColors = new HashMap<>();
        assignColors();

        int totalWidth = HEADER_WIDTH + (result.totalTime() + 2) * TIME_UNIT_WIDTH + RIGHT_MARGIN;
        int totalHeight = TOP_MARGIN + (processorCount + 1) * ROW_HEIGHT + BOTTOM_MARGIN;
        setPreferredSize(new Dimension(totalWidth, totalHeight));
        setBackground(Color.WHITE);
    }

    private void assignColors() {
        Color[] palette = {
                new Color(66, 133, 244),   // Blue
                new Color(219, 68, 55),    // Red
                new Color(244, 180, 0),    // Yellow
                new Color(15, 157, 88),    // Green
                new Color(171, 71, 188),   // Purple
                new Color(255, 112, 67),   // Orange
                new Color(0, 172, 193),    // Cyan
                new Color(124, 179, 66),   // Light Green
                new Color(255, 167, 38),   // Amber
                new Color(126, 87, 194),   // Deep Purple
        };

        int colorIndex = 0;
        for (ExecutionLogEntry entry : result.logEntries()) {
            String processName = extractProcessName(entry.label());
            if (!processColors.containsKey(processName)) {
                processColors.put(processName, palette[colorIndex % palette.length]);
                colorIndex++;
            }
        }
    }

    private String extractProcessName(String label) {
        // Extract process name from labels like "SysCall(P1)" → "P1", "Load P1" → "P1", "Save P1" → "P1"
        if (label.startsWith("SysCall(") && label.endsWith(")")) {
            return label.substring("SysCall(".length(), label.length() - 1);
        }
        if (label.startsWith("Load ")) {
            return label.substring(5);
        }
        if (label.startsWith("Save ")) {
            return label.substring(5);
        }
        return label;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawTimeAxis(g2);
        drawRowLabels(g2);
        drawGrid(g2);
        drawEntries(g2);
        drawLegend(g2);
    }

    private void drawTimeAxis(Graphics2D g2) {
        g2.setFont(TIME_FONT);
        g2.setColor(Color.DARK_GRAY);

        int maxTime = result.totalTime();
        for (int t = 0; t <= maxTime; t++) {
            int x = HEADER_WIDTH + t * TIME_UNIT_WIDTH;
            int y = TOP_MARGIN - 5;

            // Draw tick marks at every time unit, labels every 5
            g2.drawLine(x, y, x, y + 5);
            if (t % 5 == 0 || t == maxTime) {
                String label = String.valueOf(t);
                FontMetrics fm = g2.getFontMetrics();
                int labelWidth = fm.stringWidth(label);
                g2.drawString(label, x - labelWidth / 2, y - 2);
            }
        }
    }

    private void drawRowLabels(Graphics2D g2) {
        g2.setFont(HEADER_FONT);
        g2.setColor(Color.BLACK);

        for (int i = 0; i < processorCount; i++) {
            int y = TOP_MARGIN + i * ROW_HEIGHT + ROW_HEIGHT / 2 + 5;
            g2.drawString("CPU " + i, 10, y);
        }

        int diskRowY = TOP_MARGIN + processorCount * ROW_HEIGHT + ROW_HEIGHT / 2 + 5;
        g2.drawString("Disk", 10, diskRowY);
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(new Color(230, 230, 230));

        int totalRows = processorCount + 1; // processors + disk

        // Horizontal lines
        for (int i = 0; i <= totalRows; i++) {
            int y = TOP_MARGIN + i * ROW_HEIGHT;
            g2.drawLine(HEADER_WIDTH, y, getWidth() - RIGHT_MARGIN, y);
        }

        // Vertical time lines
        for (int t = 0; t <= result.totalTime(); t++) {
            int x = HEADER_WIDTH + t * TIME_UNIT_WIDTH;
            g2.drawLine(x, TOP_MARGIN, x, TOP_MARGIN + totalRows * ROW_HEIGHT);
        }
    }

    private void drawEntries(Graphics2D g2) {
        for (ExecutionLogEntry entry : result.logEntries()) {
            int row;
            if (entry.processorId() == ExecutionLogEntry.DISK_PROCESSOR_ID) {
                row = processorCount; // disk row
            } else {
                row = entry.processorId();
            }

            int x = HEADER_WIDTH + entry.startTime() * TIME_UNIT_WIDTH;
            int y = TOP_MARGIN + row * ROW_HEIGHT + 3;
            int width = (entry.endTime() - entry.startTime()) * TIME_UNIT_WIDTH;
            int height = ROW_HEIGHT - 6;

            // Get color based on process name
            String processName = extractProcessName(entry.label());
            Color baseColor = processColors.getOrDefault(processName, Color.GRAY);

            // Adjust color based on entry type
            Color fillColor;
            switch (entry.type()) {
                case CPU_BURST -> fillColor = baseColor;
                case SYSCALL -> fillColor = baseColor.darker();
                case DISK_LOAD -> fillColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 150);
                case DISK_SAVE -> fillColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 100);
                default -> fillColor = Color.LIGHT_GRAY;
            }

            // Draw filled rectangle
            g2.setColor(fillColor);
            g2.fillRoundRect(x, y, width, height, 4, 4);

            // Draw border
            g2.setColor(baseColor.darker());
            g2.drawRoundRect(x, y, width, height, 4, 4);

            // Draw label text
            g2.setFont(LABEL_FONT);
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            String label = entry.label();

            // Truncate label if too wide
            while (fm.stringWidth(label) > width - 4 && label.length() > 1) {
                label = label.substring(0, label.length() - 1);
            }

            if (fm.stringWidth(label) <= width - 4) {
                int textX = x + (width - fm.stringWidth(label)) / 2;
                int textY = y + (height + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, textX, textY);
            }
        }
    }

    private void drawLegend(Graphics2D g2) {
        int legendY = TOP_MARGIN + (processorCount + 1) * ROW_HEIGHT + 15;
        int legendX = HEADER_WIDTH;

        g2.setFont(HEADER_FONT);
        g2.setColor(Color.BLACK);
        g2.drawString("Legend:", legendX, legendY);

        g2.setFont(LABEL_FONT);
        legendX += 60;

        for (Map.Entry<String, Color> entry : processColors.entrySet()) {
            g2.setColor(entry.getValue());
            g2.fillRect(legendX, legendY - 10, 15, 12);
            g2.setColor(Color.BLACK);
            g2.drawRect(legendX, legendY - 10, 15, 12);
            g2.drawString(entry.getKey(), legendX + 20, legendY);
            legendX += 70;
        }
    }

    /**
     * Displays the Gantt chart in a JFrame window.
     */
    public void display() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Process Scheduling Simulation - Gantt Chart");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JScrollPane scrollPane = new JScrollPane(this);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

            frame.add(scrollPane);
            frame.setSize(900, 400);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
