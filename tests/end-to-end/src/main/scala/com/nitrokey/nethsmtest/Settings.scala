package com.nitrokey.nethsmtest

import com.typesafe.config._
import collection.JavaConversions._

/**
 * Settings holds the values retrieved from the configuration file(s). 
 */
class Settings(var config: Config) {
  // non-lazy fields, we want all exceptions at construct time
  // Get values for nethsm service:
  val host = config.getString("nethsm.host")
  val port = config.getInt("nethsm.port")
  val prefix = config.getString("nethsm.prefix")
  val tls = config.getBoolean("nethsm.tls")
  println("Settings loaded.")
}