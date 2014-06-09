package net.hearthstats.osx

import net.hearthstats.config.{OS, Environment}
import net.hearthstats.notification.{DialogNotificationQueue, NotificationQueue}
import net.hearthstats.ConfigDeprecated
import java.io.File

/**
 * Windows environment.
 */
class EnvironmentOsx extends Environment {
  override def os: OS = OS.OSX

  // OS X may use the HearthStats notifications or inbuild OS X notifications
  override def newNotificationQueue: NotificationQueue = {
    if (ConfigDeprecated.useOsxNotifications)
      new OsxNotificationQueue
    else
      new DialogNotificationQueue
  }


  def extractionFolder = {
    val libFolder = new File(Environment.systemProperty("user.home") + "/Library/Application Support/HearthStatsUploader")
    libFolder.mkdir
    libFolder.getAbsolutePath
  }
}
