package com.cisco.cmad.blogapp.vertx_blog_service;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;

public class HTTPCorsHandler {
	
	
	private Handler<RoutingContext> handler = null;
	
	public Handler<RoutingContext> getAllowedMethodsAndHeaders() {
		
		handler = CorsHandler.create("*")
  		      .allowedMethod(HttpMethod.GET)
  		      .allowedMethod(HttpMethod.POST)
  		      .allowedMethod(HttpMethod.PUT)
  		      .allowedMethod(HttpMethod.DELETE)
  		      .allowedMethod(HttpMethod.OPTIONS)
  		      .allowedHeader("X-PINGARUNER")
  		      .allowedHeader("*")
  		      .allowedHeader("Content-Type")
  		      .allowedHeader("Access-Control-Allow-Headers: Origin, X-Requested-With, Content-Type, Accept, Authorization");
		
		return handler;
	}

}
