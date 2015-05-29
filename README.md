Introduction
============

Gatling AMQP support

- CAUTION: This is not official library!
    - but using 'io.gatling.amqp' for FQCN to deal with 'private[gatling]', sorry.
- inspired by https://github.com/fhalim/gatling-rabbitmq (thanks!)


Usage
=====

- publish (normal)


```
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    .auth("guest", "guest")
    .poolSize(10)

  val req = PublishRequest("q1", payload = "{foo:1}")

  val scn = scenario("AMQP Publish").repeat(1000) {
    exec(amqp("Publish").publish(req))
  }

  setUp(scn.inject(rampUsers(10) over (1 seconds))).protocols(amqpProtocol)
```

- publish (with persistent)
    - PublishRequest.persistent make request DeliveryMode(2)
    - known issue: persistent reset request's properties

```
  val req = PublishRequest("q1", payload = "{foo:1}").persistent
```

- publish (with confirmation)
    - set "confirmMode()" in protocol that invokes "channel.confirmSelect()"


```
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    .auth("guest", "guest")
    .poolSize(10)
    .confirmMode()

  val req = PublishRequest("q1", payload = "{foo:1}")

  val scn = scenario("AMQP Publish(ack)").repeat(1000) {
    exec(amqp("Publish").publish(req))
  }

  setUp(scn.inject(rampUsers(10) over (1 seconds))).protocols(amqpProtocol)
```

- full code: src/test/scala/io/gatling/amqp/PublishingSimulation.scala


Run
===

```
% sbt
> test
```

Restrictions
============

- work in progress
    - only one action can be defined in action builder
    - support only publish action (TODO: consume)


Environment
===========

- gatling-sbt-2.1.6 (to implement easily)
- gatling-2.2.0-M3 (live with edge)


TODO
====

- declare exchanges, queues and bindings in protocol builder context
- declare exchanges, queues and bindings in action builder context (to test declaration costs)
- make AmqpProtocol immutable
- make Builder mutable
- consume action
