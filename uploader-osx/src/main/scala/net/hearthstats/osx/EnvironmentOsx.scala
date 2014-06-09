package net.hearthstats.osx

import net.hearthstats.config.{OS, Environment}

/**
 * Windows environment.
 */
class EnvironmentOsx extends Environment {
  override def os: OS = OS.OSX
}
