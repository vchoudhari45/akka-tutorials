package com.vc.actor;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

class Printer {
  public final static class PrintMe {
    public final String message;
    public PrintMe(String message) {
      this.message = message;
    }
  }

  public static Behavior<PrintMe> create() {
    return Behaviors.setup(context ->
      Behaviors.receive(PrintMe.class)
        .onMessage(PrintMe.class, printMe -> {
          context.getLog().info(printMe.message);
          return Behaviors.same();
        })
        .build()
    );
  }
}

class FireAndForget {
  public static void main(String[] args) {
    final ActorSystem<Printer.PrintMe> system =
      ActorSystem.create(Printer.create(), "clusterSystem");

    /**
     *  fire & forget
     **/
    system.tell(new Printer.PrintMe("Message 1"));
  }
}
