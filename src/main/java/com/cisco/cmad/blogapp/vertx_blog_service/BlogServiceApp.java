package com.cisco.cmad.blogapp.vertx_blog_service;

import java.util.List;
import com.cisco.cmad.blogapp.config.ZooConfig;
import com.cisco.cmad.blogapp.utils.Base64Util;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;


public class BlogServiceApp extends AbstractVerticle
{
	private MongoClient client;

    public EventBus eb = null;
	    
    ZooConfig zooConfig = new ZooConfig("localhost");
    Base64Util base64Util = new Base64Util();
    HTTPCorsHandler corsHandlerUtil = new HTTPCorsHandler();
    EventBusConsumer ebConsumer = new EventBusConsumer();
    
    public MongoClient getClient(JsonObject conf) {
    	return MongoClient.createShared(vertx, conf);
    }
    
	public void start(Future<Void> startFuture) {
		
    	System.out.println("vertx-blog-service verticle started!");
    	
    	// construct the conf which will be used to initialize the mongoClient object
    	JsonObject conf = new JsonObject()
        .put("connection_string", "mongodb://" + zooConfig.readDbHostConfig() + ":" + zooConfig.readDbPortConfig())
        .put("db_name", zooConfig.readDbNameConfig());
    	
    	// initialize the mongo client
    	client = getClient(conf);
    	
    	// initialize the eventBus
    	eb = vertx.eventBus();
    	
    	// start the HTTP server
    	HttpServer(Integer.parseInt(zooConfig.readAppPortConfig()));
    	
    	startFuture.complete();
    }
    
    public void stop(Future<Void> stopFuture) throws Exception{
    	System.out.println("vertx-blog-service verticle stopped!");
    	zooConfig.close();
    	stopFuture.complete();
    }
    
    private void HttpServer(int port) {
    	
    	HttpServer server = vertx.createHttpServer();
    	Router router = Router.router(vertx);
    	router.route().handler(BodyHandler.create());
    	    	    	
    	// handle cors issue and allow methods and any headers
    	router.route().handler(corsHandlerUtil.getAllowedMethodsAndHeaders());
    	
    	// submit blog
    	submitBlog(router);
    	
    	// get all blogs
    	getBlogs(router);
    	
    	// search blogs with a tag
    	searchBlogsWithTags(router);
    	
    	// submit comments
    	submitComments(router);
    	
    	// event bus for broadcast communication with other verticles
    	ebConsumer.consumer(eb, client);
    	
    	server.requestHandler(router::accept).listen(port);

    }

    
    public void submitBlog(Router router) {
    	
    	router.post("/Services/rest/blogs").handler(ctx -> {
    		
    		String authHeader = ctx.request().headers().get("Authorization");
    		
    		String decodedString = base64Util.decode(authHeader);
    		String userName = base64Util.getUserName(decodedString);
    		String password = base64Util.getPassword(decodedString);
    		
    	    client.findOne("blog_users", new JsonObject().put("userName", userName).put("password", password), null, lookupUser -> {
    	            
    	        if(lookupUser.result() != null) {
    	        	
    	          JsonObject blogDetails = ctx.getBodyAsJson();
    	          client.findOne("blogs", new JsonObject().put("title", blogDetails.getString("title"))
    	          , null, lookup -> {
    	                         
    	          // error handling
    	          if (lookup.failed()) {
    	            ctx.fail(500);
    	            return;
    	          }
    	    
    	          JsonObject blog = lookup.result();
    	    
    	          if (blog != null) {
    	            // already exists
    	              ctx.fail(500);
    	          } else {
    	              
    	              JsonArray comments = new JsonArray();
    	              blogDetails.put("comments", comments);
    	              
    	              client.insert("blogs", blogDetails, insert -> {
    	                  // error handling
    	                  if (insert.failed()) {
    	                    ctx.fail(500);
    	                    return;
    	                  }
    	                  
    	                  // add the generated id to the user object
    	                  blogDetails.put("_id", insert.result());
    	                  
    	                  ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    	                  System.out.println(blogDetails.encode());
    	                  ctx.response().end(blogDetails.encode());
    	                });

    	          	}
    	          
    	        });
    	    } 
    	    else {
    	        ctx.fail(401);
    	    }
    	    });
    	});
    }
    
    
    public void submitComments(Router router) {
    	router.post("/Services/rest/blogs/:id/comments").handler(ctx -> {
    		
    		String authHeader = ctx.request().headers().get("Authorization");
    		
    		String decodedString = base64Util.decode(authHeader);
    		String userName = base64Util.getUserName(decodedString);
    		String password = base64Util.getPassword(decodedString);
    		  
    		client.findOne("blog_users", new JsonObject().put("userName", userName).put("password", password), null, lookupUser -> {
	            
    	        if(lookupUser.result() != null) {
    	        	
					  String blogId = ctx.request().getParam("id");
			
				      JsonObject blogComments = ctx.getBodyAsJson();
				      
				      System.out.println(blogComments);
				      
				      client.findOne("blogs", new JsonObject().put("_id", blogId)
				      , null, lookup -> {
				    		    	 
				      // error handling
				      if (lookup.failed()) {
				        ctx.fail(500);
				        return;
				      }
				
				      JsonObject blog = lookup.result();
				      		
				      if (blog == null || blog.size() == 0) {
					    // does not exists
				    	  ctx.fail(500);
			            
				      } else {
				    	  
			              JsonArray comments = blog.getJsonArray("comments");
			
				    	  
			              comments.add(blogComments);
			              
			              blog.put("comments", comments);
			              
				    	  client.save("blogs", blog, insert -> {
				              // error handling
				              if (insert.failed()) {
				                ctx.fail(500);
				                return;
				              }
			
				              // add the generated id to the user object
			
				              ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				              System.out.println(blog.encode());
				              ctx.response().end(blogComments.encode());
				            });
				      }
		    	      
		    	    });
	    	     }
		      else {
	    	        ctx.fail(401);
	    	 }
	    });
    	});
    }
    
