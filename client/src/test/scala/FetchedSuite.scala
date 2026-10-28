package com.alecdorrington.eunomia
package client

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.laminar.api.L.*
import munit.FunSuite
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.scalajs.js

class FetchedSuite extends FunSuite:

  private val original = js.Dynamic.global.fetch

  override def afterEach(context: AfterEach): Unit = js
    .Dynamic
    .global
    .updateDynamic("fetch")(original)

  /**
    * Stands in for the browser's `fetch`, recording each request, and gives the
    * functions answering them, in order.
    */
  private def stub(): (js.Array[js.Dynamic], js.Array[js.Dynamic]) =
    val sent      = js.Array[js.Dynamic]()
    val answerers = js.Array[js.Dynamic]()
    val fetch: js.Function2[String, js.Dynamic, js.Promise[js.Any]] =
      (_, init) =>
        sent.push(init)
        new js.Promise[js.Any]((resolve, _) =>
          answerers.push(resolve.asInstanceOf[js.Dynamic]),
        )
    js.Dynamic.global.updateDynamic("fetch")(fetch)
    (sent, answerers)

  private def reply(status: Int, text: String): js.Dynamic =
    val body: js.Function0[js.Promise[String]] =
      () => js.Promise.resolve[String](text)
    js.Dynamic.literal(status = status, text = body)

  test("A list is got with the session's cookie, as its status and text"):
    val (sent, answerers) = stub()
    val owner             = ManualOwner()
    val result            = Promise[(Int, String)]()
    Fetched("/api/list").addObserver(Observer.fromTry(
      result.tryComplete(_): Unit,
    ))(using owner)
    answerers(0)(reply(200, "[]"))
    result
      .future
      .map: answer =>
        owner.killSubscriptions()
        assertEquals(answer, 200 -> "[]")
        assertEquals(
          sent(0).credentials.asInstanceOf[String],
          "same-origin",
        )

  test("Stopping aborts the request"):
    val (sent, _) = stub()
    val owner     = ManualOwner()
    Fetched("/api/list").addObserver(Observer.empty)(using owner)
    owner.killSubscriptions()
    assert(sent(0).signal.aborted.asInstanceOf[Boolean])
