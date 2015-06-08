[![Build Status](https://travis-ci.org/maiha/gatling-amqp.svg?branch=master)](https://travis-ci.org/maiha/gatling-amqp)

Introduction
============

Gatling AMQP support

- CAUTION: This is not official library!
    - but using 'io.gatling.amqp' for FQCN to deal with 'private[gatling]', sorry.
- inspired by https://github.com/fhalim/gatling-rabbitmq (thanks!)


Usage
=====

## publish (normal)


```
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    // .vhost("/")
    .auth("guest", "guest")
    .poolSize(10)

  val req = PublishRequest("q1", payload = "{foo:1}")

  val scn = scenario("AMQP Publish").repeat(1000) {
    exec(amqp("Publish").publish(req))
  }

  setUp(scn.inject(rampUsers(10) over (1 seconds))).protocols(amqpProtocol)
```

## publish (with persistent)

- PublishRequest.persistent make request DeliveryMode(2)
- known issue: persistent reset request's properties

```
  val req = PublishRequest("q1", payload = "{foo:1}").persistent
```

## publish (with confirmation)

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

## declare queues

```
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    .auth("guest", "guest")
    .declare(queue("q1", durable = true, autoDelete = false))
```

## declare exchange and binding


```
  val x = exchange("color", "direct", autoDelete = false)
  val q = queue("orange")
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("localhost")
    .port(5672)
    .auth("guest", "guest")
    .declare(x)
    .declare(q)
    .bind(x, q, routingKey = "orange")
```

- full code: src/test/scala/io/gatling/amqp/PublishingSimulation.scala

## consume (auto acked)


```
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("amqp")
    .port(5672)
    .auth("guest", "guest")

  val scn = scenario("AMQP Publish(ack)").exec {
    amqp("Consume").consume("q1", autoAck = true)
  }

  setUp(scn.inject(atOnceUsers(1))).protocols(amqpProtocol)
```

- full code: src/test/scala/io/gatling/amqp/ConsumingSimulation.scala

## consume (manual acked)

- not implemented yet


Run
===

## sbt directly

```
% sbt
> testOnly io.gatling.amqp.PublishingSimulation

% sbt
> testOnly io.gatling.amqp.ConsumingSimulation
```

- try `sbt -J-Xmx8192m -J-XX:MaxPermSize=256m` for publishing massive messages

## shell script to store gatling stdout logs and simulation sources

```
% ./run p foo
```

- stored in "stats/p/foo"


Benchmark
=========

### environments

#### server
- cpu: Xeon X5687(3.60GHz)
- mem: 24GB, limit(10GiB), watermark(5GiB)

#### rabbitmq
- version: 3.5.2
- total bytes = `servers * payalod * messages * users`
- users = concurrency of AMQP connections

## publish (persistent queue)

| servers | payload | ack | repeat | users | total | sec | qps  | spd       |
|--------:|--------:|:---:|-------:|------:|------:|----:|-----:|----------:|
| 1       |   1 KB  |  o  |100,000 |    10 | 1 GB  |  69 | 14326| 14.3 MB/s |
| 1       |  10 KB  |  o  | 10,000 |    10 | 1 GB  |  14 |  6778| 67.8 MB/s |
| 1       | 100 KB  |  o  |  1,000 |    10 | 1 GB  |  11 |   881| 88.1 MB/s |
| 1       |   1 MB  |  o  |    100 |    10 | 1 GB  |  10 |    97| 97.8 MB/s |
| 1       |  10 MB  |  o  |    100 |     1 | 1 GB  |  -  |   -  | log error |
| 1       |  10 KB  |  o  |  1,000 |   100 | 1 GB  |  17 |  5791| 57.9 MB/s |
| 4       |   1 KB  |  o  |100,000 |    10 | 4 GB  | 298 | 13490| 13.5 MB/s |
| 4       |  10 KB  |  o  | 10,000 |    10 | 4 GB  |  56 |  7208| 72.1 MB/s |

- log error: statsEngine stopped before working actors finished

## publish (persistent queue, paging)

| servers | payload | ack | repeat | users | total | sec | qps   | spd       |
|--------:|--------:|:---:|-------:|------:|------:|----:|------:|----------:|
| 1       | 10 KB   |  o  | 100,000|    10 | 10 GB | 143 |  6983 | 69.8 MB/s |
| 1       | 10 KB   |  o  | 200,000|    10 | 20 GB | 301 |  6632 | 66.3 MB/s |

## consume (persistent queue)

| payload | message| users | total | sec | qps   | spd       |
|--------:|-------:|------:|------:|----:|------:|----------:|
| 10 KB   | 100 k  |     1 |  1 GB |  12 |  8436 | 84.4 MB/s |
| 10 KB   |   2 m  |     1 | 20 GB | 179 | 11161 |111.6 MB/s |

## publish and consume (persistent queue)

| payload |p-ack| repeat |publisher| total | qps  | spd       | consumer| ack | qps |
|--------:|:---:|-------:|--------:|------:|-----:|----------:|--------:|:---:|----:|
|  10 KB  |  o  | 10,000 |    10   |  1 GB | 6779 | 67.8 MB/s |     1   |auto |6233 |
|  10 KB  |  o  |200,000 |    10   | 20 GB | 7639 | 76.4 MB/s |     1   |auto |7622 |

Library
=======

- amqp-client-3.5.3
- gatling-sbt-2.1.6 (to implement easily)
- gatling-2.2.0-M3 (live with edge)


TODO
====

- declare exchanges, queues and bindings in action builder context (to test declaration costs)
- make AmqpProtocol immutable
- make Builder mutable
- consume action (manual ack)

