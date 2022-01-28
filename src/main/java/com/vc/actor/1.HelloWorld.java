package com.vc.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

class GreeterActor extends AbstractBehavior<GreeterActor.Command> {
  protected interface Command{}

  public static final class ThankYou implements GreeterActor.Command {
    final ActorRef<GreetedActor.Command> from;
    public ThankYou(ActorRef<GreetedActor.Command> from) {
      this.from = from;
    }
  }

  static Behavior<GreeterActor.Command> create() {
    return Behaviors.setup(context -> new GreeterActor(context));
  }

  private GreeterActor(ActorContext<GreeterActor.Command> context) {
    super(context);

    /**
     * spawning a child actor, and sending {@link GreetedActor.Greetings} msg to it
     **/
    ActorRef<GreetedActor.Command> actorRef
      = getContext().spawn(GreetedActor.create(), "GreetedActor");

    actorRef.tell(new GreetedActor.Greetings("Greetings", getContext().getSelf()));
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
     .onMessage(ThankYou.class, thankYou -> onThankYou(thankYou))
     .build();
  }

  private Behavior<GreeterActor.Command> onThankYou(ThankYou thankYou) {
    getContext().getLog().info("Thank you received from Greeted actor {}", thankYou.from);
    return this;
  }
}

class GreetedActor extends AbstractBehavior<GreetedActor.Command> {
  protected interface Command{}

  public static final class Greetings implements GreetedActor.Command {
    final String msg;
    final ActorRef<GreeterActor.Command> from;
    public Greetings(String msg, ActorRef<GreeterActor.Command> from) {
      this.msg = msg;
      this.from = from;
    }
  }

  static Behavior<GreetedActor.Command> create() {
    return Behaviors.setup(context -> new GreetedActor(context));
  }

  private GreetedActor(ActorContext<GreetedActor.Command> context) {
    super(context);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
     .onMessage(Greetings.class, greetings -> onGreetings(greetings))
     .build();
  }

  private Behavior<GreetedActor.Command> onGreetings(Greetings greetings) {
    getContext().getLog().info("{} received from Greeting actor {}", greetings.msg, greetings.from);
    greetings.from.tell(new GreeterActor.ThankYou(getContext().getSelf()));
    return this;
  }
}

class HelloWorld {
  public static void main(String[] args) {
    ActorSystem.create(GreeterActor.create(), "actorSystem");
  }
}
