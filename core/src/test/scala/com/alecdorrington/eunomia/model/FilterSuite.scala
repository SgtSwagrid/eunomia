package com.alecdorrington.eunomia
package model

import io.circe.parser.decode
import io.circe.syntax.*
import munit.FunSuite

class FilterSuite extends FunSuite:

  private final case class Book(name: String, rating: Option[Long])

  private val name   = Field.of[Book]("name", _.name)
  private val rating = Field.of[Book]("rating", _.rating)

  private val every = (name.contains("x") && !(rating >= 50L)) ||
    rating.oneOf(1L, 2L) || rating.missing || name.is("y")

  private def leaves(filter: Filter): Int = filter.fold[Int](
    not = identity,
    all = _.sum,
    any = _.sum,
    compare = (_, _, _) => 1,
    contains = (_, _) => 1,
    oneOf = (_, _) => 1,
    missing = _ => 1,
  )

  test("a fold reaches every leaf once, and with its own cases gives it back"):
    assertEquals(leaves(every), 5)
    assertEquals(leaves(Filter.always), 0)
    assertEquals(leaves(Filter.never), 0)
    assertEquals(
      every.fold[Filter](
        not = Filter.Not(_),
        all = Filter.And(_),
        any = Filter.Or(_),
        compare = Filter.Compare(_, _, _),
        contains = Filter.Contains(_, _),
        oneOf = Filter.OneOf(_, _),
        missing = Filter.Missing(_),
      ),
      every,
    )

  test("every shape of filter survives the wire as small JSON objects"):
    assertEquals(
      (rating >= 50L).asJson.noSpaces,
      """{"field":"rating","is":">=","value":50}""",
    )
    val filter = (name.contains("x") && !(rating >= 50L)) ||
      rating.oneOf(1L, 2L) || rating.missing || Filter.Compare(
        "score",
        Operator.Unequal,
        Value.Real(2.5),
      ) || Filter.Compare(
        "inPrint",
        Operator.Equal,
        Value.Flag(true),
      )
    assertEquals(
      decode[Filter](filter.asJson.noSpaces),
      Right(filter),
    )
    assert(decode[Filter]("""{"xor":[]}""").isLeft)
    assert(decode[Filter]("""{"field":"rating","is":"~","value":1}""").isLeft)

  private final case class Novel(title: String, author: String)

  private val title  = Field.of[Novel]("title", _.title)
  private val author = Field.of[Novel]("author", _.author)

  test("a search seeks every word in any of its fields, in any order"):
    def sought(word: String) = Filter.Or(List(
      Filter.Contains("title", word),
      Filter.Contains("author", word),
    ))
    assertEquals(
      Filter.search(" Brontë  wuthering ", title, author),
      Filter.And(List(sought("Brontë"), sought("wuthering"))),
    )
    val found = ListSchema(title, author).matches(
      Filter.search("emily HEIGHTS", title, author),
    )
    assert(found(Novel("Wuthering Heights", "Emily Brontë")))
    assert(!found(Novel("Jane Eyre", "Charlotte Brontë")))

  test("a search parts words at any space, and without any holds everywhere"):
    assertEquals(
      Filter.search(" \t\n ", title, author),
      Filter.always,
    )
    assertEquals(
      Filter.search("a\u3000b\u00a0a", title),
      Filter.search("a b", title),
    )
