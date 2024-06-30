package monitoring;

import org.apache.zookeeper.KeeperException;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MonitoringApp {
    private static ZooKeeperConnector zooKeeperConnector;
    private static final int NUM_SERVERS = 4;
    private static XYSeries[] cpuSeries = new XYSeries[NUM_SERVERS];
    private static double[] totalCosts = new double[NUM_SERVERS];
    private static double factor = 0.5;
    private static JLabel[] costLabels = new JLabel[NUM_SERVERS];
    private static JLabel totalCostLabel;

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        zooKeeperConnector = new ZooKeeperConnector();
        zooKeeperConnector.connect("localhost", "/processing_servers", "");

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("CPU Monitoring");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Labels for Server Names
            for (int i = 0; i < NUM_SERVERS; i++) {
                JLabel serverLabel = new JLabel("Servidor " + (i + 1), SwingConstants.CENTER);
                serverLabel.setFont(new Font("Arial", Font.BOLD, 16));
                gbc.gridx = i;
                gbc.gridy = 0;
                frame.add(serverLabel, gbc);
            }

            JPanel chartPanelContainer = new JPanel(new GridLayout(1, NUM_SERVERS));
            for (int i = 0; i < NUM_SERVERS; i++) {
                cpuSeries[i] = new XYSeries("Server " + (i + 1));
                XYSeriesCollection dataset = new XYSeriesCollection(cpuSeries[i]);
                JFreeChart chart = ChartFactory.createXYLineChart(
                        "Historial de uso de CPU",
                        "Tiempo (s)",
                        "CPU (%)",
                        dataset,
                        PlotOrientation.VERTICAL,
                        false,
                        false,
                        false
                );

                XYPlot plot = chart.getXYPlot();
                XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
                renderer.setSeriesShapesVisible(0, false);
                plot.setRenderer(renderer);

                ChartPanel chartPanel = new ChartPanel(chart);
                chartPanel.setPreferredSize(new Dimension(200, 200));
                chartPanelContainer.add(chartPanel);

                gbc.gridx = i;
                gbc.gridy = 1;
                frame.add(chartPanel, gbc);

                costLabels[i] = new JLabel("$0", SwingConstants.CENTER);
                costLabels[i].setFont(new Font("Arial", Font.BOLD, 16));
                gbc.gridx = i;
                gbc.gridy = 2;
                frame.add(costLabels[i], gbc);
            }

            totalCostLabel = new JLabel("$0", SwingConstants.CENTER);
            totalCostLabel.setFont(new Font("Arial", Font.BOLD, 16));
            gbc.gridx = NUM_SERVERS - 1;
            gbc.gridy = 3;
            frame.add(totalCostLabel, gbc);

            JLabel totalLabel = new JLabel("TOTAL", SwingConstants.CENTER);
            totalLabel.setFont(new Font("Arial", Font.BOLD, 16));
            gbc.gridx = NUM_SERVERS - 2;
            gbc.gridy = 3;
            frame.add(totalLabel, gbc);

            frame.pack();
            frame.setVisible(true);

            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                private int time = 0;

                @Override
                public void run() {
                    double totalCost = 0.0;
                    for (int i = 0; i < NUM_SERVERS; i++) {
                        try {
                            double cpuUsage = zooKeeperConnector.getCPUUsage(i);
                            cpuSeries[i].add(time, cpuUsage);
                            totalCosts[i] += cpuUsage * factor;
                            costLabels[i].setText(String.format("$%.2f", totalCosts[i]));
                            totalCost += totalCosts[i];
                        } catch (KeeperException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    totalCostLabel.setText(String.format("$%.2f", totalCost));
                    time++;
                }
            }, 0, 1000);
        });
    }
}
