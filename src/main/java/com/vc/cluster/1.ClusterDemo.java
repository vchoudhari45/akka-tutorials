package com.vc.cluster;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.ClusterEvent;
import akka.cluster.typed.*;


class PrintActorCluster extends AbstractBehavior<PrintActorCluster.Command> {
  protected interface Command{}

  public static enum PrintClusterMemberEvent implements Command {
    INSTANCE
  }
  public static enum Print implements Command {
    INSTANCE
  }

  static Behavior<PrintActorCluster.Command> create() {
    return Behaviors.setup(context -> new PrintActorCluster(context));
  }

  private PrintActorCluster(ActorContext<PrintActorCluster.Command> context) {
    super(context);

    //Accessing the Cluster extension on each node
    Cluster cluster = Cluster.get(getContext().getSystem());

    //Joining the cluster, should be used when seed nodes are not specified
    //cluster.manager().tell(Join.create(cluster.selfMember().address()));

    //Subscribing to cluster state change events
    ActorRef<ClusterEvent.MemberEvent> clusterMemberEventActorRef = getContext()
      .messageAdapter(ClusterEvent.MemberEvent.class, memberEvent -> PrintClusterMemberEvent.INSTANCE);

    cluster.subscriptions()
      .tell(Subscribe.create(clusterMemberEventActorRef, ClusterEvent.MemberEvent.class));

    //Leaving the cluster, should be used when seed nodes are not specified
    cluster.manager().tell(Leave.create(cluster.selfMember().address()));
  }

  @Override
  public Receive<PrintActorCluster.Command> createReceive() {
    return newReceiveBuilder().build();
  }
}

class ClusterDemo {
  public static void main(String[] args) {
    ActorSystem<PrintActorCluster.Command> actorSystem =
      ActorSystem.create(PrintActorCluster.create(), "ClusterSystem");
  }
}
