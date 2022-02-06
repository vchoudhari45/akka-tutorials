package com.vc.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

class CookieResponder
  extends AbstractBehavior<CookieResponder.Command> {

  protected interface Command{}

  public static final class CookieRequest
    implements CookieResponder.Command {
    final long requestId;
    final ActorRef<CookieRequester.Command> replyTo;

    public CookieRequest(long requestId, ActorRef<CookieRequester.Command> replyTo) {
      this.requestId = requestId;
      this.replyTo = replyTo;
    }
  }

  static Behavior<CookieResponder.Command> create() {
    return Behaviors.setup(context -> new CookieResponder(context));
  }

  public CookieResponder(ActorContext<Command> context) {
    super(context);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(CookieRequest.class, cookieRequest -> onCookieRequest(cookieRequest))
      .build();
  }

  private Behavior<CookieResponder.Command> onCookieRequest
    (CookieRequest cookieRequest) {

    getContext()
      .getLog().info("Cookie request received from {}", cookieRequest.replyTo);

    cookieRequest.replyTo
      .tell(new CookieRequester.CookieResponse(cookieRequest.requestId));

    return this;
  }
}

class CookieRequester
  extends AbstractBehavior<CookieRequester.Command> {
  
  protected interface Command{}

  public static final class CookieResponse
    implements CookieRequester.Command {
      final long requestId;
      public CookieResponse(long requestId) {
        this.requestId = requestId;
      }
  }

  static Behavior<CookieRequester.Command> create() {
    return Behaviors.setup(context -> new CookieRequester(context));
  }

  public CookieRequester(ActorContext<Command> context) {
    super(context);

    ActorRef<CookieResponder.Command> cookieResponder =
      getContext().spawn(CookieResponder.create(), "CookieResponder");

    cookieResponder
      .tell(new CookieResponder.CookieRequest(1l, getContext().getSelf()));
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(CookieRequester.CookieResponse.class,
        cookieResponse -> onCookieResponse(cookieResponse))
      .build();
  }

  private Behavior<CookieRequester.Command>
  onCookieResponse(CookieResponse cookieResponse) {
    getContext().getLog()
      .info("I received a cookie, requestId: {}", cookieResponse.requestId);
    return this;
  }

  public static void main(String[] args) {
    ActorSystem.create(CookieRequester.create(), "clusterSystem");
  }
}