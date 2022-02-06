package com.vc.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.pattern.StatusReply;

import java.time.Duration;

class GenericResponseHal extends AbstractBehavior<GenericResponseHal.Command> {

  protected interface Command {}

  public static final class OpenThePodBayDoorsPlease
    implements GenericResponseHal.Command {
    public final ActorRef<StatusReply<String>> respondTo;

    public OpenThePodBayDoorsPlease(ActorRef<StatusReply<String>> respondTo) {
      this.respondTo = respondTo;
    }
  }

  public static Behavior<GenericResponseHal.Command> create() {
    return Behaviors.setup(context -> new GenericResponseHal(context));
  }

  private GenericResponseHal(ActorContext<GenericResponseHal.Command> context) {
    super(context);
  }

  @Override
  public Receive<GenericResponseHal.Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(GenericResponseHal.OpenThePodBayDoorsPlease.class,
        message -> onOpenThePodBayDoorsPlease(message))
      .build();
  }

  private Behavior<GenericResponseHal.Command> onOpenThePodBayDoorsPlease(
    GenericResponseHal.OpenThePodBayDoorsPlease message) {
    /**
     *  Wrapping success reply in Generic {@link akka.pattern.StatusReply} object
     **/
    message.respondTo.tell(StatusReply.success("Done"));
    /**
     *  Uncomment below to wrap error response in Generic {@link akka.pattern.StatusReply}
     *  message.respondTo.tell(StatusReply.error("I'm sorry, Dave. I'm afraid I can't do that."));
     **/
    return this;
  }
}


class GenericResponseDave extends AbstractBehavior<GenericResponseDave.Command> {

  protected interface Command {}

  private static final class AdaptedResponse implements GenericResponseDave.Command {
    public final String message;

    public AdaptedResponse(String message) {
      this.message = message;
    }
  }

  public static Behavior<GenericResponseDave.Command> create(
    ActorRef<GenericResponseHal.Command> genericResponseHal) {

    return Behaviors.setup(context -> new GenericResponseDave(context, genericResponseHal));
  }

  private GenericResponseDave(ActorContext<GenericResponseDave.Command> context,
                              ActorRef<GenericResponseHal.Command> genericResponseHal) {
    super(context);

    final Duration timeout = Duration.ofSeconds(3);
    context.askWithStatus(
      String.class,
      genericResponseHal,
      timeout,
      (ActorRef<StatusReply<String>> ref) -> new GenericResponseHal.OpenThePodBayDoorsPlease(ref),
      (response, throwable) -> {
        if (response != null) {
          return new GenericResponseDave.AdaptedResponse(response);
        } else {
          return new GenericResponseDave.AdaptedResponse("Request failed: " + throwable.getMessage());
        }
      });
  }

  @Override
  public Receive<GenericResponseDave.Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(GenericResponseDave.AdaptedResponse.class,
        response -> onAdaptedResponse(response))
      .build();
  }

  private Behavior<GenericResponseDave.Command> onAdaptedResponse(
    GenericResponseDave.AdaptedResponse response) {
    getContext().getLog().info("Got response from HAL: {}", response.message);
    return this;
  }
}

class GenericResponseWrapper {
  public static Behavior<Void> create() {
    return Behaviors.setup(
      context -> {
        ActorRef<GenericResponseHal.Command> halActorRef = context.spawn(GenericResponseHal.create(), "halActorRef");
        context.spawn(GenericResponseDave.create(halActorRef), "daveActorRef");
        return Behaviors.receive(Void.class).build();
      });
  }

  public static void main(String[] args) {
    ActorSystem.create(GenericResponseWrapper.create(), "clusterSystem");
  }
}
