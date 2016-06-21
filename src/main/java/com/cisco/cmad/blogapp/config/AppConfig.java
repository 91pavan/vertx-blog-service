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

	public String readDbHostConfig() {
		return prop.getProperty(ConfigKeys.MONGO_HOST, "localhost");
	}
	
	public String readDbPortConfig() {
		return prop.getProperty(ConfigKeys.MONGO_PORT, "27017");
	}
	
	public String radDbNameConfig() {
		return prop.getProperty(ConfigKeys.DB_NAME, "cmad");
	}
	
	public String readAppPortConfig() {
		return prop.getProperty(ConfigKeys.BLOG_APP_PORT, "8085");
	}
	
}
