package com.ovoenergy.comms.aws
package common

import org.scalatest.{Matchers, WordSpec}

abstract class IntegrationSpec extends WordSpec with Matchers with IOFutures {
  sys.props.put("log4j.configurationFile", "log4j2-it.xml")
}

