package com.alecdorrington.eunomia
package client

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Gets an address when started, emitting the reply's status and body, or an
  * error if the reply never comes, and aborting the request once no one is
  * listening.
  */
private[client] object Fetched:

  def apply(url: String): EventStream[(Int, String)] =
    var inFlight = Option.empty[dom.AbortController]
    EventStream.fromCustomSource[(Int, String)](
      start = (fire, fail, _, _) =>
        val controller = new dom.AbortController
        inFlight = Some(controller)
        val init = new dom.RequestInit:
          credentials = dom.RequestCredentials.`same-origin`
          signal = controller.signal
        val reply = dom
          .fetch(url, init)
          .toFuture
          .flatMap(response =>
            response.text().toFuture.map(response.status -> _),
          )
        // Only into the run that sent it: a stream stopped and started again
        // has a controller of its run's own.
        def current = inFlight.contains(controller)
        reply.foreach(answer => if current then fire(answer))
        reply
          .failed
          .foreach(error =>
            if current && !controller.signal.aborted then fail(error),
          )
      ,
      stop = _ =>
        inFlight.foreach(_.abort())
        inFlight = None,
    )
