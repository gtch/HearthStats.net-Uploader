package net.hearthstats.config

/**
 * Represents the operating systems supported by the HearthStats Uploader.
 */
object OperatingSystem extends Enumeration {
  type OperatingSystem = Value

  val WINDOWS, OSX, UNSUPPORTED = Value
}
