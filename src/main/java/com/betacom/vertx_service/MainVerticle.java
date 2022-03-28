package com.betacom.vertx_service;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;


public class MainVerticle extends AbstractVerticle {

  private MongoClient mongoClient;

  private JWTAuth provider;

  @Override
  public void start(Promise<Void> startPromise) {
    mongoClient = MongoClient.createShared(vertx, new JsonObject()
      .put("db_name", config().getString("db_name", "vertx"))
      .put("connection_string", config().getString("connection_string", "mongodb://localhost:27017"))
    );

    provider = JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer("keyboard cat")));

    Router apiRouter = Router.router(vertx);
    apiRouter.route().handler(BodyHandler.create());
    apiRouter.post("/register").handler(this::registerUser);
    apiRouter.post("/login").handler(this::login);
    apiRouter.post("/items").handler(JWTAuthHandler.create(provider)).handler(this::addItem);
    apiRouter.get("/items").handler(JWTAuthHandler.create(provider)).handler(this::items);

    vertx.createHttpServer()
      .requestHandler(apiRouter)
      .listen(8080, http -> {
        if (http.succeeded()) {
          startPromise.complete();
          System.out.println("HTTP server started on port 8080");
        } else {
          startPromise.fail(http.cause());
        }
      });
  }

  private void items(RoutingContext routingContext) {
    JsonObject item = new JsonObject();
    item.put("owner", routingContext.user().get("_id"));
    mongoClient.find("items", item, ar -> {
      if (ar.succeeded()) {
        JsonObject items = new JsonObject();
        items.put("items", ar.result());
        routingContext.response()
          .setStatusCode(201)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end(Json.encodePrettily(items));
      } else {
        routingContext.response()
          .setStatusCode(500)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end(Json.encodePrettily(ar.cause().getMessage()));
      }});
  }

  private void login(RoutingContext routingContext) {
    if(validateUser(routingContext)) {
      String login = routingContext.getBodyAsJson().getString("login");
      String password = routingContext.getBodyAsJson().getString("password");
      JsonObject searchQuery = new JsonObject();
      searchQuery.put("login", login);
      mongoClient.find("users", searchQuery, ar -> {
        if (ar.succeeded()) {
          List<JsonObject> playerInDatabase = ar.result();
          if (!playerInDatabase.isEmpty()) {
            String passwordInDatabase = playerInDatabase.get(0).getString("password");
            if (passwordInDatabase.equals(md5(password))) {
              String token = provider.generateToken(new JsonObject()
                .put("login", login)
                .put("password", password)
                .put("_id", playerInDatabase.get(0).getString("_id")));
              routingContext.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(new JsonObject().put("token", token)));
            } else {
              routingContext.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json; charset=utf-8")
                .end(Json.encodePrettily("Wrong password"));
            }
          } else {
            routingContext.response()
              .setStatusCode(500)
              .putHeader("Content-Type", "application/json; charset=utf-8")
              .end(Json.encodePrettily("No user with this login"));
          }
        } else {
          routingContext.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(ar.cause().getMessage()));
        }
      });
    }
  }

  private void addItem(RoutingContext routingContext) {
    if(validateItem(routingContext)) {
      JsonObject item = new JsonObject();
      item.put("name", routingContext.getBodyAsJson().getString("name"));
      item.put("owner", routingContext.user().get("_id"));
      item.put("_id", UUID.randomUUID().toString());
      mongoClient.insert("items", item, ar -> {
        if (ar.succeeded()) {
          routingContext.response()
            .setStatusCode(201)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .end(Json.encodePrettily("Item added"));
        } else {
          routingContext.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(ar.cause().getMessage()));
        }
      });
    }
  }

  private void registerUser(RoutingContext routingContext) {
    if(validateUser(routingContext)) {
      String login = routingContext.getBodyAsJson().getString("login");
      JsonObject searchQuery = new JsonObject();
      searchQuery.put("login", login);
      mongoClient.find("users", searchQuery, ar -> {
        if (ar.succeeded()) {
          List<JsonObject> playerInDatabase = ar.result();
          if (playerInDatabase == null || playerInDatabase.isEmpty()) {
            JsonObject userToRegister = new JsonObject();
            userToRegister.put("login", routingContext.getBodyAsJson().getString("login"));
            userToRegister.put("password", md5(routingContext.getBodyAsJson().getString("password")));
            userToRegister.put("_id", UUID.randomUUID().toString());
            mongoClient.insert("users", userToRegister, arIns -> {
              if (arIns.succeeded()) {
                String token = provider.generateToken(new JsonObject()
                  .put("login", userToRegister.getString("login"))
                  .put("_id", userToRegister.getString("_id"))
                  .put("password", userToRegister.getString("password")));
                routingContext.response()
                  .setStatusCode(201)
                  .putHeader("Content-Type", "application/json; charset=utf-8")
                  .end(Json.encodePrettily(new JsonObject().put("token", token)));
              } else {
                routingContext.response()
                  .setStatusCode(500)
                  .putHeader("Content-Type", "application/json; charset=utf-8")
                  .end(Json.encodePrettily(arIns.cause().getMessage()));
              }
            });
          } else {
            routingContext.response()
              .setStatusCode(500)
              .putHeader("Content-Type", "application/json; charset=utf-8")
              .end(Json.encodePrettily("User with this login already exists"));
          }
        } else {
        routingContext.response()
          .setStatusCode(500)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end(Json.encodePrettily(ar.cause().getMessage()));
      }
      });
    }
  }

  private static boolean validateUser(RoutingContext routingContext){
    String password = routingContext.getBodyAsJson().getString("password");
    String login = routingContext.getBodyAsJson().getString("login");
    if(password==null||password.isEmpty()){
      routingContext.response()
        .setStatusCode(500)
        .putHeader("Content-Type", "application/json; charset=utf-8")
        .end(Json.encodePrettily("Password required"));
      return false;
    } else if (login==null||login.isEmpty()){
      routingContext.response()
        .setStatusCode(500)
        .putHeader("Content-Type", "application/json; charset=utf-8")
        .end(Json.encodePrettily("Password required"));
      return false;
    }
    return true;
  }

  private static boolean validateItem(RoutingContext routingContext){
    String name = routingContext.getBodyAsJson().getString("name");
    if(name==null||name.isEmpty()){
      routingContext.response()
        .setStatusCode(500)
        .putHeader("Content-Type", "application/json; charset=utf-8")
        .end(Json.encodePrettily("Name required"));
      return false;
    }
    return true;
  }

  public static String md5(String input) {
    if (null == input) {
      return null;
    }
    String md5 = null;
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      digest.update(input.getBytes(), 0, input.length());
      md5 = new BigInteger(1, digest.digest()).toString(16);
    } catch (NoSuchAlgorithmException e) {
      //Do nothing
    }
    return md5;
  }
}
