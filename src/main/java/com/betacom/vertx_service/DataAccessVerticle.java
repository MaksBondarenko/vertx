package com.betacom.vertx_service;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

public class DataAccessVerticle extends AbstractVerticle {

  private MongoClient mongoClient;

  @Override
  public void start(){
    mongoClient = MongoClient.createShared(vertx, new JsonObject()
      .put("db_name", config().getString("db_name", "vertx"))
      .put("connection_string", config().getString("connection_string", "mongodb://localhost:27017"))
    );

//    vertx.eventBus().consumer("login", msg ->{
//      msg.reply("");
//    });
//
//    vertx.eventBus().consumer("login", this::login);
//
//    vertx.eventBus().consumer("register", msg ->{
//      Object smth = msg.body();
//      msg.reply("");
//    });

    vertx.eventBus().consumer("register", this::registerUser);
    vertx.eventBus().consumer("login", this::login);
    vertx.eventBus().consumer("get-items", this::getItems);
    vertx.eventBus().consumer("add-item", this::addItem);
  }

  private void getItems(Message<JsonObject> searchQuery) {
    mongoClient.find("items", searchQuery.body(), ar -> {
      JsonObject result = new JsonObject();
      if (ar.succeeded()) {
        result.put("message", ar.result());
      } else {
        result.put("message", ar.cause().getMessage());
      }
      searchQuery.reply(result);
    });
  }

  private void login(Message<JsonObject> message) {
    JsonObject searchQuery = new JsonObject();
    searchQuery.put("login", message.body().getString("login"));
    mongoClient.find("users", searchQuery, ar -> {
      JsonObject result = new JsonObject();
      if (ar.succeeded()) {
        List<JsonObject> playerInDatabase = ar.result();
        if (!playerInDatabase.isEmpty()) {
          String passwordInDatabase = playerInDatabase.get(0).getString("password");
          if(passwordInDatabase.equals(md5(message.body().getString("password")))) {
            result.put("id", playerInDatabase.get(0).getString("_id"));
          } else {
            result.put("error", "Wrong password");
          }
        } else {
          result.put("error", "No user with this login");
        }
      } else {
        result.put("error", ar.cause().getMessage());
      }
      message.reply(result);
    });
  }

  private void addItem(Message<JsonObject> itemMessage) {
    JsonObject item = itemMessage.body();
    item.put("_id", UUID.randomUUID().toString());
    mongoClient.insert("items", item, ar -> {
      JsonObject result = new JsonObject();
      if (ar.succeeded()) {
        result.put("message", "Item added");
      } else {
        result.put("message", ar.cause().getMessage());
      }
      itemMessage.reply(result);
    });
  }

  private void registerUser(Message<JsonObject> message) {
    JsonObject searchQuery = new JsonObject();
    searchQuery.put("login", message.body().getString("login"));
    mongoClient.find("users", searchQuery, ar -> {
      JsonObject result = new JsonObject();
      if (ar.succeeded()) {
        List<JsonObject> playerInDatabase = ar.result();
        if (playerInDatabase == null || playerInDatabase.isEmpty()) {
          JsonObject userToRegister = new JsonObject();
          userToRegister.put("login", message.body().getString("login"));
          userToRegister.put("password", md5(message.body().getString("password")));
          userToRegister.put("_id", UUID.randomUUID().toString());
          mongoClient.insert("users", userToRegister, arIns -> {
            if (arIns.succeeded()) {
              message.reply(userToRegister);
            } else {
              result.put("error", arIns.cause().getMessage());
              message.reply(result);
            }
          });
        } else {
          result.put("error", "User with this login already exists");
          message.reply(result);
        }
      } else {
        result.put("error", ar.cause().getMessage());
        message.reply(result);
      }
    });
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
