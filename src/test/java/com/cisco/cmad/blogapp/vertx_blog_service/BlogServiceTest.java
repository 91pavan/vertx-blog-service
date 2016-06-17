package com.cisco.cmad.blogapp.vertx_blog_service;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.cisco.cmad.blogapp.utils.Base64Util;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class BlogServiceTest
{
	Vertx vertx = null;
	int port = 0;
	MongoClient mongo = null;
	private String userName = "dan";
	private String password = "password";
	
	private static MongodProcess MONGO;
    private static int MONGO_PORT = 12345;
    
    Base64Util base64Util = new Base64Util();
    
    @BeforeClass
    public static void initialize() throws IOException {
      MongodStarter starter = MongodStarter.getDefaultInstance();
      IMongodConfig mongodConfig = new MongodConfigBuilder()
          .version(Version.Main.PRODUCTION)
          .net(new Net(MONGO_PORT, Network.localhostIsIPv6()))
          .build();
      MongodExecutable mongodExecutable =
            starter.prepare(mongodConfig);
     MONGO = mongodExecutable.start();
          
    }

    @AfterClass
    public static void shutdown() {  MONGO.stop(); }
	
	
	@Before
	public void setUp(TestContext context) throws IOException {
	  vertx = Vertx.vertx();
	  ServerSocket socket = new ServerSocket(0);
	  port = 8085;
	  socket.close();
	  mongo = MongoClient.createShared(vertx, new JsonObject().put("db_name", "blogapp_test")
			  .put("connection_string", "mongodb://localhost:" + MONGO_PORT));

	  DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
           .put("http.port", 8085)
          .put("db_name", "blogapp_test")
          .put("connection_string",
              "mongodb://localhost:" + MONGO_PORT)
    		  );
	  vertx.deployVerticle(BlogServiceApp.class.getName(), options, context.asyncAssertSuccess());
	  insertBlogUsers();
	}
	
	@After
	public void tearDown(TestContext context) {
	  vertx.close(context.asyncAssertSuccess());
	}
	
	public void insertBlogUsers() {
		
		JsonObject blogUsers = new JsonObject().put("userName", userName).put("password", password);
		mongo.findOne("blog_users", blogUsers, null, lookup -> {
            // error handling
            if (lookup.failed()) {
              return;
            }
            if(lookup.result() != null) {
                // already exists
                // do nothing
            } else {
  		  
	  		  mongo.save("blog_users", blogUsers, insert -> {
		          // error handling
	              if (insert.failed()) {
	                return;
	              }
	             
	              System.out.println("User object inserted to test_blog_users table");
	              blogUsers.put("_id", insert.result());
	              
	            });
	  		  
            }
            });
	}
	
	@Test
	public void getZeroBlogRecords(TestContext context) {
		Async async = context.async();
		HttpClientOptions options = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(port);

		HttpClient client = vertx.createHttpClient(options);

		client.request(HttpMethod.GET, "/Services/rest/blogs" , response -> {
			context.assertEquals(response.statusCode(), 404);
        	async.complete();
			}).putHeader("Authorization", base64Util.encode(userName, password)).end();

		
		System.out.println(base64Util.encode(userName, password));
		  
	}
}