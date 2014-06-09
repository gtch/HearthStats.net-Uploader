package net.hearthstats.win

import net.hearthstats.config.{OS, Environment}
import net.hearthstats.notification.{DialogNotificationQueue, NotificationQueue}
import java.io.File

/**
 * Windows environment.
 */
class EnvironmentWin extends Environment {
  override def os: OS = OS.WINDOWS

  // Windows only supports HearthStats notifications
  override def newNotificationQueue: NotificationQueue = new DialogNotificationQueue


  override def extractionFolder =  {
    var path = "tmp"
    (new File(path)).mkdirs
    path
  }
}
