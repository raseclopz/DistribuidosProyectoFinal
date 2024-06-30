package backend;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.File;
import java.util.Scanner;

public class BackendServer {
    private static ZooKeeperConnector zooKeeperConnector;
    private static List<String> texts = new ArrayList<>();
    private static List<String> textNames = new ArrayList<>();
    private static String zNodePath = "/processing_servers";

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        // Conectar a ZooKeeper
        zooKeeperConnector = new ZooKeeperConnector();
        zooKeeperConnector.connect("localhost", zNodePath, "");

        // Cargar los textos en memoria
        loadTexts();

        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/consulta", new ConsultaHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("BackendServer running on port 8081");
    }

    private static void loadTexts() throws IOException {
        Path resourceDirectory = Paths.get("src", "main", "resources", "texts");
        File folder = new File(resourceDirectory.toUri());
        File[] listOfFiles = folder.listFiles((dir, name) -> name.endsWith(".txt"));

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
                texts.add(content);
                textNames.add(file.getName().replace(".txt", ""));  // Guardar el nombre del libro sin la extensión
                System.out.println("Loaded text from: " + file.getPath());  // Mensaje de depuración
            }
        } else {
            System.out.println("No files found in texts folder.");  // Mensaje de depuración
        }
    }

    static class ConsultaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            int n = Integer.parseInt(query.split("=")[1]);

            List<String> activeServers;
            try {
                activeServers = zooKeeperConnector.getActiveServers();
            } catch (KeeperException | InterruptedException e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1); // Internal Server Error
                return;
            }

            if (activeServers.isEmpty()) {
                System.out.println("No active servers available.");
                exchange.sendResponseHeaders(503, -1);  // Service Unavailable
                return;
            }

            List<String> results = new ArrayList<>();
            long startTime = System.currentTimeMillis();

            // Redistribuir los textos entre los servidores activos
            int numActiveServers = activeServers.size();
            int textsPerServer = (int) Math.ceil((double) texts.size() / numActiveServers);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < numActiveServers; i++) {
                final int serverIndex = i;
                final List<String> textSlice = new ArrayList<>(texts.subList(serverIndex * textsPerServer, Math.min((serverIndex + 1) * textsPerServer, texts.size())));
                final List<String> textNameSlice = new ArrayList<>(textNames.subList(serverIndex * textsPerServer, Math.min((serverIndex + 1) * textsPerServer, textNames.size())));

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        String serverUrl = activeServers.get(serverIndex) + "/process?n=" + n;
                        HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                        connection.setConnectTimeout(10000);
                        connection.setReadTimeout(50000);

                        OutputStream os = connection.getOutputStream();
                        os.write(serialize(textSlice, textNameSlice).getBytes());
                        os.close();

                        if (connection.getResponseCode() == 200) {
                            Scanner scanner = new Scanner(connection.getInputStream());
                            while (scanner.hasNextLine()) {
                                synchronized (results) {
                                    results.add(scanner.nextLine());
                                }
                            }
                            scanner.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                futures.add(future);
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            try {
                allFutures.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            StringBuilder response = new StringBuilder();
            response.append("Numero de servidores activos: ").append(numActiveServers).append("\n");
            response.append("Tiempo de procesamiento: ").append(duration / 1000).append(" segundos con ").append(duration % 1000).append(" milisegundos\n");

            Map<String, Set<String>> phraseToTexts = new HashMap<>();
            for (String result : results) {
                String[] parts = result.split(" aparece en ");
                if (parts.length == 2) {
                    String phrase = parts[0].trim();
                    String[] texts = parts[1].split(" y ");
                    for (String text : texts) {
                        phraseToTexts.putIfAbsent(phrase, new HashSet<>());
                        phraseToTexts.get(phrase).add(text.trim());
                    }
                }
            }

            for (Map.Entry<String, Set<String>> entry : phraseToTexts.entrySet()) {
                String phrase = entry.getKey();
                Set<String> texts = entry.getValue();
                response.append("\"").append(phrase).append("\" aparece en:\n");
                for (String text : texts) {
                    response.append("\t- \"").append(text).append("\"\n");
                }
                response.append("\n");
            }

            exchange.sendResponseHeaders(200, response.toString().getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.toString().getBytes());
            os.close();
        }

        private String serialize(List<String> texts, List<String> textNames) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < texts.size(); i++) {
                sb.append("##TEXT##").append(textNames.get(i)).append("##CONTENT##").append(texts.get(i)).append("##END##");
            }
            return sb.toString();
        }
    }
}
