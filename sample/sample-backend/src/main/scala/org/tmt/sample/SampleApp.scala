package org.tmt.sample

import caseapp.core.RemainingArgs
import csw.location.api.models.Metadata
import csw.network.utils.SocketUtils
import esw.http.template.wiring.ServerApp
import SampleAppCommand.StartCommand
import org.tmt.sample.impl.SampleWiring

object SampleApp extends ServerApp[SampleAppCommand] {
  override def appName: String    = getClass.getSimpleName.dropRight(1)

  override def run(command: SampleAppCommand, remainingArgs: RemainingArgs): Unit =
    command match {
      case StartCommand(port) =>
        val wiring = new SampleWiring(Some(port.getOrElse(SocketUtils.getFreePort)))
        start(wiring, Metadata.empty)
    }
}