    public void getBlogs(Router router) {
    	
    	router.get("/Services/rest/blogs").handler(ctx -> {
    		String authHeader = ctx.request().headers().get("Authorization");
    		
    		String decodedString = base64Util.decode(authHeader);
    		String userName = base64Util.getUserName(decodedString);
    		String password = base64Util.getPassword(decodedString);
    		    		
    		client.findOne("blog_users", new JsonObject().put("userName", userName).put("password", password), null, lookupUser -> {
	            
    			
    	        if(lookupUser.result() != null) {
    	        
    	        	
		    		client.find("blogs", new JsonObject(), lookup -> {
		    	        // error handling
		    	        if (lookup.failed()) {
		    	          ctx.fail(500);
		    	          return;
		    	        }
		
		    	        List<JsonObject> blogs = lookup.result();
		    	        System.out.println(blogs);
		
		    	        if (blogs == null || blogs.size() == 0) {
		    	           // ctx.fail(404);
		    	           ctx.response().setStatusCode(404).end();
		    	          
		    	        } else {
		    	        	
		    	        	ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				            ctx.response().end(blogs.toString());
		    	        }
		    	      });
    	        } else {
    	        	ctx.fail(401);
    	        }
    		});
    	});
    }
    
    
    public void searchBlogsWithTags(Router router) {
    	router.get("/Services/rest/blogs/:tags").handler(ctx -> {
    		
    		String authHeader = ctx.request().headers().get("Authorization");
    		
    		String decodedString = base64Util.decode(authHeader);
    		String userName = base64Util.getUserName(decodedString);
    		String password = base64Util.getPassword(decodedString);
    		  
    		client.findOne("blog_users", new JsonObject().put("userName", userName).put("password", password), null, lookupUser -> {
	            
    	        if(lookupUser.result() != null) {
    	        	
		    		client.find("blogs", new JsonObject().put("tags", ctx.request().getParam("tags")), lookup -> {
		    	        // error handling
		    	        if (lookup.failed()) {
		    	          ctx.fail(500);
		    	          return;
		    	        }
		
		    	        List<JsonObject> blogs = lookup.result();
		    	        System.out.println(blogs.toString());
		
		    	        if (blogs == null || blogs.size() == 0) {
		    	          ctx.fail(404);
		    	        } else {
		    	        	
		    	        	ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				            ctx.response().end(blogs.toString());
		    	        }
		    	      });
    	        } else {
    	        	ctx.fail(401);
    	        }
    		});
    	});
    }
    
    public static void main( String[] args )
    {	
    	ClusterManager mgr = new HazelcastClusterManager();
		VertxOptions options = new VertxOptions().setWorkerPoolSize(10).setClusterManager(mgr);
		//Vertx vertx = Vertx.factory.vertx(options);
		
		Vertx.clusteredVertx(options, res -> {
		  if (res.succeeded()) {
		    Vertx vertx = res.result();
		    vertx.deployVerticle("com.cisco.cmad.blogapp.vertx_blog_service.BlogServiceApp");
		  } else {
		    System.out.println("Failed to deploy BlogServiceApp verticle!");
		  }
		});
    	
    }
}
