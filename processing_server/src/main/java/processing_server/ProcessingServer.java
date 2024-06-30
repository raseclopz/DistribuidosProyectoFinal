package processing_server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;

public class ProcessingServer {
    private static ZooKeeperConnector zooKeeperConnector;
    private static String zNodePath = "/processing_servers";
    private static SystemInfo systemInfo = new SystemInfo();
    private static CentralProcessor processor = systemInfo.getHardware().getProcessor();

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage: java -cp <jar-file> processing_server.ProcessingServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        // Conectar a ZooKeeper
        zooKeeperConnector = new ZooKeeperConnector();
        zooKeeperConnector.connect("localhost", zNodePath, "localhost:" + port);

        // Crear servidor HTTP
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/process", new ProcessHandler());
        server.createContext("/cpu", new CpuHandler());
        server.setExecutor(Executors.newFixedThreadPool(10)); // Crear un pool de hilos con 10 hilos
        server.start();
        System.out.println("ProcessingServer running on port " + port);
    }

    static class ProcessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // Leer los datos enviados por el backend
                Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A");
                String body = scanner.hasNext() ? scanner.next() : "";
                scanner.close();

                // Deserializar los datos recibidos
                Map<String, String> texts = deserialize(body);

                // Procesar los textos
                List<String> results = processTexts(texts, getN(exchange.getRequestURI().getQuery()));

                // Enviar la respuesta
                String response = String.join("\n", results);
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
            }
        }

        private int getN(String query) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] pair = param.split("=");
                if ("n".equals(pair[0])) {
                    return Integer.parseInt(pair[1]);
                }
            }
            return 3; // Valor por defecto
        }

        private List<String> processTexts(Map<String, String> texts, int n) {
            List<String> results = new ArrayList<>();
            Map<String, Set<String>> phraseToTexts = new HashMap<>();

            for (Map.Entry<String, String> entry : texts.entrySet()) {
                String textName = entry.getKey();
                String content = entry.getValue();
                String[] words = content.split("\\s+");

                for (int i = 0; i <= words.length - n; i++) {
                    String phrase = String.join(" ", Arrays.copyOfRange(words, i, i + n)).toLowerCase().replaceAll("[^a-z0-9 ]", "");
                    phraseToTexts.putIfAbsent(phrase, new HashSet<>());
                    phraseToTexts.get(phrase).add(textName);
                }
            }

            for (Map.Entry<String, Set<String>> entry : phraseToTexts.entrySet()) {
                if (entry.getValue().size() > 1) {
                    results.add(entry.getKey() + " aparece en " + String.join(" y ", entry.getValue()));
                }
            }

            return results;
        }

        private Map<String, String> deserialize(String data) {
            Map<String, String> texts = new HashMap<>();
            String[] parts = data.split("##END##");
            for (String part : parts) {
                if (!part.trim().isEmpty()) {
                    String[] sections = part.split("##CONTENT##");
                    if (sections.length == 2) {
                        String textName = sections[0].replace("##TEXT##", "").trim();
                        String content = sections[1].trim();
                        texts.put(textName, content);
                    }
                }
            }
            return texts;
        }
    }

    static class CpuHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                double cpuUsage = getCpuUsage();
                String response = String.valueOf(cpuUsage);
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
            }
        }

        private double getCpuUsage() {
            double[] load = processor.getSystemLoadAverage(3);
            return load[0] >= 0 ? load[0] * 100 : 0.0;  // OSHI devuelve la carga en fracción de 1.0
        }
    }
}
