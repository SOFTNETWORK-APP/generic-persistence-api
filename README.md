# generic-persistence-api
a CQRS/ES framework in scala using akka persistence

![](docs/diagrams/src/CQRS.svg)

"CQRS is simply the creation of two objects where there was previously only one. The separation occurs based upon whether the methods are a command or a query (the same definition that is used by Meyer in Command and Query Separation: a command is any method that mutates state and a query is any method that returns a value)."
—Greg Young, CQRS, Task Based UIs, Event Sourcing agh!

## Write Side

### Akka Persistence

**generic-persistence-api** relies on [Akka Persistence](https://doc.akka.io/docs/akka/current/typed/persistence.html) which provides through **Cluster Sharding** and **EventSourcedBehavior** a **scalable** and **resilient** way to implement the write-side of CQRS using commands and events that simplify the implementation of event-sourced systems.

#### Cluster Sharding

Akka Persistence provides a powerful mechanism for scaling out stateful actors using cluster sharding. Cluster sharding allows stateful actors to be distributed across a cluster of nodes, while ensuring that messages are routed to the correct actor instance regardless of which node it is running on. This can help improve the scalability and reliability of distributed systems built using Akka Persistence.

Here's an overview of how cluster sharding works within Akka Persistence:

1. **Actor entity**: Each stateful actor that needs to be distributed using cluster sharding is referred to as an "actor entity".
2. **Sharding region**: A "sharding region" is responsible for managing a set of actor entities. Each sharding region is responsible for a subset of the actor entities in the system.
3. **Shard identifier**: Each actor entity is assigned a unique "shard identifier" that is used to determine which sharding region is responsible for managing that entity.
4. **Message routing**: When a message is sent to an actor entity, the message is first sent to the sharding region responsible for managing that entity. The sharding region then routes the message to the appropriate actor instance based on its shard identifier.
5. **Node awareness**: The sharding mechanism is aware of the state of the cluster, so it can ensure that actor instances are running on nodes that are currently available and healthy.
6. **Persistence**: Akka Persistence is used to persist the state of each actor entity, ensuring that the state is durable and can survive node failures and other types of system failures.

In the context of Akka's Cluster Sharding, the **ask pattern** can be used to send a message to an entity actor running on a remote node and receive a response back. Here's an overview of how the ask pattern works in the context of Cluster Sharding:

1. **Send a message**: An actor sends a message to an entity actor using its EntityRef.
2. **ask the entity**: The sender actor can call the ask method on the EntityRef to send the message and expect a response.
3. **Proxy actor**: When the ask method is called, the EntityRef creates a "proxy" actor to forward the message to the remote entity actor.
4. **Message routing**: The message is routed to the sharding coordinator and then to the correct node where the entity actor is running based on the entity's shard identifier.
5. **Message processing**: The entity actor receives the message and processes it, potentially modifying its state and sending back a response message.
6. **Future completion**: When the response message is received, the Future representing the response message is completed with the received response message.
7. **Timeout handling**: If the response message is not received within the specified timeout, the Future will be completed with a TimeoutException.

#### EventSourcedBehavior

The write-side using Akka **EventSourcedBehavior** typically involves the following steps:

1. **Command handling**: When a **command** is received, it is typically handled by an instance of the EventSourcedBehavior actor that represents the aggregate root for the corresponding domain entity. The actor uses the current **state** of the entity to determine how to handle the command, and may generate one or more **events** before eventually responding to the sender (**ask pattern**).
2. **Event persistence**: Once the events have been generated, they are appended to a **journal** (an event log using Akka Persistence's event sourcing mechanism), with additional **tags**. This allows events to be easily filtered and queried based on their tags, improving the efficiency of read-side **projections**.
3. **Event replay**: When the actor is created or restarted, Akka Persistence automatically replays all the events from the event log, allowing the actor to rebuild its current state based on the events.
4. **Snapshotting**: To avoid replaying all events every time the actor is created or restarted, Akka EventSourcedBehavior supports snapshotting. Every N events or when a given predicate of the state is fulfilled, the actor can save a **snapshot** of its current state, including any accumulated events and their tags after the previous snapshot. This snapshot is persisted alongside the event log and can be used to restore the state of the actor at a later point in time, rather than replaying all events from the beginning.

The framework handles the complexities of event persistence, replay, and snapshotting, allowing developers to focus on defining the behavior of the domain entity in response to commands. 

The resulting system is highly efficient, with the ability to quickly rebuild its state from a **snapshot** and replay only a subset of events, while still ensuring the accuracy of the system's state.

Moreover, providing the ability to **tag events** enables **read-side projections** to be easily implemented and maintained, improving the overall performance and scalability of the system.

### generic-persistence-api 

#### Commands

In CQRS, commands play a critical role in defining the write-side of the system. A command is a message that encapsulates a user's intent to perform a specific action, such as creating a new account, updating an existing record, or deleting data. The main purpose of commands is to initiate a change in the state of the system.

Command responses play also an important role in providing feedback to the user who initiated the command. When a user sends a command to the system, they expect a response that confirms that the command has been successfully executed or that an error has occurred.

The framework defines the trait **_Command_** as the root interface of any command sends to a recipient in the system, and the trait **_CommandResult_** as the root interface of any command response.

![](docs/diagrams/out/Command.svg)

#### Events

Events provide the basis for synchronizing the changes on the write-side (that result from processing commands) with the read side.

If the write-side raises an event whenever the state of the application changes, the read side should respond to that event and update the data that is used by its queries and views.

The framework defines the trait **_Event_** as the root interface of any event in the system.

![](docs/diagrams/out/Event.svg)

#### State

The state of an entity actor can be divided into two parts: the in-memory state and the persistent state.

The in-memory state represents the current state of the actor as it is being processed. This state is modified as the actor receives commands and generates events in response. The in-memory state can be any type of data structure, such as a case class or a collection.

The persistent state represents the state of the actor as it has been stored in the journal. This state includes all events that have been generated by the actor, and can be used to rebuild the in-memory state of the actor in case of a failure or restart. The persistent state is maintained by the Akka Persistence journal and is not directly accessible by the actor.

When an entity actor is created, it starts with an empty in-memory state and persistent state. As events are persisted to the journal, the persistent state is updated, and the in-memory state is rebuilt by replaying the events from the journal.

The framework defines the trait **_State_** as the root interface of the in-memory state of any entity actor.

![](docs/diagrams/out/State.svg)

#### Serialization

Akka Persistence provides a built-in serialization mechanism that uses the Akka Serialization library. This library allows you to define serializers for your custom data types, so that they can be serialized and deserialized automatically when they are persisted to the event store.

When an Akka actor receives a command, it creates one or more domain events and sends them to the event journal for persistence. Before the events are persisted, they are serialized using the configured serialization mechanism. When events are read from the event journal, they are deserialized back into domain events using the same mechanism.

Finally, before persisting a snapshot, the current state of the actor (its in-memory state) is serialized using the configured serialization mechanism.

The framework supports natively three types of **Serializers**:
+ **proto**
+ **jackson-cbor**
+ **chill**

```hocon
akka {
  actor {
    allow-java-serialization = off

    serializers {
      proto = "akka.remote.serialization.ProtobufSerializer"
      jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
      chill  = "com.twitter.chill.akka.AkkaSerializer"
    }
...
  }
}
```

The default [configuration](core/src/main/resources/softnetwork-persistence.conf) defines the following serialization bindings :

```hocon
akka {
  actor {
...
    enable-additional-serialization-bindings = on

    serialization-bindings {
      "app.softnetwork.persistence.model.package$Timestamped" = proto
      "app.softnetwork.persistence.message.package$ProtobufEvent" = proto # protobuf events
      "app.softnetwork.persistence.model.package$ProtobufDomainObject" = proto # protobuf domain objects
      "app.softnetwork.persistence.model.package$ProtobufStateObject" = proto # protobuf state objects

      "app.softnetwork.persistence.message.package$CborEvent" = jackson-cbor # cbor events
      "app.softnetwork.persistence.model.package$CborDomainObject" = jackson-cbor # cbor domain objects

      "app.softnetwork.persistence.message.package$Command" = chill
      "app.softnetwork.persistence.message.package$CommandResult" = chill
      "app.softnetwork.persistence.message.package$Event" = chill
      "app.softnetwork.persistence.model.package$State" = chill

    }
  }
}
```

![](docs/diagrams/out/Serialization.svg)

##### Versioning

You may find it necessary to change the definition of a particular event type or aggregate at some point in the future.
You must consider how your system will be able to handle multiple versions of an event type and aggregates.

#### Entity Behavior

![](docs/diagrams/out/EntityBehavior.svg)

#### Entity Pattern

As we have seen, Cluster Sharding is a distributed system mechanism in Akka that allows you to partition actor entities across multiple nodes in a cluster. Each entity is uniquely identified by a shard identifier (EntityTypeKey) and an entity identifier. 

The shard identifier represents a group of entities that share the same state, and the entity identifier identifies a specific entity within that shard.

When a command is sent to an entity, the "entity pattern" is used to locate the actor reference for that entity. The shard identifier is used to determine which node in the cluster is responsible for managing the shard, and the entity identifier is used to locate the specific actor entity within that shard on that node.

Once the actor reference for the entity has been located, the command is sent to the EntityBehavior, which processes it and updates its state. The state changes are then persisted using Akka Persistence, which ensures that the state changes are durable and can be recovered in the event of a node failure.

![](docs/diagrams/out/Patterns.svg)

### Event Sourcing with generic-persistence-api

![](docs/diagrams/out/EventSourcing.svg)

