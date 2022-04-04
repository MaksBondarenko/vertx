package com.betacom.vertx_service;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;


public class MainVerticle extends AbstractVerticle {

  @Override
  public void start() {
    DeploymentOptions options = new DeploymentOptions()
      .setWorker(true)
      .setInstances(config().getInteger("data_access_verticles_number", 1));
    vertx.deployVerticle("com.betacom.vertx_service.DataAccessVerticle", options);
    vertx.deployVerticle("com.betacom.vertx_service.HttpVerticle", options);
  }
}
