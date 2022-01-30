package com.vc.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.Optional;

class Keys {}
class Wallet {}

class KeyCabinet {
  public static class GetKeys {
    public final String whoseKeys;
    public final ActorRef<Keys> replyTo;

    public GetKeys(String whoseKeys, ActorRef<Keys> respondTo) {
      this.whoseKeys = whoseKeys;
      this.replyTo = respondTo;
    }
  }

  public static Behavior<GetKeys> create() {
    return Behaviors.receiveMessage(getKeys -> onGetKeys(getKeys));
  }

  private static Behavior<GetKeys> onGetKeys(GetKeys message) {
    message.replyTo.tell(new Keys());
    return Behaviors.same();
  }
}

class Drawer {
  public static class GetWallet {
    public final String whoseWallet;
    public final ActorRef<Wallet> replyTo;

    public GetWallet(String whoseWallet, ActorRef<Wallet> replyTo) {
      this.whoseWallet = whoseWallet;
      this.replyTo = replyTo;
    }
  }

  public static Behavior<GetWallet> create() {
    return Behaviors.receiveMessage(getWalletMessage -> onGetWallet(getWalletMessage));
  }

  private static Behavior<GetWallet> onGetWallet(GetWallet getWalletMessage) {
    getWalletMessage.replyTo.tell(new Wallet());
    return Behaviors.same();
  }
}

class Home {

  public interface Command {}

  public static class LeaveHome implements Command {
    public final String who;
    public final ActorRef<ReadyToLeaveHome> respondTo;

    public LeaveHome(String who, ActorRef<ReadyToLeaveHome> respondTo) {
      this.who = who;
      this.respondTo = respondTo;
    }
  }

  public static class ReadyToLeaveHome {
    public final String who;
    public final Keys keys;
    public final Wallet wallet;

    public ReadyToLeaveHome(String who, Keys keys, Wallet wallet) {
      this.who = who;
      this.keys = keys;
      this.wallet = wallet;
    }
  }

  private final ActorContext<Command> context;
  private final ActorRef<KeyCabinet.GetKeys> keyCabinet;
  private final ActorRef<Drawer.GetWallet> drawer;

  private Home(ActorContext<Command> context) {
    this.context = context;
    this.keyCabinet = context.spawn(KeyCabinet.create(), "key-cabinet");
    this.drawer = context.spawn(Drawer.create(), "drawer");
  }

  private Behavior<Command> behavior() {
    return Behaviors.receive(Command.class)
      .onMessage(LeaveHome.class, leaveHome -> onLeaveHome(leaveHome))
      .build();
  }

  private Behavior<Command> onLeaveHome(LeaveHome message) {
    context.spawn(
      PrepareToLeaveHome.create(message.who, message.respondTo, keyCabinet, drawer),
      "leaving" + message.who);
    return Behaviors.same();
  }

  // actor behavior
  public static Behavior<Command> create() {
    return Behaviors.setup(context -> new Home(context).behavior());
  }
}

// per session actor behavior
class PrepareToLeaveHome extends AbstractBehavior<Object> {
  static Behavior<Object> create(
    String whoIsLeaving,
    ActorRef<Home.ReadyToLeaveHome> replyTo,
    ActorRef<KeyCabinet.GetKeys> keyCabinet,
    ActorRef<Drawer.GetWallet> drawer) {
    return Behaviors.setup(
      context -> new PrepareToLeaveHome(context, whoIsLeaving, replyTo, keyCabinet, drawer));
  }

  private final String whoIsLeaving;
  private final ActorRef<Home.ReadyToLeaveHome> replyTo;
  private final ActorRef<KeyCabinet.GetKeys> keyCabinet;
  private final ActorRef<Drawer.GetWallet> drawer;
  private Optional<Wallet> wallet = Optional.empty();
  private Optional<Keys> keys = Optional.empty();

  private PrepareToLeaveHome(
    ActorContext<Object> context,
    String whoIsLeaving,
    ActorRef<Home.ReadyToLeaveHome> replyTo,
    ActorRef<KeyCabinet.GetKeys> keyCabinet,
    ActorRef<Drawer.GetWallet> drawer) {
    super(context);
    this.whoIsLeaving = whoIsLeaving;
    this.replyTo = replyTo;
    this.keyCabinet = keyCabinet;
    this.drawer = drawer;
  }

  @Override
  public Receive<Object> createReceive() {
    return newReceiveBuilder()
      .onMessage(Wallet.class, wallet -> onWallet(wallet))
      .onMessage(Keys.class, keys -> onKeys(keys))
      .build();
  }

  private Behavior<Object> onWallet(Wallet wallet) {
    this.wallet = Optional.of(wallet);
    return completeOrContinue();
  }

  private Behavior<Object> onKeys(Keys keys) {
    this.keys = Optional.of(keys);
    return completeOrContinue();
  }

  private Behavior<Object> completeOrContinue() {
    if (wallet.isPresent() && keys.isPresent()) {
      replyTo.tell(new Home.ReadyToLeaveHome(whoIsLeaving, keys.get(), wallet.get()));
      return Behaviors.stopped();
    } else {
      return this;
    }
  }
}
