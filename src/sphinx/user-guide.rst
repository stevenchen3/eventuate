.. _user-guide:

----------
User guide
----------

This is a brief user guide to Eventuate. It is recommended to read sections :ref:`overview` and :ref:`architecture` first. Based on simple examples, you’ll see how to

- implement an event-sourced actor
- replicate actor state with event sourcing
- detect concurrent updates to replicated state
- track conflicts from concurrent updates
- resolve conflicts automatically and interactively
- make concurrent updates conflict-free with operation-based CRDTs
- implement an event-sourced view over many event-sourced actors
- achieve causal read consistency across event-sourced actors and views and
- implement event-driven communication between event-sourced actors.

The user guide only scratches the surface of Eventuate. You can find further details in the :ref:`reference`.

.. _guide-event-sourced-actors:

Event-sourced actors
--------------------

An event-sourced actor is an actor that captures changes to its internal state as a sequence of events. It *persists* these events to an event log and *replays* them to recover internal state after a crash or a planned re-start. This is the basic idea behind `event sourcing`_: instead of storing current application state, the full history of changes is stored as *immutable facts* and current state is derived from these facts.

Event-sourced actors distinguish between *commands* and *events*. During command processing they usually validate external commands against internal state and, if validation succeeds, write one or more events to their event log. During event processing they consume events they have written and update internal state by handling these events.

.. hint::
   Event-sourced actors can also write new events during event processing. This is covered in section :ref:`guide-event-driven-communication`.

Concrete event-sourced actors must implement the ``EventsourcedActor`` trait. The following ``ExampleActor`` maintains state of type ``Vector[String]`` to which entries can be appended:

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: event-sourced-actor
   .. includecode:: code/userguide/japi/ActorExample.java
      :snippet: event-sourced-actor

For modifying ``currentState``, applications send ``Append`` commands which are handled by the ``onCommand`` handler. From an ``Append`` command, the handler derives an ``Appended`` event and ``persist``\ s it to the given ``eventLog``. If persistence succeeds, the command sender is informed about successful processing. If persistence fails, the command sender is informed about the failure so it can retry, if needed. 

The ``onEvent`` handler updates ``currentState`` from persisted events and is automatically called after a successful ``persist``. If the actor is re-started, either after a crash or during normal application start, persisted events are replayed to ``onEvent`` which recovers internal state before new commands are processed.

``EventsourcedActor`` implementations must define a global unique ``id`` and require an ``eventLog`` actor reference for writing and replaying events. An event-sourced actor may also define an optional ``aggregateId`` which has an impact how events are routed between event-sourced actors.

.. hint::
   Section :ref:`event-log` explains how to create ``eventLog`` actor references. 

Creating a single instance
~~~~~~~~~~~~~~~~~~~~~~~~~~

In the following, a single instance of ``ExampleActor`` is created and two ``Append`` commands are sent to it:

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: create-one-instance
   .. includecode:: code/userguide/japi/ActorExample.java
      :snippet: create-one-instance

Sending a ``Print`` command 

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: print-one-instance
   .. includecode:: code/userguide/japi/ActorExample.java
      :snippet: print-one-instance

should print::

    [id = 1, aggregate id = a] a,b

When the application is re-started, persisted events are replayed to ``onEvent`` which recovers ``currentState``. Sending another ``Print`` command should print again::

    [id = 1, aggregate id = a] a,b

.. note::
   In the following sections, several instances of ``ExampleActor`` are created. It is assumed that they share a :ref:`replicated-event-log` and are running at different *locations*. 

   A shared event log is a pre-requisite for event-sourced actors to consume each other’s events. However, sharing an event log doesn’t necessarily mean broadcast communication between all actors on the same log. It is the ``aggreagteId`` that determines which actors consume each other’s events.

Creating two isolated instances
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When creating two instances of ``ExampleActor`` with different ``aggregateId``\ s, they are isolated from each other, by default, and do not consume each other’s events:

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: create-two-instances
   .. includecode:: code/userguide/japi/ActorExample.java
      :snippet: create-two-instances

Sending two ``Print`` commands

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: print-two-instances
   .. includecode:: code/userguide/japi/ActorExample.java
      :snippet: print-two-instances

should print::

    [id = 2, aggregate id = b] a,b
    [id = 3, aggregate id = c] x,y

