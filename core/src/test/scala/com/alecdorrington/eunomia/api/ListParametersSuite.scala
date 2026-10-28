package com.alecdorrington.eunomia
package api

import com.alecdorrington.eunomia.model.*
import munit.FunSuite

class ListParametersSuite extends FunSuite:

  private final case class Book(name: String, rating: Option[Long])

  private val name   = Field.of[Book]("name", _.name)
  private val rating = Field.of[Book]("rating", _.rating)

  test("a query is written as parameters, omitting defaults, and read back"):
    val query = ListQuery(
      name.contains("x") && rating >= 50L,
      List(rating.descending, name.ascending),
      Some(Page(20, 10)),
    )
    assertEquals(
      ListParameters.of(query.copy(filter = rating >= 50L)),
      List(
        "filter" -> """{"field":"rating","is":">=","value":50}""",
        "order"  -> "-rating,name",
        "offset" -> "20",
        "limit"  -> "10",
      ),
    )
    assertEquals(
      ListParameters.parse(ListParameters.of(query).toMap.get),
      Right(query),
    )
    assertEquals(
      ListParameters.of(ListQuery()),
      List.empty,
    )
    assertEquals(
      ListParameters.parse(_ => None),
      Right(ListQuery()),
    )

  test("an offset without a limit asks for the whole list"):
    assertEquals(
      ListParameters.parse(Map("offset" -> "20").get).map(_.page),
      Right(None),
    )
    assertEquals(
      ListParameters.parse(Map("limit" -> "10").get).map(_.page),
      Right(Some(Page(0, 10))),
    )

  test("parameters that describe no query are refused"):
    assert(ListParameters.parse(Map("filter" -> "{").get).isLeft)
    assert(ListParameters.parse(Map("limit" -> "ten").get).isLeft)
    assert(ListParameters.parse(Map("offset" -> "1.5").get).isLeft)
