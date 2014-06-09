package net.hearthstats.win

import net.hearthstats.Main

/**
 * Main class for Windows application, starts up the HearthStats Uploader.
 */
class MainWin {

  def main(args: Array[String]) {

    val environment = new EnvironmentWin()

    Main.start(environment)

  }

}
