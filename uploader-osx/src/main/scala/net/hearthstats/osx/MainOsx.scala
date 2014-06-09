package net.hearthstats.osx

import net.hearthstats.Main

/**
 * Created by charlie on 9/06/2014.
 */
class MainOsx {

  def main(args: Array[String]) {

    val environment = new EnvironmentOsx()

    Main.start(environment)

  }

}
