package io.gatling.amqp.check

import io.gatling.core.check.extractor.xpath.{ JdkXPathExtractorFactory, SaxonXPathExtractorFactory }
import io.gatling.core.session.Expression

trait AmqpCheckSupport {

//  def simpleCheck = AmqpSimpleCheck

//  def xpath(expression: Expression[String], namespaces: List[(String, String)] = Nil)(implicit saxonXPathExtractorFactory: SaxonXPathExtractorFactory, jdkXPathExtractorFactory: JdkXPathExtractorFactory) =
//    AmqpXPathCheckBuilder.xpath(expression, namespaces)
}
