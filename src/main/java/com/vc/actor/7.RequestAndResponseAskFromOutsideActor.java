package com.vc.actor;


import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

class CookieFabric extends AbstractBehavior<CookieFabric.Command> {

  protected interface Command {}
  public static class GiveMeCookies implements Command {
    public final int count;
    public final ActorRef<Reply> replyTo;

    public GiveMeCookies(int count, ActorRef<Reply> replyTo) {
      this.count = count;
      this.replyTo = replyTo;
    }
  }

  protected interface Reply {}
  public static class Cookies implements Reply {
    public final int count;

    public Cookies(int count) {
      this.count = count;
    }
  }

  public static class InvalidRequest implements Reply {
    public final String reason;

    public InvalidRequest(String reason) {
      this.reason = reason;
    }
  }

  public static Behavior<Command> create() {
    return Behaviors.setup(CookieFabric::new);
  }

  private CookieFabric(ActorContext<Command> context) {
    super(context);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().onMessage(GiveMeCookies.class, this::onGiveMeCookies).build();
  }

  private Behavior<Command> onGiveMeCookies(GiveMeCookies request) {
    if (request.count >= 5) request.replyTo.tell(new InvalidRequest("Too many cookies."));
    else request.replyTo.tell(new Cookies(request.count));
    return this;
  }
}

class RequestAndResponseAskFromOutsideActor {
  public static void main(String[] args) {
    ActorSystem<CookieFabric.Command> cookieFabric =
      ActorSystem.create(CookieFabric.create(), "actorSystem");

    /**
     *  RequestResponse with Ask from outside of the actor
     **/
    CompletionStage<CookieFabric.Reply> result =
      AskPattern.ask(
        cookieFabric,
        replyTo -> new CookieFabric.GiveMeCookies(3, replyTo),
        Duration.ofSeconds(1),
        cookieFabric.scheduler());

    result.whenComplete(
      (reply, failure) -> {
        if (reply instanceof CookieFabric.Cookies)
          System.out.println("Yay, " + ((CookieFabric.Cookies) reply).count + " cookies!");
        else if (reply instanceof CookieFabric.InvalidRequest)
          System.out.println("No cookies for me. " + ((CookieFabric.InvalidRequest) reply).reason);
        else System.out.println("Boo! didn't get cookies in time. " + failure);
      });
  }
}
