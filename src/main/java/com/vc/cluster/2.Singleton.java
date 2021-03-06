package com.vc.cluster;


import akka.actor.typed.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.SingletonActor;

class Counter extends AbstractBehavior<Counter.Command> {
  protected interface Command {}

  public enum Increment implements Command {
    INSTANCE
  }

  public static class GetValue implements Command {
    private final ActorRef<Integer> replyTo;

    public GetValue(ActorRef<Integer> replyTo) {
      this.replyTo = replyTo;
    }
  }

  public enum GoodByeCounter implements Command {
    INSTANCE
  }

  public static Behavior<Command> create() {
    return Behaviors.setup(context -> new Counter(context));
  }

  private int value = 0;

  private Counter(ActorContext<Command> context) {
    super(context);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(Increment.class, msg -> onIncrement())
      .onMessage(GetValue.class, msg -> onGetValue(msg))
      .onMessage(GoodByeCounter.class, msg -> onGoodByCounter())
      .build();
  }

  private Behavior<Command> onIncrement() {
    getContext().getLog().info("Incrementing value: "+value);
    value++;
    return this;
  }

  private Behavior<Command> onGetValue(GetValue msg) {
    msg.replyTo.tell(value);
    return this;
  }

  private Behavior<Command> onGoodByCounter() {
    return this;
  }
}

class SingletonDemo {
  public static Behavior<Integer> create() {
    return Behaviors.setup(context -> {
      ClusterSingleton singleton = ClusterSingleton.get(context.getSystem());

      ActorRef<Counter.Command> proxy =
        singleton.init(SingletonActor.of(Counter.create(), "GlobalCounter"));

      proxy.tell(Counter.Increment.INSTANCE);
      proxy.tell(new Counter.GetValue(context.getSelf()));

      return Behaviors.receive(Integer.class).build();
    });
  }

  public static void main(String[] args) {
    ActorSystem.create(SingletonDemo.create(), "clusterSystem");
  }
}