package com.cisco.cmad.blogapp.vertx_blog_service;

import java.util.List;
import java.util.Properties;

import com.cisco.cmad.blogapp.config.AppConfig;
import com.cisco.cmad.blogapp.utils.Base64Util;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;


public class BlogServiceApp extends AbstractVerticle
{
	private MongoClient client;

    public EventBus eb = null;
	
    JsonObject userObj = null;
    Properties prop = null;
    
    AppConfig appConfig = new AppConfig();
    Base64Util base64Util = new Base64Util();
    
    
	public void start(Future<Void> startFuture) {
		
    	System.out.println("vertx-blog-service verticle started!");
    	
    	// read from config.properties
    	prop = new Properties();
    	
    	String mongo_host = appConfig.getMongoHostConfig(prop);
    	String mongo_port = appConfig.getMongoPortConfig(prop);
    	
    	// construct the conf which will be used to initialize the mongoClient object
    	JsonObject conf = new JsonObject()
        .put("connection_string", "mongodb://" + mongo_host + ":" + mongo_port)
        .put("db_name", appConfig.getMongoDbConfig(prop));
    	client = MongoClient.createShared(vertx, conf);
    	
    	// initialize the eventBus
    	eb = vertx.eventBus();
    	// start the HTTP server
    	HttpServer(Integer.parseInt(appConfig.getAppPortConfig(prop)));
    	startFuture.complete();
    }
    
    public void stop(Future<Void> stopFuture) throws Exception{
    	System.out.println("vertx-blog-service verticle stopped!");
    	stopFuture.complete();
    }
    
    private void HttpServer(int port) {
    	
    	HttpServer server = vertx.createHttpServer();
    	Router router = Router.router(vertx);
    	router.route().handler(BodyHandler.create());
    	    	    	
    	// handle cors issue
    	router.route().handler(CorsHandler.create("*")
    		      .allowedMethod(HttpMethod.GET)
    		      .allowedMethod(HttpMethod.POST)
    		      .allowedMethod(HttpMethod.PUT)
    		      .allowedMethod(HttpMethod.DELETE)
    		      .allowedMethod(HttpMethod.OPTIONS)
    		      .allowedHeader("X-PINGARUNER")
    		      .allowedHeader("*")
    		      .allowedHeader("Content-Type")
    		      .allowedHeader("Access-Control-Allow-Headers: Origin, X-Requested-With, Content-Type, Accept, Authorization"));
    	
    	// submit blog
    	submitBlog(router);
    	
    	getBlogs(router);
    	
    	searchBlogsWithTags(router);
    	
    	submitComments(router);
    	
    	eventBusConsumer();
    	
    	server.requestHandler(router::accept).listen(port);

    }
    
    public void eventBusConsumer() {
    	eb.consumer("user.creation", message -> {
    		
  		  Object obj = message.body();
  		  
  		  JsonObject obj1 = (JsonObject) obj;
  		  
  		  String username = obj1.getString("userName");
  		  String password = obj1.getString("password");
  		  
  		  JsonObject blogUsers = new JsonObject().put("userName", username).put("password", password);
  		  
  		client.findOne("blog_users", blogUsers, null, lookup -> {
            // error handling
            if (lookup.failed()) {
              return;
            }
            if(lookup.result() != null) {
                // already exists
                // do nothing
            } else {
  		  
	  		  client.save("blog_users", blogUsers, insert -> {
		          // error handling
	              if (insert.failed()) {
	                return;
	              }
	             
	              System.out.println("User object inserted to blog_users table in blog service app");
	              blogUsers.put("_id", insert.result());
	              
	            });
	  		  
            }
            });
    	});
    	return;
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
		    // failed!
		  }
		});
    	
    }
}
