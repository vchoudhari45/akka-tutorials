package com.vc.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.time.Duration;

class Hal extends AbstractBehavior<Hal.Command> {
  protected interface Command {}

  public static final class OpenThePodBayDoorsPlease implements Hal.Command {
    public final ActorRef<HalResponse> respondTo;

    public OpenThePodBayDoorsPlease(ActorRef<HalResponse> respondTo) {
      this.respondTo = respondTo;
    }
  }

  public static final class HalResponse {
    public final String message;

    public HalResponse(String message) {
      this.message = message;
    }
  }

  public static Behavior<Hal.Command> create() {
    return Behaviors.setup(context -> new Hal(context));
  }

  private Hal(ActorContext<Hal.Command> context) {
    super(context);
  }

  @Override
  public Receive<Hal.Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(OpenThePodBayDoorsPlease.class,
        openThePodBayDoorsPlease -> onOpenThePodBayDoorsPlease(openThePodBayDoorsPlease))
      .build();
  }

  private Behavior<Hal.Command>
  onOpenThePodBayDoorsPlease(OpenThePodBayDoorsPlease openThePodBayDoorsPlease) throws InterruptedException {
    /**
     *  Uncomment below to make ask from Dave actor fail
     *  Thread.sleep(2000);
     **/
    openThePodBayDoorsPlease.respondTo.tell(new HalResponse("I'm sorry, Dave. I'm afraid I can't do that."));
    return this;
  }
}

class Dave extends AbstractBehavior<Dave.Command> {

  public interface Command {}

  private static final class AdaptedResponse implements Command {
    public final String message;
    public AdaptedResponse(String message) {
      this.message = message;
    }
  }

  public static Behavior<Command> create(ActorRef<Hal.Command> hal) {
    return Behaviors.setup(context -> new Dave(context, hal));
  }

  private Dave(ActorContext<Command> context, ActorRef<Hal.Command> hal) {
    super(context);

    /**
     *  RequestResponse with Ask
     **/
    final Duration timeout = Duration.ofSeconds(1);
    final int requestId = 1;
    context.ask(
      Hal.HalResponse.class,
      hal,
      timeout,
      (ActorRef<Hal.HalResponse> ref) -> new Hal.OpenThePodBayDoorsPlease(ref),
      (response, throwable) -> {
        if (response != null)
          return new AdaptedResponse(requestId + ": " + response.message);
        else
          return new AdaptedResponse(requestId + ": Request failed");
      });
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(AdaptedResponse.class, response -> onAdaptedResponse(response))
      .build();
  }

  private Behavior<Command> onAdaptedResponse(AdaptedResponse response) {
    getContext().getLog().info("Got response from HAL: {}", response.message);
    return this;
  }
}

class RequestAndResponseAsk {
  public static void main(String[] args) {
    ActorRef<Hal.Command> halActorRef = ActorSystem.create(Hal.create(), "halActorRef");

    ActorSystem<Dave.Command> actorSystem
      = ActorSystem.create(Dave.create(halActorRef), "actorSystem");
  }
}
