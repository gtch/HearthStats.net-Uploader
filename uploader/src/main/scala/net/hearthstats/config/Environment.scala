package net.hearthstats.config

import net.hearthstats.notification.NotificationQueue
import grizzled.slf4j.Logging


/**
 * Represents the environment-specific information that varies between OS X and Windows.
 */
abstract class Environment {

  def os: OS

  /**
   * The location where temporary files can be extracted.
   */
  def extractionFolder: String

  /**
   * Creates a new NotificationQueue object that is suitable for the current environment.
   */
  def newNotificationQueue: NotificationQueue

}


object Environment extends Logging {

  /**
   * Looks up a system property, but without throwing an exception if the property does not exist.
   *
   * @param property the name for a standard system property
   * @return the requested property, or blank if the property was not set
   */
  def systemProperty(property: String): String = {
    try {
      System.getProperty(property)
    }
    catch {
      case ex: SecurityException => {
        warn("Caught a SecurityException reading the system property '" + property + "', defaulting to blank string.")
        ""
      }
    }
  }


}