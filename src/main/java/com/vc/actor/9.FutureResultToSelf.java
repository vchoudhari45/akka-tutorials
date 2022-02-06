package com.vc.actor;


import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

interface CustomerDataAccess {
  CompletionStage<Done> update(Customer customer);
}

class Customer {
  public final String id;
  public final long version;
  public final String name;
  public final String address;

  public Customer(String id, long version, String name, String address) {
    this.id = id;
    this.version = version;
    this.name = name;
    this.address = address;
  }
}

class CustomerRepository extends AbstractBehavior<CustomerRepository.Command> {

  private static final int MAX_OPERATIONS_IN_PROGRESS = 10;

  protected interface Command {}

  public static class Update implements Command {
    public final Customer customer;
    public final ActorRef<FutureResultToSelf.Command> replyTo;

    public Update(Customer customer, ActorRef<FutureResultToSelf.Command> replyTo) {
      this.customer = customer;
      this.replyTo = replyTo;
    }
  }

  private final CustomerDataAccess dataAccess;
  private int operationsInProgress = 0;

  private CustomerRepository(ActorContext<Command> context, CustomerDataAccess dataAccess) {
    super(context);
    this.dataAccess = dataAccess;
  }

  public static Behavior<Command> create(CustomerDataAccess dataAccess) {
    return Behaviors.setup(context -> new CustomerRepository(context, dataAccess));
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(Update.class, this::onUpdate)
      .onMessage(FutureResultToSelf.WrappedUpdateResult.class, this::onUpdateResult)
      .build();
  }

  private Behavior<Command> onUpdate(Update command) {
    getContext().getLog().info("Received update {}", command.customer.id);
    if (operationsInProgress == MAX_OPERATIONS_IN_PROGRESS) {
      command.replyTo.tell(
        new FutureResultToSelf.UpdateFailure(
          command.customer.id,
          "Max " + MAX_OPERATIONS_IN_PROGRESS + " concurrent operations supported"));
    } else {
      operationsInProgress++;
      /**
       * Actor can send a message to itself using getContext().pipeToSelf
       * getContext().pipeToSelf should be used calling long running operation as below
       **/
      CompletionStage<Done> futureResult = dataAccess.update(command.customer);
      getContext()
        .pipeToSelf(
          futureResult,
          (ok, exc) -> {
            if (exc == null)
              return new FutureResultToSelf.WrappedUpdateResult(
                new FutureResultToSelf.UpdateSuccess(command.customer.id), command.replyTo);
            else
              return new FutureResultToSelf.WrappedUpdateResult(
                new FutureResultToSelf.UpdateFailure(command.customer.id, exc.getMessage()),
                command.replyTo);
          });
    }
    return this;
  }

  private Behavior<Command> onUpdateResult(FutureResultToSelf.WrappedUpdateResult wrapped) {
    getContext().getLog().info("Received updateResult ");
    operationsInProgress--;
    wrapped.replyTo.tell(wrapped.result);
    return this;
  }
}

class FutureResultToSelf extends AbstractBehavior<FutureResultToSelf.Command> {
  protected interface Command {}

  public static class UpdateSuccess implements Command {
    public final String id;

    public UpdateSuccess(String id) {
      this.id = id;
    }
  }

  public static class UpdateFailure implements Command {
    public final String id;
    public final String reason;

    public UpdateFailure(String id, String reason) {
      this.id = id;
      this.reason = reason;
    }
  }

  public static class WrappedUpdateResult implements CustomerRepository.Command {
    public final Command result;
    public final ActorRef<Command> replyTo;

    public WrappedUpdateResult(Command result, ActorRef<Command> replyTo) {
      this.result = result;
      this.replyTo = replyTo;
    }
  }

  public static Behavior<FutureResultToSelf.Command> create() {
    return Behaviors.setup(context -> new FutureResultToSelf(context));
  }

  private FutureResultToSelf(ActorContext<FutureResultToSelf.Command> context) {
    super(context);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().build();
  }
}


class FutureResultToSelfDemo {

  public static Behavior<Void> create() {
    return Behaviors.setup(context -> {
      ActorRef<CustomerRepository.Command> customerDAOActorRef =
        context.spawn(CustomerRepository.create(new CustomerDataAccess() {
          @Override
          public CompletionStage<Done> update(Customer customer) {
            return CompletableFuture.completedFuture(Done.getInstance());
          }
        }), "customerDAOActorRef");

      ActorRef<FutureResultToSelf.Command> actorRef = context
        .spawn(FutureResultToSelf.create(), "actorRef");

      customerDAOActorRef.tell(new CustomerRepository.Update(
        new Customer(
          "cust001",
          1L,
          "customerName",
          "customerAddress"), actorRef));

        return Behaviors.receive(Void.class).build();
    });
  }

  public static void main(String[] args) {
    ActorSystem.create(FutureResultToSelfDemo.create(), "clusterSystem");
  }
}