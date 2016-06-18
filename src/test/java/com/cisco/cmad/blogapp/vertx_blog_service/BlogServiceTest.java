package com.cisco.cmad.blogapp.vertx_blog_service;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.cisco.cmad.blogapp.utils.Base64Util;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.runners.MethodSorters;


@RunWith(VertxUnitRunner.class)
public class BlogServiceTest 
{
	
	Vertx vertx = null;
	int port = 0;
	MongoClient mongo = null;
	private String userName = "dan";
	private String password = "password";
	
    private static int MONGO_PORT = 12345;
    private static final MongodStarter starter = MongodStarter.getDefaultInstance();

    private MongodExecutable _mongodExe;
    private MongodProcess _mongod;

    private MongoClient _mongo;
    
    Base64Util base64Util = new Base64Util();
    

	
	@Before
	public void setUp(TestContext context) throws IOException {
		
	 _mongodExe = starter.prepare(new MongodConfigBuilder()
         .version(Version.Main.PRODUCTION)
         .net(new Net(MONGO_PORT, false))
         .build());
     _mongod = _mongodExe.start();

	  vertx = Vertx.vertx();
	  ServerSocket socket = new ServerSocket(0);
	  port = 8085;
	  socket.close();
	  
	  mongo = MongoClient.createShared(vertx, new JsonObject().put("db_name", "blogapp_test")
			  .put("connection_string", "mongodb://localhost:" + MONGO_PORT));

	  DeploymentOptions options = new DeploymentOptions().setWorker(true)
      .setConfig(new JsonObject()
           .put("http.port", 8085)
          .put("db_name", "blogapp_test")
          .put("connection_string",
              "mongodb://localhost:" + MONGO_PORT)
    		  );
	  vertx.deployVerticle(BlogServiceApp.class.getName(), options, context.asyncAssertSuccess());
	  // firstInsertSingleBlogUser();
	}
	
	@After
	public void tearDown(TestContext context) {
	  vertx.close(context.asyncAssertSuccess());
	  if(_mongod != null) {
		  mongo.close();
		  _mongod.stop();
	      _mongodExe.stop();
	  }
	}
	
	
	@Test
	public void findBlogUser(TestContext context) {
		
		JsonObject blogUsers = new JsonObject().put("userName", userName).put("password", password);

		mongo.findOne("blog_users", blogUsers, null, lookup -> {
            // error handling
            if (lookup.failed()) {
              return;
            }
            
            context.assertNull(lookup.result());
            context.async().complete();
            });
	}
	
	
	@Test
	public void getZeroBlogRecords(TestContext context) {
		
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
		
		
		Async async = context.async();
		HttpClientOptions options = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(port);

		HttpClient client = vertx.createHttpClient(options);
		try {
		HttpClientRequest req = client.get(port, "localhost", "/Services/rest/blogs");
		  req.exceptionHandler(err -> context.fail(err.getMessage()));
		  req.handler(resp -> {
			  resp.bodyHandler( ctx -> 
			{ 
			   context.assertEquals(404, resp.statusCode());
				async.complete(); 
				
			}
			  );
		    
		  });
		  
		 req.putHeader("Authorization", base64Util.encode(userName, password)).end();
				
		} catch(Exception e) {
			System.out.println(e.getMessage());
			fail();
		}
		
	} 
	
	@Test
	public void insertSingleBlogRecord(TestContext context) {
		
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
		
		
		Async async = context.async();
		HttpClientOptions options = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(port);
		
		final String json = Json.encodePrettily(new JsonObject().put("userFirst", "Pavan").put("userLast", "Sudheendra").put("userId", "572857ef72861272f8998fd8")
						  .put("date","2016-06-18T06:47:14.139Z").put("content", "blog content").put("title", "good blog title").put("tags", "goodtag").put("comments", new JsonArray()));
		final String length = Integer.toString(json.length());
		HttpClient client = vertx.createHttpClient(options);
		try {
		HttpClientRequest req = client.post(8085, "localhost", "/Services/rest/blogs");
		req.putHeader("content-type", "application/json").putHeader("content-length", length)
		.putHeader("Authorization", base64Util.encode(userName, password))
		.handler(response -> {
			context.assertEquals(response.statusCode(), 200);
			context.assertTrue(response.headers().get("content-type").contains("application/json"));
			response.bodyHandler( body -> {
				context.assertNotNull(body.toJsonObject().getString("_id"));
				context.assertNotEquals(body.toJsonObject().getString("content"), null);
				async.complete();
			});
		}).write(json).end();
		    				
		} catch(Exception e) {
			System.out.println(e.getMessage());
			fail();
		}
		
	}
	
	@Test
	public void searchWithTag(TestContext context) {
		
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
		
		Async async = context.async();
		HttpClientOptions options = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(port);
		
		final String tag = "goodtag";
		HttpClient client = vertx.createHttpClient(options);
		try {
		
			HttpClientRequest req = client.get(port, "localhost", "/Services/rest/blogs/" + tag);
			  req.exceptionHandler(err -> context.fail(err.getMessage()));
			  req.handler(resp -> {
				  resp.bodyHandler( ctx -> 
				{ 
				   context.assertEquals(404, resp.statusCode());
					async.complete(); 
					
				}
				  );
			    
			  });
			  
			 req.putHeader("Authorization", base64Util.encode(userName, password)).end();
		    				
		} catch(Exception e) {
			System.out.println(e.getMessage());
			fail();
		}
		
	}

}
