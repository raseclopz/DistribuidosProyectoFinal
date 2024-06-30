package backend;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ZooKeeperConnector {
    private ZooKeeper zooKeeper;
    private static final Logger logger = Logger.getLogger(ZooKeeperConnector.class.getName());

    public void connect(String host, String zNodePath, String serverUrl) throws IOException, KeeperException, InterruptedException {
        zooKeeper = new ZooKeeper(host, 5000, event -> {
            // Watcher implementation
        });

        

        Stat stat = zooKeeper.exists(zNodePath, false);
        if (stat == null) {
            zooKeeper.create(zNodePath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }

        if (!serverUrl.isEmpty()) {
            String serverNodePath = zNodePath + "/" + serverUrl.replace("http://", "").replace(":", "_");
            if (zooKeeper.exists(serverNodePath, false) == null) {
                zooKeeper.create(serverNodePath, serverUrl.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                logger.info("Server registered at path: " + serverNodePath);
            }
        }
    }
    

    public void close() throws InterruptedException {
        zooKeeper.close();
    }

    public List<String> getActiveServers() throws KeeperException, InterruptedException {
        List<String> servers = zooKeeper.getChildren("/processing_servers", false);
        logger.info("Active servers: " + servers);
        return servers.stream().map(server -> "http://" + server.replace("_", ":")).collect(Collectors.toList());
    }
}
