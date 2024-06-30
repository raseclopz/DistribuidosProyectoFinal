package processing_server;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ZooKeeperConnector {
    private ZooKeeper zooKeeper;
    private Set<Integer> usedPorts = new HashSet<>();
    private int basePort = 8082; // Base port for processing servers

    public void connect(String host, String zNodePath, String serverUrl) throws IOException, KeeperException, InterruptedException {
        zooKeeper = new ZooKeeper(host, 5000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                try {
                    handleWatchEvent(event);
                } catch (KeeperException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Stat stat = zooKeeper.exists(zNodePath, false);
        if (stat == null) {
            zooKeeper.create(zNodePath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }

        String serverNodePath = zNodePath + "/" + serverUrl.replace("http://", "").replace(":", "_");
        if (zooKeeper.exists(serverNodePath, false) == null) {
            zooKeeper.create(serverNodePath, serverUrl.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        }

        // Register the port in used ports set
        int port = Integer.parseInt(serverUrl.split(":")[1]);
        usedPorts.add(port);
    }

    private void handleWatchEvent(WatchedEvent event) throws KeeperException, InterruptedException {
        if (event.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
            List<String> servers = zooKeeper.getChildren("/processing_servers", true);
        }
    }

    private int getNextAvailablePort() {
        for (int port = basePort; port < basePort + 100; port++) { // Check next 100 ports
            if (!usedPorts.contains(port)) {
                usedPorts.add(port);
                return port;
            }
        }
        return -1; // No available port found
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }

    public List<String> getActiveServers() throws KeeperException, InterruptedException {
        List<String> servers = zooKeeper.getChildren("/processing_servers", false);
        return servers.stream().map(server -> "http://" + server.replace("_", ":")).collect(Collectors.toList());
    }
}
