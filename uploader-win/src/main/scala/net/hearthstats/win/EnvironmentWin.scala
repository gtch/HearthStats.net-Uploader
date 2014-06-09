package net.hearthstats.win

import net.hearthstats.config.{OS, Environment}

/**
 * Windows environment.
 */
class EnvironmentWin extends Environment {
  override def os: OS = OS.WINDOWS
}
