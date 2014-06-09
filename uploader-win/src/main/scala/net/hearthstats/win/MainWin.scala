package net.hearthstats.win

import net.hearthstats.Main

/**
 * Main object for the Windows application, starts up the HearthStats Uploader.
 */
object MainWin {

  def main(args: Array[String]) {

    val environment = new EnvironmentWin()

    Main.start(environment)

  }

}