Creating two replica instances
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When creating two ``ExampleActor`` instances with the same ``aggregateId``, they consume each other’s events [#]_.

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: create-replica-instances
   .. includecode:: code/userguide/japi/ActorExample.java
      :snippet: create-replica-instances

Here, ``d4`` processes an ``Append`` command and persists an ``Appended`` event. Both, ``d4`` and ``d5``, consume that event and update their internal state. After waiting a bit for convergence, sending a ``Print`` command to both actors should print::

    [id = 4, aggregate id = d] a
    [id = 5, aggregate id = d] a

After both replicas have converged, another ``Append`` is sent to ``d5``. 

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: send-another-append
   .. includecode:: code/userguide/japi/ActorExample.java
      :snippet: send-another-append

Again both actors consume the event and sending another ``Print`` command should print::

    [id = 4, aggregate id = d] a,b
    [id = 5, aggregate id = d] a,b

.. warning::
   As you have probably recognized, replica convergence in this example can only be achieved if the second ``Append`` command is sent after both actors have processed the ``Appended`` event from the first ``Append`` command. 

   In other words, the first ``Appended`` event must *happen before* the second one. Only in this case, these two events can have a causal relationship. Since events are guaranteed to be delivered in potential causal order to all replicas, they can converge to the same state.

   When concurrent updates are made to both replicas, the corresponding ``Appended`` events are not causally related and can be delivered in any order to both replicas. This may cause replicas to diverge because *append* operations do not commute. The following sections give examples how to detect and handle concurrent updates.

Detecting concurrent updates
----------------------------

Eventuate tracks *happened-before* relationships (= potential causality) of events with :ref:`vector-clocks`. Why is that needed at all? Let’s assume that an event-sourced actor emits an event ``e1`` for changing internal state and later receives an event ``e2`` from a replica instance. If the replica instance emits ``e2`` after having processed ``e1``, the actor can apply ``e2`` as regular update. If the replica instance emits ``e2`` before having received ``e1``, the actor receives a concurrent, potentially conflicting event. 

How can the actor determine if ``e2`` is a regular i.e. causally related or concurrent update? It can do so by comparing the vector timestamps of ``e1`` and ``e2``, where ``t1`` is the vector timestamp of ``e1`` and ``t2`` the vector timestamp of ``e2``. If events ``e1`` and ``e2`` are concurrent then ``t1 conc t2`` evaluates to ``true``. Otherwise, they are causally related and ``t1 < t2`` evaluates to ``true`` (because ``e1`` *happened-before* ``e2``).

The vector timestamp of an event can be obtained with ``lastVectorTimestamp`` during event processing. Vector timestamps can be attached as *update timestamp* to current state and compared with the vector timestamp of a new event in order to determine whether the new event is causally related to the previous state update or not\ [#]_:

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: detecting-concurrent-update
   .. includecode:: code/userguide/japi/ConcurrentExample.java
      :snippet: detecting-concurrent-update

Attaching update timestamps to current state and comparing them with vector timestamps of new events can be easily abstracted over so that applications don’t have to deal with these low level details, as shown in the next section. 

.. _tracking-conflicting-versions:

Tracking conflicting versions
-----------------------------

If state update operations from concurrent events do not commute, conflicting versions of actor state arise that must be tracked and resolved. This can be done with Eventuate’s ``ConcurrentVersions[S, A]`` abstraction and an application-defined *update function* of type ``(S, A) => S`` where ``S`` is the type of actor state and ``A`` the update type. In our example, the ``ConcurrentVersions`` type is ``ConcurrentVersions[Vector[String], String]`` and the update function ``(s, a) => s :+ a``:

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: tracking-conflicting-versions
   .. includecode:: code/userguide/japi/TrackingExample.java
      :snippet: tracking-conflicting-versions

Internally, ``ConcurrentVersions`` maintains versions of actor state in a tree structure where each concurrent ``update`` creates a new branch. The shape of the tree is determined solely by the vector timestamps of the corresponding update events. 

An event’s vector timestamp is passed as ``lastVectorTimestamp`` argument to ``update``. The ``update`` method internally creates a new version by applying the update function ``(s, a) => s :+ a`` to the closest predecessor version and the actual update value (``entry``). The ``lastVectorTimestamp`` is attached as update timestamp to the newly created version.

Concurrent versions of actor state and their update timestamp can be obtained with ``all`` which is a sequence of type ``Seq[Versioned[Vector[String]]]`` in our example. The Versioned_ data type represents a particular version of actor state and its update timestamp (= ``vectorTimestamp`` field).  

If ``all`` contains only a single element, there is no conflict and the element represents the current, conflict-free actor state. If the sequence contains two or more elements, there is a conflict where the elements represent conflicting versions of actor states. They can be resolved either automatically or interactively.

.. note::
   Only concurrent updates to replicas with the same ``aggregateId`` may conflict. Concurrent updates to actors with different ``aggregateId`` do not conflict (unless an application does custom :ref:`event-routing`).

   Also, if the data type of actor state is designed in a way that update operations commute, concurrent updates can be made conflict-free. This is discussed in section :ref:`commutative-replicated-data-types`.

Resolving conflicting versions
------------------------------

.. _automated-conflict-resolution:

Automated conflict resolution
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The following is a simple example of automated conflict resolution: if a conflict has been detected, the version with the higher wall clock timestamp is selected to be the winner. In case of equal wall clock timestamps, the version with the lower emitter id is selected. The wall clock timestamp can be obtained with ``lastSystemTimestamp`` during event handling, the emitter id with ``lastEmitterId``. The emitter id is the ``id`` of the ``EventsourcedActor`` that emitted the event.

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: automated-conflict-resolution
   .. includecode:: code/userguide/japi/ResolveExample.java
      :snippet: automated-conflict-resolution

Here, conflicting versions are sorted by descending wall clock timestamp and ascending emitter id where the latter is tracked as ``creator`` of the version. The first version is selected to be the winner. Its vector timestamp is passed as argument to ``resolve`` which selects this version and discards all other versions.

More advanced conflict resolution could select a winner depending on the actual value of concurrent versions. After selection, an application could even update the winner with the *merged* value of all conflicting versions\ [#]_.

.. note::
   For replicas to converge, it is important that winner selection does not depend on the order of conflicting events. In our example, this is the case because wall clock timestamp and emitter id comparison is transitive.

Interactive conflict resolution
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Interactive conflict resolution does not resolve conflicts immediately but requests the user to inspect and resolve a conflict. The following is a very simple example of interactive conflict resolution: a user selects a winner version if conflicting versions of application state exist.

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: interactive-conflict-resolution
   .. includecode:: code/userguide/japi/ResolveExample.java
      :snippet: interactive-conflict-resolution

When a user tries to ``Append`` in presence of a conflict, the ``ExampleActor`` rejects the update and requests the user to select a winner version from a sequence of conflicting versions. The user then sends the update timestamp of the winner version as ``selectedTimestamp`` with a ``Resolve`` command from which a ``Resolved`` event is derived and persisted. Handling of ``Resolved`` at all replicas finally resolves the conflict.

In addition to just selecting a winner, an application could also update the winner version in a second step, for example, with a value derived from the merge result of conflicting versions. Support for *atomic*, interactive conflict resolution with an application-defined merge function is planned for later Eventuate releases.

.. note::
   Interactive conflict resolution requires agreement among replicas that are affected by a given conflict: only one of them may emit the ``Resolved`` event. This does not necessarily mean distributed lock acquisition or leader (= resolver) election but can also rely on static rules such as *only the initial creator location of an aggregate is allowed to resolve the conflict*\ [#]_. This rule is implemented in the :ref:`example-application`.

.. _commutative-replicated-data-types:

Operation-based CRDTs
---------------------

If state update operations commute, there’s no need to use Eventuate’s ``ConcurrentVersions`` utility. A simple example is a replicated counter, which converges because its increment and decrement operations commute. 

A formal to approach to commutative replicated data types (CmRDTs) or operation-based CRDTs is given in the paper `A comprehensive study of Convergent and Commutative Replicated Data Types`_ by Marc Shapiro et al. Eventuate is a good basis for implementing operation-based CRDTs:

- Update operations can be modeled as events and reliably broadcasted to all replicas by a :ref:`replicated-event-log`.
- The command and event handler of an event-sourced actor can be used to implement the two update phases mentioned in the paper: *atSource* and *downstream*, respectively.
- All *downstream* preconditions mentioned in the paper are satisfied in case of causal delivery of update operations which is guaranteed for actors consuming from a replicated event log.

Eventuate currently implements 5 out of 12 operation-based CRDTs specified in the paper. These are *Counter*, *MV-Register*, *LWW-Register*, *OR-Set* and *OR-Cart* (a shopping cart CRDT). They can be instantiated and used via their corresponding *CRDT services*. CRDT operations are asynchronous methods on the service interfaces. CRDT services free applications from dealing with low-level details like event-sourced actors or command messages directly. The following is the definition of ORSetService_:

.. tabbed-code::
    .. includecode:: ../../eventuate-crdt/src/main/scala/com/rbmhtechnology/eventuate/crdt/ORSet.scala
       :snippet: or-set-service
    .. includecode:: code/userguide/japi/CrdtExample.java
       :snippet: or-set-service

The ORSetService_ is a CRDT service that manages ORSet_ instances. It implements the asynchronous ``add`` and ``remove`` methods and inherits the ``value(id: String): Future[Set[A]]`` method from ``CRDTService[ORSet[A], Set[A]]`` for reading the current value. Their ``id`` parameter identifies an ``ORSet`` instance. Instances are automatically created by the service on demand. A usage example is the ReplicatedOrSetSpec_ that is based on Akka’s `multi node testkit`_.

A CRDT service also implements a ``save(id: String): Future[SnapshotMetadata]`` method for saving CRDT snapshots. :ref:`snapshots` may reduce recovery times of CRDTs with a long update history but are not required for CRDT persistence. 

New operation-based CRDTs and their corresponding services can be developed with the CRDT development framework, by defining an instance of the CRDTServiceOps_ type class and implementing the CRDTService_ trait. Take a look at the `CRDT sources`_ for examples. 

.. hint::
   Eventuate’s CRDT approach is also described in `this article`_.

.. _this article: http://krasserm.github.io/2016/10/19/operation-based-crdt-framework/

.. _guide-event-sourced-views:

Event-sourced views
-------------------

Event-sourced views are a functional subset of event-sourced actors. They can only consume events from an event log but cannot produce new events. Concrete event-sourced views must implement the ``EventsourcedView`` trait. In the following example, the view counts all ``Appended`` and ``Resolved`` events emitted by all event-sourced actors to the same ``eventLog``:

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: event-sourced-view
   .. includecode:: code/userguide/japi/ViewExample.java
      :snippet: event-sourced-view

Event-sourced views handle events in the same way as event-sourced actors by implementing an ``onEvent`` handler. The ``onCommand`` handler in the example processes the queries ``GetAppendCount`` and ``GetResolveCount``.

``ExampleView`` implements the mandatory global unique ``id`` but doesn’t define an ``aggregateId``. A view that doesn’t define an ``aggregateId`` can consume events from all event-sourced actors on the same event log. If it defines an ``aggregateId`` it can only consume events from event-sourced actors with the same ``aggregateId`` (assuming the default :ref:`event-routing` rules). 

.. hint::
   While event-sourced views maintain view state in-memory, :ref:`ref-event-sourced-writers` can be used to persist view state to external databases. A specialization of event-sourced writers are :ref:`ref-event-sourced-processors` whose external database is an event log.

.. _conditional-requests:

Conditional requests
--------------------

Causal read consistency is the default when reading state from a single event-sourced actor or view. The event stream received by that actor is always causally ordered, hence, it will never see an *effect* before having seen its *cause*. 

The situation is different when a client reads from multiple actors. Imagine two event-sourced actor replicas where a client updates one replica and observes the updated state with the reply. A subsequent from the other replica, made by the same client, may return the old state which violates causal consistency. 

Similar considerations can be made for reading from an event-sourced view after having made an update to an event-sourced actor. For example, an application that successfully appended an entry to ``ExampleActor`` may not immediately see that update in the ``appendCount`` of ``ExampleView``. To achieve causal read consistency, the view should delay command processing until the emitted event has been consumed by the view. This can be achieved with a ``ConditionalRequest``.

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: conditional-requests
   .. includecode:: code/userguide/japi/ConditionalExample.java
      :snippet: conditional-requests

Here, the ``ExampleActor`` includes the event’s vector timestamp in its ``AppendSuccess`` reply. Together with the actual ``GetAppendCount`` command, the timestamp is included as condition in a ``ConditionalRequest`` and sent to the view. For ``ConditionalRequest`` processing, an event-sourced view must extend the ``ConditionalRequests`` trait. ``ConditionalRequests`` internally delays the command, if needed, and only dispatches ``GetAppendCount`` to the view’s ``onCommand`` handler if the condition timestamp is in the *causal past* of the view (which is earliest the case when the view consumed the update event). When running the example with an empty event log, it should print::

    append count = 1

.. note::
   Not only event-sourced views but also event-sourced actors, stateful event-sourced writers and processors can extend ``ConditionalRequests``. Delaying conditional requests may re-order them relative to other conditional and non-conditional requests.

.. _guide-event-driven-communication:

Event-driven communication
--------------------------

Earlier sections have already shown one form of event collaboration: *state replication*. For that purpose, event-sourced actors of the same type exchange their events to re-construct actor state at different locations. 

In more general cases, event-sourced actors of different type exchange events to achieve a common goal. They react on received events by updating internal state and producing new events. This form of event collaboration is called *event-driven communication*. In the following example, two event-actors collaborate in a ping-pong game where 

- a ``PingActor`` emits a ``Ping`` event on receiving a ``Pong`` event and
- a ``PongActor`` emits a ``Pong`` event on receiving a ``Ping`` event

.. tabbed-code::
   .. includecode:: code/UserGuideDoc.scala
      :snippet: event-driven-communication
   .. includecode:: code/userguide/japi/CommunicationExample.java
      :snippet: event-driven-communication

The ping-pong game is started by sending the ``PingActor`` a ``”serve”`` command which ``persist``\ s the first ``Ping`` event. This event however is not consumed by the emitter but rather by the ``PongActor``. The ``PongActor`` reacts on the ``Ping`` event by emitting a ``Pong`` event. Other than in previous examples, the event is not emitted in the actor’s ``onCommand`` handler but in the ``onEvent`` handler. For that purpose, the actor has to mixin the ``PersistOnEvent`` trait and use the ``persistOnEvent`` method. The emitted ``Pong`` too isn’t consumed by its emitter but rather by the ``PingActor``, emitting another ``Ping``, and so on. The game ends when the ``PingActor`` received the 10th ``Pong``.

.. note::
   The ping-pong game is **reliable**. When an actor crashes and is re-started, the game is reliably resumed from where it was interrupted. The ``persistOnEvent`` method is idempotent i.e. no duplicates are written under failure conditions and later event replay. When deployed at different location, the ping-pong actors are also **partition-tolerant**. When their game is interrupted by a network partition, it is automatically resumed when the partition heals. 

   Furthermore, the actors don’t need to care about idempotency in their business logic i.e. they can assume to receive a **de-duplicated** and **causally-ordered** event stream in their ``onEvent`` handler. This is a significant advantage over at-least-once delivery based communication with ConfirmedDelivery_, for example, which can lead to duplicates and message re-ordering.

In a more real-world example, there would be several actors of different type collaborating to achieve a common goal, for example, in a distributed business process. These actors can be considered as event-driven and event-sourced *microservices*, collaborating on a causally ordered event stream in a reliable and partition-tolerant way. Furthermore, when partitioned, they remain available for local writes and automatically catch up with their collaborators when the partition heals.

.. hint::
   Further ``persistOnEvent`` details are described in the PersistOnEvent_ API docs.

.. _ZooKeeper: http://zookeeper.apache.org/
.. _event sourcing: http://martinfowler.com/eaaDev/EventSourcing.html
.. _vector clock update rules: http://en.wikipedia.org/wiki/Vector_clock
.. _version vector update rules: http://en.wikipedia.org/wiki/Version_vector
.. _Lamport timestamps: http://en.wikipedia.org/wiki/Lamport_timestamps
.. _multi node testkit: http://doc.akka.io/docs/akka/2.4/dev/multi-node-testing.html
.. _ReplicatedOrSetSpec: https://github.com/RBMHTechnology/eventuate/blob/master/src/multi-jvm/scala/com/rbmhtechnology/eventuate/crdt/ReplicatedORSetSpec.scala
.. _CRDT sources: https://github.com/RBMHTechnology/eventuate/tree/master/eventuate-crdt/src/main/scala/com/rbmhtechnology/eventuate/crdt
.. _A comprehensive study of Convergent and Commutative Replicated Data Types: http://hal.upmc.fr/file/index/docid/555588/filename/techreport.pdf

.. _Versioned: latest/api/index.html#com.rbmhtechnology.eventuate.Versioned
.. _ORSet: latest/api/index.html#com.rbmhtechnology.eventuate.crdt.ORSet
.. _ORSetService: latest/api/index.html#com.rbmhtechnology.eventuate.crdt.ORSetService
.. _CRDTService: latest/api/index.html#com.rbmhtechnology.eventuate.crdt.CRDTService
.. _CRDTServiceOps: latest/api/index.html#com.rbmhtechnology.eventuate.crdt.CRDTServiceOps
.. _ConfirmedDelivery: latest/api/index.html#com.rbmhtechnology.eventuate.ConfirmedDelivery
.. _PersistOnEvent: latest/api/index.html#com.rbmhtechnology.eventuate.PersistOnEvent

.. [#] ``EventsourcedActor``\ s and ``EventsourcedView``\ s that have an undefined ``aggregateId`` can consume events from all other actors on the same event log.
.. [#] Attached update timestamps are not version vectors because Eventuate uses `vector clock update rules`_ instead of `version vector update rules`_. Consequently, update timestamp equivalence cannot be used as criterion for replica convergence.
.. [#] A formal approach to automatically *merge* concurrent versions of application state are convergent replicated data types (CvRDTs) or state-based CRDTs.
.. [#] Distributed lock acquisition or leader election require an external coordination service like ZooKeeper_, for example, whereas static rules do not.
