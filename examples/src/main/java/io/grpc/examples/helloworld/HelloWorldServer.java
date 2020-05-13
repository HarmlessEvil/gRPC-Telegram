/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.examples.helloworld;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class HelloWorldServer {
  private static final Logger logger = Logger.getLogger(HelloWorldServer.class.getName());

  private Server server;

  private void start() throws IOException {
    /* The port on which the server should run */
    int port = 50051;
    server = ServerBuilder.forPort(port)
        .addService(new GreeterImpl(name))
        .build()
        .start();
    logger.info("Server started, listening on " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        try {
          HelloWorldServer.this.stop();
        } catch (InterruptedException e) {
          e.printStackTrace(System.err);
        }
        System.err.println("*** server shut down");
      }
    });
  }

  private void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  private final String name;

  public HelloWorldServer(String name) {
    this.name = name;
  }

  /**
   * Main launches the server from the command line.
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    String name = "admin";

    if (args.length > 0) {
      name = args[0];
    }

    final HelloWorldServer server = new HelloWorldServer(name);
    server.start();
    server.blockUntilShutdown();
  }

  static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
    private final String name;

    public GreeterImpl(String name) {
      this.name = name;
    }

    private String opponentName;

    @Override
    public void greet(HelloGreeting req, StreamObserver<HelloGreeting> responseObserver) {
      opponentName = req.getName();
      System.out.println(opponentName + " has joined the chat.");

      HelloGreeting greeting = HelloGreeting.newBuilder().setName(name).build();
      responseObserver.onNext(greeting);
      responseObserver.onCompleted();
    }

    @Override
    public void sendMessage(HelloMessage request, StreamObserver<Empty> responseObserver) {
      System.out.println(opponentName + "> " + request.getText());

      responseObserver.onNext(Empty.newBuilder().build());
      responseObserver.onCompleted();
    }

    private static final Scanner scanner = new Scanner(System.in);
    @Override
    public StreamObserver<HelloMessage> sendMessages(final StreamObserver<HelloMessage> responseObserver) {
      class ReadingThread implements Runnable {
        private boolean running = true;

        @Override
        public void run() {
          while (running) {
            HelloMessage message = HelloMessage
                    .newBuilder()
                    .setText(scanner.nextLine())
                    .setTimestamp((new Date()).getTime())
                    .build();
            responseObserver.onNext(message);
          }
        }

        public void stop() {
          running = false;
        }
      }

      ReadingThread reading = new ReadingThread();
      Thread thread = new Thread(reading);
      thread.start();

      return new StreamObserver<HelloMessage>() {
        @Override
        public void onNext(HelloMessage value) {
          System.out.println(
                  '(' + new Date(value.getTimestamp()).toString() + ") " +  opponentName + "> " + value.getText()
          );
        }

        @Override
        public void onError(Throwable t) {
          logger.log(Level.WARNING, "Some error occurred");
          reading.stop();
          try {
            thread.join();
          } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Some error occurred while reading input");
          }
        }

        @Override
        public void onCompleted() {
          responseObserver.onCompleted();
        }
      };
    }
  }
}
