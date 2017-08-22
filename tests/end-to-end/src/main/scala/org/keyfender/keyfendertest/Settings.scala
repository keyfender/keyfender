package org.keyfender.keyfendertest

import com.typesafe.config._
import collection.JavaConversions._

/**
 * Settings holds the values retrieved from the configuration file(s). 
 */
class Settings(var config: Config) {
  // non-lazy fields, we want all exceptions at construct time
  // Get values for keyfender service:
  val host = config.getString("keyfender.host")
  val port = config.getInt("keyfender.port")
  val prefix = config.getString("keyfender.prefix")
  val tls = config.getBoolean("keyfender.tls")
  println("Settings loaded.")

  def fullHost: String = {
    tls match {
      case true => "https://" + host + ":" + port
      case false => "http://" + host + ":" + port
    }
  }

}