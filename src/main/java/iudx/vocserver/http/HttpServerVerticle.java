/**
* <h1>HttpServerVerticle.java</h1>
* HTTP Server Verticle
*/

package iudx.vocserver.http;

import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


import iudx.vocserver.database.DBService;
import iudx.vocserver.utils.Validator;

public class HttpServerVerticle extends AbstractVerticle {
    /**
     * HttpServerVerticle Class
     * @param dbClient MongoDB Client
     * @param readyHandler Async query result handler. Returns query results as JSONArray
     */

    // Config variables
    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String CONFIG_DB_QUEUE = "vocserver.queue";

    // Default logger
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    // iudx-voc-server DBService
    private DBService dbService;

    // Validator objects
    private Validator classValidator;
    private Validator propertyValidator;

    /**
     * AbstractVerticle start
     * */
    @Override
    public void start(Promise<Void> promise) throws Exception {

        String dbQueue = config().getString(CONFIG_DB_QUEUE, "vocserver.queue");
        dbService = DBService.createProxy(vertx, dbQueue);
        propertyValidator = new Validator("/propertySchema.json");
        classValidator = new Validator("/classSchema.json");

        HttpServer server = vertx.createHttpServer();

        /** ROUTES */
        Router router = Router.router(vertx);
        /** Get classes or properties by name */
        router.get("/:name").handler(this::getSchemaHandler);
        router.route("/:name").handler(BodyHandler.create());
        router.post("/:name").handler(this::insertSchemaHandler);

        /* Defaults to 8080 */
        /** @TODO: Make port configureable */
        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
        server
            .requestHandler(router)
            .listen(portNumber, ar -> {
                if (ar.succeeded()) {
                    LOGGER.info("HTTP server running on port " + portNumber);
                    promise.complete();
                } else {
                    LOGGER.error("Could not start a HTTP server", ar.cause());
                    promise.fail(ar.cause());
                }
            });
    }


    /**
     * getSchemaHandler - handler to get classes or properties by name
     */
    // tag::db-service-calls[]
    private void getSchemaHandler(RoutingContext context) {
        String name = context.request().getParam("name");
        /** Check if provided param is class or property */
        boolean isClass = Character.isUpperCase(name.charAt(0));
        /** This can be simplified by setting a flag, leaving it expanded for future use. */
        if (isClass == true) {
            dbService.getClass(name, reply -> {
                if (reply.succeeded()) {
                    context.response().putHeader("Content-Type", "application/json");
                    context.response().setStatusCode(200)
                                        .end(reply.result().encode());
                }
                else {
                    context.response().putHeader("Content-Type", "application/json");
                    context.response().setStatusCode(404).end();
                }
            });
        }
        else if (isClass == false) {
            dbService.getProperty(name, reply -> {
                if (reply.succeeded()) {
                    context.response().putHeader("Content-Type", "application/json");
                    context.response().setStatusCode(200)
                                        .end(reply.result().encode());
                }
                else {
                    context.response().putHeader("Content-Type", "application/json");
                    context.response().setStatusCode(404).end();
                }
            });
        }
    }


    /**
     * insertSchemaHandler - handler to insert a class or property
     * @TODO: Check duplicates
     */
    // tag::db-service-calls[]
    private void insertSchemaHandler(RoutingContext context) {
        String name = context.request().getParam("name");
        /** Check if provided param is class or property */
        boolean isClass = Character.isUpperCase(name.charAt(0));
        /** This can be simplified by setting a flag, leaving it expanded for future use. */
        context.response().putHeader("Content-Type", "application/json");
        try {
            String body = context.getBodyAsString();

            if (isClass == true) {
                boolean isValid = classValidator.validate(body);
                LOGGER.info("isValid " + isValid);
                if (isValid == false) {
                    context.response().setStatusCode(404).end();
                }
                else {
                    dbService.insertClass(name, context.getBodyAsJson(), reply -> {
                        if (reply.succeeded()) {
                            LOGGER.info("Insertion success");
                            context.response().setStatusCode(201).end();
                        }
                        else {
                            context.response().setStatusCode(404).end();
                        }
                    });
                }
            }
            else if (isClass == false) {
                boolean isValid = propertyValidator.validate(body);
                if (isValid == false) {
                    context.response().setStatusCode(404).end();
                }
                else {
                    dbService.insertProperty(name, context.getBodyAsJson(), reply -> {
                        if (reply.succeeded()) {
                            LOGGER.info("Insertion success");
                            context.response().setStatusCode(201).end();
                        }
                        else {
                            context.response().setStatusCode(404).end();
                        }
                    });
                }
            }
        }
        catch (NullPointerException e) {
            context.response().setStatusCode(404);
        }
    }
}
