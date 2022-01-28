package com.vc.actor;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

class MasterControlProgramWatchingActor extends
  AbstractBehavior<MasterControlProgramWatchingActor.Command> {

  protected interface Command {}

  public static final class SpawnJob implements
    MasterControlProgramWatchingActor.Command {
    public final String name;
    public SpawnJob(String name) {
      this.name = name;
    }
  }

  public static Behavior<MasterControlProgramWatchingActor.Command> create() {
    return Behaviors.setup(context ->
      new MasterControlProgramWatchingActor(context));
  }

  public MasterControlProgramWatchingActor
    (ActorContext<MasterControlProgramWatchingActor.Command> context) {
    super(context);
  }

  @Override
  public Receive<MasterControlProgramWatchingActor.Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(SpawnJob.class, message -> onSpawnJob(message))
      .onSignal(Terminated.class, terminated -> onTerminated(terminated))
      .build();
  }

  private Behavior<Command> onSpawnJob(SpawnJob message) {
    getContext().getSystem().log().info("Spawning job {}", message.name);
    /**
     * spawning an actor
     **/
    ActorRef<JobWatchedActor.Command> job = getContext()
      .spawn(JobWatchedActor.create(message.name), message.name);

    /**
     * watching an actor
     **/
    getContext().watch(job);
    job.tell(new JobWatchedActor.Terminate());
    return this;
  }

  private Behavior<Command> onTerminated(Terminated terminated) {
    getContext().getSystem().log().info("Job stopped: {}", terminated.getRef().path().name());
    return this;
  }
}

class JobWatchedActor extends AbstractBehavior<JobWatchedActor.Command> {
  protected interface Command {}

  public static final class Terminate implements JobWatchedActor.Command {
    public Terminate() {}
  }

  public static Behavior<Command> create(String name) {
    return Behaviors.setup(context -> new JobWatchedActor(context, name));
  }

  private final String name;

  public JobWatchedActor(ActorContext<Command> context, String name) {
    super(context);
    this.name = name;
  }

  @Override
  public Receive<JobWatchedActor.Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(Terminate.class,  terminate -> onTerminate())
      .onSignal(PostStop.class, postStop -> onPostStop()).build();
  }

  private Behavior<Command> onTerminate() {
    getContext().getSystem().log().info("Received Terminate signal");
    return Behaviors.stopped();
  }

  private Behavior<Command> onPostStop() {
    getContext().getSystem().log().info("Worker {} stopped", name);
    return this;
  }
}

class WatchingActor {
  public static void main(String[] args) {
    ActorSystem<MasterControlProgramWatchingActor.Command> actorSystem
      = ActorSystem.create(MasterControlProgramWatchingActor.create(), "MasterProgram");

    actorSystem.tell(new MasterControlProgramWatchingActor.SpawnJob("Job-1"));
  }
}
