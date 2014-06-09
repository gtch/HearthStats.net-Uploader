package net.hearthstats.osx

import net.hearthstats.Main
import com.apple.eawt.{AboutHandler, Application}
import com.apple.eawt.AppEvent.AboutEvent

/**
 * Main object for the OS X bundle, starts up the HearthStats Uploader.
 */
object HearthStatsOsx {

  def main(args: Array[String]) {

    val environment = new EnvironmentOsx()

    val application = Application.getApplication()
    application.setAboutHandler(null);

    Main.start(environment)

  }

}
