package com.betacom.vertx_service;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class HttpVerticle extends AbstractVerticle {

  private JWTAuth provider;

  @Override
  public void start(Promise<Void> startPromise){

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

//  private void przekazanie(RoutingContext routingContext) {
//    vertx.eventBus().request("log", routingContext.getBodyAsJson(), reply  -> {
//      routingContext.request().response().end((String) reply.result().body());
//    });
//  }

  private void items(RoutingContext routingContext) {
    JsonObject item = new JsonObject();
    item.put("owner", routingContext.user().get("_id"));
    vertx.eventBus().request("get-items", item, reply  -> {
      if(reply.succeeded()){
        JsonObject result = (JsonObject) reply.result().body();
        routingContext.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end(Json.encodePrettily(result.getString("message")));
      } else {
        routingContext.response()
          .setStatusCode(500)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end(Json.encodePrettily(reply.cause().getMessage()));
      }
    });
  }

  private void login(RoutingContext routingContext) {
    if(validateUser(routingContext)) {
      String login = routingContext.getBodyAsJson().getString("login");
      String password = routingContext.getBodyAsJson().getString("password");
      JsonObject searchQuery = new JsonObject();
      searchQuery.put("login", login);
      searchQuery.put("password", password);
      vertx.eventBus().request("login", searchQuery, reply  -> {
        if(reply.succeeded()){
          JsonObject result = (JsonObject) reply.result().body();
          if(result.containsKey("id")){
            String token = provider.generateToken(new JsonObject()
              .put("login", login)
              .put("password", password)
              .put("_id", result.getString("id")));
            routingContext.response()
              .setStatusCode(200)
              .putHeader("Content-Type", "application/json; charset=utf-8")
              .end(Json.encodePrettily(new JsonObject().put("token", token)));
          } else {
            routingContext.response()
              .setStatusCode(500)
              .putHeader("Content-Type", "application/json; charset=utf-8")
              .end(Json.encodePrettily(result.getString("error")));
          }
        } else {
          routingContext.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(reply.cause().getMessage()));
        }
//      routingContext.request().response().end((String) reply.result().body()); todo sprawdzic czym sie rozni response()/.request().response()
      });
    }
  }

  private void addItem(RoutingContext routingContext) {
    if(validateItem(routingContext)) {
      JsonObject item = new JsonObject();
      item.put("name", routingContext.getBodyAsJson().getString("name"));
      item.put("owner", routingContext.user().get("_id"));
      vertx.eventBus().request("add-item", item, reply  -> {
        if(reply.succeeded()){
        JsonObject result = (JsonObject) reply.result().body();
          routingContext.response()
            .setStatusCode(201)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(result.getString("message")));
        } else {
          routingContext.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(reply.cause().getMessage()));
        }
      });
    }
  }

  private void registerUser(RoutingContext routingContext) {
    if(validateUser(routingContext)) {
      String login = routingContext.getBodyAsJson().getString("login");
      JsonObject searchQuery = new JsonObject();
      searchQuery.put("login", login);
      searchQuery.put("password", routingContext.getBodyAsJson().getString("password"));
      vertx.eventBus().request("register", searchQuery, reply  -> {
        if(reply.succeeded()){
          JsonObject result = (JsonObject) reply.result().body();
          if(result.containsKey("_id")){
            String token = provider.generateToken(new JsonObject()
              .put("login", login)
              .put("password", result.getString("password"))//todo
              .put("_id", result.getString("id")));
            routingContext.response()
              .setStatusCode(201)
              .putHeader("Content-Type", "application/json; charset=utf-8")
              .end(Json.encodePrettily(new JsonObject().put("token", token)));
          } else {
            routingContext.response()
              .setStatusCode(500)
              .putHeader("Content-Type", "application/json; charset=utf-8")
              .end(Json.encodePrettily(result.getString("error")));
          }
        } else {
          routingContext.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(reply.cause().getMessage()));
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
        .end(Json.encodePrettily("Login required"));
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
}
