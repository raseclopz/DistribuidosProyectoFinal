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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MonitoringApp {
    private static final Logger logger = Logger.getLogger(MonitoringApp.class.getName());
    private static ZooKeeperConnector zooKeeperConnector;
    private static final int NUM_PROCESSING_SERVERS = 3;
    private static final int TOTAL_SERVERS = NUM_PROCESSING_SERVERS + 1;
    private static XYSeries[] cpuSeries = new XYSeries[TOTAL_SERVERS];
    private static double[] totalCosts = new double[TOTAL_SERVERS];
    private static final double COST_PER_HOUR = 1000; 
    private static final double SECONDS_PER_HOUR = 3600.0;
    private static JLabel[] costLabels = new JLabel[TOTAL_SERVERS];
    private static JLabel totalCostLabel;
    private static OkHttpClient client = new OkHttpClient();

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
            for (int i = 0; i < TOTAL_SERVERS; i++) {
                JLabel serverLabel = new JLabel(i == NUM_PROCESSING_SERVERS ? "Servidor Web" : "Servidor " + (i + 1), SwingConstants.CENTER);
                serverLabel.setFont(new Font("Arial", Font.BOLD, 16));
                gbc.gridx = i;
                gbc.gridy = 0;
                frame.add(serverLabel, gbc);
            }

            JPanel chartPanelContainer = new JPanel(new GridLayout(1, TOTAL_SERVERS));
            for (int i = 0; i < TOTAL_SERVERS; i++) {
                cpuSeries[i] = new XYSeries(i == NUM_PROCESSING_SERVERS ? "Backend" : "Server " + (i + 1));
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

                costLabels[i] = new JLabel("$0.00", SwingConstants.CENTER);
                costLabels[i].setFont(new Font("Arial", Font.BOLD, 16));
                gbc.gridx = i;
                gbc.gridy = 2;
                frame.add(costLabels[i], gbc);
            }

            totalCostLabel = new JLabel("$0.00", SwingConstants.CENTER);
            totalCostLabel.setFont(new Font("Arial", Font.BOLD, 16));
            gbc.gridx = TOTAL_SERVERS - 1;
            gbc.gridy = 3;
            frame.add(totalCostLabel, gbc);

            JLabel totalLabel = new JLabel("TOTAL", SwingConstants.CENTER);
            totalLabel.setFont(new Font("Arial", Font.BOLD, 16));
            gbc.gridx = TOTAL_SERVERS - 2;
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
                    for (int i = 0; i < TOTAL_SERVERS; i++) {
                        try {
                            double cpuUsage = (i == NUM_PROCESSING_SERVERS) ? getBackendCPUUsage() : zooKeeperConnector.getCPUUsage(i);
                            logger.info("CPU usage for server " + (i == NUM_PROCESSING_SERVERS ? "Backend" : "Server " + (i + 1)) + ": " + cpuUsage);
                            // Normalize CPU usage if it exceeds 100%
                            cpuUsage = Math.min(cpuUsage, 100.0);
                            cpuSeries[i].add(time, cpuUsage);
                            double costIncrement = (cpuUsage / 100.0) * (COST_PER_HOUR / SECONDS_PER_HOUR);
                            totalCosts[i] += costIncrement;
                            costLabels[i].setText(String.format("$%.2f", totalCosts[i]));
                            totalCost += totalCosts[i];
                        } catch (KeeperException | InterruptedException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                    totalCostLabel.setText(String.format("$%.2f", totalCost));
                    time++;
                }
            }, 0, 1000);
        });
    }

    private static double getBackendCPUUsage() throws IOException {
        String backendUrl = "http://localhost:8081/cpu";
        Request request = new Request.Builder().url(backendUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                double cpuUsage = Double.parseDouble(response.body().string());
                logger.info("Backend CPU usage: " + cpuUsage);
                return cpuUsage;
            } else {
                logger.warning("Failed to get CPU usage from backend. Response code: " + response.code());
                return 0.0; // Return 0 if the server is down or there's an error
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Exception occurred while getting backend CPU usage", e);
            return 0.0; // Return 0 if there's an exception
        }
    }
}
