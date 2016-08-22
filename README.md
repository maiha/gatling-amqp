[![Build Status](https://travis-ci.org/maiha/gatling-amqp.svg?branch=master)](https://travis-ci.org/maiha/gatling-amqp)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/sc.ala/gatling-amqp_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/sc.ala/gatling-amqp_2.11)

Introduction
============

Gatling AMQP support

- CAUTION: This is not official library!
    - but using 'io.gatling.amqp' for FQCN to deal with 'private[gatling]', sorry.
- inspired by https://github.com/fhalim/gatling-rabbitmq (thanks!)


Usage
=====

## handy cli (use AmqpProtocol as console utility) [0.6 feature]


```
scala> import io.gatling.amqp.Predef._
scala> amqp.declare(queue("q3", durable = true)).run
```


## publish (normal)

Publish is asynchronous step and does not block scenario until actual
message is published. It can do publish with confirm mode turned on or
off. It changes way of getting publish seq number and way of acknowledge
from amqp server is done (sync or async).

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

Consume is asynchronous operation which will start consumer on given
queue, which will consume all messages which gets to the queue. It does
not block scenario (it is async).


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

## consumeSingle

Consume is blocking operation which will start consumer on given
queue and wait until exactly one message is consumed. Message can be
saved in session if needed and used later. Do not forget to drop it from
session if no longer needed.

- implementation is bit broken and unreliable when run more than few
users in parallel


```
  implicit val amqpProtocol: AmqpProtocol = amqp
    .host("amqp")
    .port(5672)
    .auth("guest", "guest")

  val scn = scenario("AMQP Publish(ack)").exec {
    amqp("Consume")
      .consumeSingle("q1", saveResultToSession = true)
      .exec(session => {
        val msg = session(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY).asOption[DeliveredMsg]
        println("Response=" + msg)
        println("ResponseBody=" + msg.map(m => new String(m.getBody, "UTF-8")))
        // drop possibly large response from session
        session.set(AmqpConsumer.LAST_CONSUMED_MESSAGE_KEY, null)
      })
  }

  setUp(scn.inject(atOnceUsers(1))).protocols(amqpProtocol)
```

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

- amqp-client-3.6.5 (3.5.1, 3.5.3, 3.5.7 and 3.6.1 is also known to be working)
- gatling-2.2.2


TODO
====

- declare exchanges, queues and bindings in action builder context (to test declaration costs)
- make AmqpProtocol immutable
- make Builder mutable
- mandatory
- consume action (manual ack)
- consume followed by publish and than pause will cause to report publish times containing also pause time (RpcSimmulation with pause after each publish shows it) 

License
=======

released under the [MIT License](http://www.opensource.org/licenses/MIT).
