package com.cisco.cmad.blogapp.config;

import java.io.InputStream;
import java.util.Properties;

import com.cisco.cmad.blogapp.vertx_blog_service.BlogServiceApp;

public class AppConfig {

	static Properties prop = new Properties();
	static InputStream input = null;
	
	public static Properties loadConfig() {
	
        try {
    		input = BlogServiceApp.class.getClassLoader().getResourceAsStream("config.properties");
    		
    		//load a properties file from class path
    		prop.load(input);
		} catch (Exception e) {
			
			e.printStackTrace();
		}
		return prop;
	}

	public String getMongoHostConfig(Properties prop) {
		return prop.getProperty(AppConfigKeys.MONGO_HOST, "localhost");
	}
	
	public String getMongoPortConfig(Properties prop) {
		return prop.getProperty(AppConfigKeys.MONGO_PORT, "27017");
	}
	
	public String getMongoDbConfig(Properties prop) {
		return prop.getProperty(AppConfigKeys.DB_NAME, "cmad");
	}
	
	public String getAppPortConfig(Properties prop) {
		return prop.getProperty(AppConfigKeys.APP_PORT, "8085");
	}
	
}
