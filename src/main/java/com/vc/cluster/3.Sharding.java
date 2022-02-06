package com.vc.cluster;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;

class CounterSharding extends AbstractBehavior<CounterSharding.Command> {

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

  public static Behavior<Command> create(String entityId) {
    return Behaviors.setup(context -> new CounterSharding(context, entityId));
  }

  private final String entityId;
  private int value = 0;

  private CounterSharding(ActorContext<Command> context, String entityId) {
    super(context);
    this.entityId = entityId;
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(Increment.class, msg -> onIncrement())
      .onMessage(GetValue.class, msg -> onGetValue(msg))
      .build();
  }

  private Behavior<Command> onIncrement() {
    value++;
    return this;
  }

  private Behavior<Command> onGetValue(GetValue msg) {
    msg.replyTo.tell(value);
    return this;
  }
}

class CounterShardingDemo {
  public static Behavior<Void> create() {
    return Behaviors.setup(context -> {
      /**
       *  ClusterSharding started
       **/
      ClusterSharding sharding = ClusterSharding.get(context.getSystem());

      /**
       *  Sharding EntityTypeKey
       **/
      EntityTypeKey<CounterSharding.Command> typeKey =
        EntityTypeKey.create(CounterSharding.Command.class, "CounterSharding");

      /**
       *  Sharding Region
       **/
      ActorRef<ShardingEnvelope<CounterSharding.Command>> shardRegion =
        sharding.init(Entity.of(typeKey, ctx -> CounterSharding.create(ctx.getEntityId())));

      /**
       *  Sending msg to shardingRegion
       **/
      shardRegion.tell(new ShardingEnvelope("counter-1", CounterSharding.Increment.INSTANCE));

      return Behaviors.receive(Void.class).build();
    });
  }

  public static void main(String[] args) {
    ActorSystem.create(CounterShardingDemo.create(), "clusterSystem");
  }
}
