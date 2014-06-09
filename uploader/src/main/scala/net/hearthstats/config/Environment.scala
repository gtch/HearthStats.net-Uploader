package net.hearthstats.config


/**
 * Represents the environment-specific information that varies between OS X and Windows.
 */
abstract class Environment {

  def os: OS

}
