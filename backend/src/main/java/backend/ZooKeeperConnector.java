package backend;

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

    public void connect(String host, String zNodePath, String serverUrl) throws IOException, KeeperException, InterruptedException {
        zooKeeper = new ZooKeeper(host, 5000, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                // Watcher implementation
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

    public void close() throws InterruptedException {
        zooKeeper.close();
    }

    public List<String> getActiveServers() throws KeeperException, InterruptedException {
        List<String> servers = zooKeeper.getChildren("/processing_servers", false);
        return servers.stream().map(server -> "http://" + server.replace("_", ":")).collect(Collectors.toList());
    }
}
