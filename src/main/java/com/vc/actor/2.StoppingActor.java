package com.vc.actor;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;


class MasterControlProgram extends
  AbstractBehavior<MasterControlProgram.Command> {

  protected interface Command {}

  public static final class SpawnJob implements Command {
    public final String name;
    public SpawnJob(String name) {
      this.name = name;
    }
  }

  public enum GracefulShutdown implements Command {
    INSTANCE
  }

  public static Behavior<Command> create() {
    return Behaviors.setup(MasterControlProgram::new);
  }

  public MasterControlProgram(ActorContext<Command> context) {
    super(context);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(SpawnJob.class, this::onSpawnJob)
      .onMessage(GracefulShutdown.class, message -> onGracefulShutdown())
      .onSignal(PostStop.class, signal -> onPostStop())
      .build();
  }

  private Behavior<Command> onSpawnJob(SpawnJob message) {
    getContext().getSystem().log().info("Spawning job {}!", message.name);
    getContext().spawn(Job.create(message.name), message.name);
    return this;
  }

  private Behavior<Command> onGracefulShutdown() {
    getContext().getSystem().log().info("Initiating graceful shutdown...");
    /**
     * stopping an actor
     **/
    return Behaviors.stopped();
  }

  private Behavior<Command> onPostStop() {
    getContext().getSystem().log().info("Master Control Program stopped");
    return this;
  }
}

class Job extends AbstractBehavior<Job.Command> {
  protected interface Command {}

  public static Behavior<Command> create(String name) {
    return Behaviors.setup(context -> new Job(context, name));
  }

  private final String name;

  public Job(ActorContext<Command> context, String name) {
    super(context);
    this.name = name;
  }

  @Override
  public Receive<Job.Command> createReceive() {
    return newReceiveBuilder()
      .onSignal(PostStop.class, postStop -> onPostStop()).build();
  }

  private Behavior<Command> onPostStop() {
    getContext().getSystem().log().info("Worker {} stopped", name);
    return this;
  }
}

class StoppingActor {
  public static void main(String[] args) {
    ActorSystem<MasterControlProgram.Command> actorSystem
      = ActorSystem.create(MasterControlProgram.create(), "MasterProgram");

    actorSystem
      .tell(new MasterControlProgram.SpawnJob("Job-1"));

    actorSystem
      .tell(new MasterControlProgram.SpawnJob("Job-2"));

    actorSystem
      .tell(MasterControlProgram.GracefulShutdown.INSTANCE);
  }
}