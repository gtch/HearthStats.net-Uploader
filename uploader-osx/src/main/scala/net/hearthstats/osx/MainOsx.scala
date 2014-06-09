package net.hearthstats.osx

import net.hearthstats.Main

/**
 * Main object for the OS X bundle, starts up the HearthStats Uploader.
 */
object MainOsx {

  def main(args: Array[String]) {

    val environment = new EnvironmentOsx()

    Main.start(environment)

  }

}
