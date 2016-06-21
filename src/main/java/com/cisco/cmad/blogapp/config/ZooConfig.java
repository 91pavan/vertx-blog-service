package com.cisco.cmad.blogapp.config;

import java.io.IOException;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher.Event.KeeperState;

public class ZooConfig {
	
	private ZooKeeper zoo;
	private String baseZNode = "config";
	
	public ZooConfig(String host) {
		try {
			this.connect(host);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	  // Method to connect to the zookeeper ensemble.
	  public ZooKeeper connect(String host) throws IOException,InterruptedException {
		
	      zoo = new ZooKeeper(host, 10000, watchedEvent -> {
				if( watchedEvent.getState() == KeeperState.Disconnected) {
					System.out.println("Disconnected");
				}
				System.out.println("Watched event path: " + watchedEvent.getPath());
				System.out.println("Watched event: " + watchedEvent);
			});
			
	      return zoo;
	   }

	   // Method to disconnect from the zookeeper server
	   public void close() throws InterruptedException {
	      zoo.close();
	   }
	   
	   
	   // get the required ZNode
	   public byte[] readZnode(String path) throws KeeperException, InterruptedException {
		   if(path != null) {
				String newPath = "/" + baseZNode + "/" + path;
			    return zoo.getData(newPath, true, zoo.exists(newPath, true));
		   }
		return null;
		}
	   
	   // read DB name from Zookeeper
	   public String readDbNameConfig() {
		   String s = null;
		   try {
			s = new String(readZnode(ConfigKeys.DB_NAME));
		} catch (KeeperException | InterruptedException e) {
			e.printStackTrace();
		}
		   return s;
	   }
	   
	   // read DB host from zookeeper
	   public String readDbHostConfig() {
		   String s = null;
		   try {
			s = new String(readZnode(ConfigKeys.MONGO_HOST));
		} catch (KeeperException | InterruptedException e) {
			e.printStackTrace();
		}
		   return s;
	   }
	   
	   // read DB port from zookeeper
	   public String readDbPortConfig() {
		   String s = null;
		   try {
			s = new String(readZnode(ConfigKeys.MONGO_PORT));
		} catch (KeeperException | InterruptedException e) {
			e.printStackTrace();
		}
		   return s;
	   }
	   
	   // read APP port from zookeeper
	   public String readAppPortConfig() {
		   String s = null;
		   try {
			s = new String(readZnode(ConfigKeys.BLOG_APP_PORT));
		} catch (KeeperException | InterruptedException e) {
			e.printStackTrace();
		}
		   return s;
	   }
}
