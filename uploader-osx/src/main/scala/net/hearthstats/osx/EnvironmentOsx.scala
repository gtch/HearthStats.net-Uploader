package net.hearthstats.osx

import net.hearthstats.config.{OperatingSystem, Environment}
import net.hearthstats.config.OperatingSystem._

/**
 * Created by charlie on 9/06/2014.
 */
class EnvironmentOsx extends Environment {
  override def os: OperatingSystem = OperatingSystem.OSX
}
