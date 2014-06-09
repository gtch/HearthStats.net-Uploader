package net.hearthstats.win

import net.hearthstats.config.{OperatingSystem, Environment}
import net.hearthstats.config.OperatingSystem.OperatingSystem

/**
 * Windows environment.
 */
class EnvironmentWin extends Environment {
  override def os: OperatingSystem = OperatingSystem.WINDOWS
}
