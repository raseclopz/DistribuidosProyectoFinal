package monitoring;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ZooKeeperConnector {
    private ZooKeeper zooKeeper;
    private OkHttpClient client = new OkHttpClient();

    public void connect(String host, String zNodePath, String serverUrl) throws IOException, KeeperException, InterruptedException {
        zooKeeper = new ZooKeeper(host, 5000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                // Implementación del Watcher
            }
        });

        Stat stat = zooKeeper.exists(zNodePath, false);
        if (stat == null) {
            zooKeeper.create(zNodePath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }

        if (!serverUrl.isEmpty()) {
            String serverNodePath = zNodePath + "/" + serverUrl.replace("http://", "").replace(":", "_");
            if (zooKeeper.exists(serverNodePath, false) == null) {
                zooKeeper.create(serverNodePath, serverUrl.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            }
        }
    }

    public double getCPUUsage(int serverIndex) throws KeeperException, InterruptedException {
        List<String> servers = zooKeeper.getChildren("/processing_servers", false);
        if (serverIndex >= 0 && serverIndex < servers.size()) {
            String server = servers.get(serverIndex);
            String serverUrl = "http://" + server.replace("_", ":") + "/cpu";
            Request request = new Request.Builder().url(serverUrl).build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return Double.parseDouble(response.body().string());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return 0.0;
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }
}
