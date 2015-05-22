Introduction
============

Gatling AMQP support

- CAUTION: This is not official library!
    - but using 'io.gatling.amqp' for FQCN to deal with 'private[gatling]', sorry.
- inspired by https://github.com/fhalim/gatling-rabbitmq (thanks!)


Usage
=====

- see example: src/test/scala/io/gatling/amqp/PublishingSimulation.scala

```
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    .auth("guest", "guest")
    .poolSize(10)

  val scn = scenario("RabbitMQ Publishing").repeat(1000) {
    exec(
      amqp("Publish")
        .publish("q1", payload = "{foo:1}")
    )
  }

  setUp(scn.inject(rampUsers(10) over (3 seconds))).protocols(amqpProtocol)
```

Run
===

```
% sbt
> test
```

Restrictions
============

- work in progress
    - currently only one action can be defined in action builder


Environment
===========

- gatling-sbt-2.1.6 (to implement easily)
- gatling-2.2.0-M3 (live with edge)


TODO
====

- declare exchanges, queues and bindings in protocol builder context
- declare exchanges, queues and bindings in action builder context (to test declaration costs)
- add 'confirm' action (it's a dialect of RabbitMQ)
    - ex) `exec(amqp.publish("q1", payload).confirm)`


