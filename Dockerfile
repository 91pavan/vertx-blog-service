# Extend vert.x image
FROM vertx/vertx3

#                                                       
ENV VERTICLE_NAME com.cisco.cmad.blogapp.vertx_blog_service.BlogServiceApp
ENV VERTICLE_FILE vertx-blog-service-0.0.1-SNAPSHOT-jar-with-dependencies.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8085

# Copy your verticle to the container                   
COPY $VERTICLE_FILE $VERTICLE_HOME/

# Copy the logging.properties file
COPY ../logging.properties $VERTICLE_HOME/
ENV VERTX_JUL_CONFIG $VERTICLE_HOME/logging.properties

# Copy the cluster.xml file 
COPY ../src/main/resources/cluster.xml $VERTICLE_HOME/
CMD [export CLASSPATH=`find $VERTICLE_HOME -printf '%p:' | sed 's/:$//'`; vertx run $VERTICLE_NAME"]

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["vertx run $VERTICLE_NAME -cp $VERTICLE_HOME/*"]