package org.jfrog.build.sbtplugin

import org.jfrog.build.api.util.{Log=> JLog}

class LogAdapter(log: sbt.Logger) extends JLog {
	override def debug(message: String): Unit = log.debug(message)
	override def info(message: String): Unit = log.info(message)
	override def warn(message: String): Unit = log.warn(message)
	override def error(message: String): Unit = log.error(message)
	override def error(message: String, t: Throwable): Unit = {
		log.error(message)
		log.trace(t)
	}
}