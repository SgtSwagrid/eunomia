package com.alecdorrington.eunomia
package model

import com.alecdorrington.eunomia.model.Filter.*
import com.alecdorrington.eunomia.model.Operator.*
import munit.FunSuite

class CellFilterSuite extends FunSuite:

  private def text(input: String) = CellFilter.parse("name", Kind.Text, input)

  private def integer(input: String) =
    CellFilter.parse("rating", Kind.Integer, input)
  private def real(input: String) = CellFilter.parse("score", Kind.Real, input)

  private def flag(input: String) =
    CellFilter.parse("inPrint", Kind.Flag, input)

  test("a blank cell matches everything, and absence is written for any kind"):
    assertEquals(text(""), Right(Filter.always))
    assertEquals(integer("  | "), Right(Filter.always))
    assertEquals(integer("?"), Right(Missing("rating")))
    assertEquals(
      text("!?"),
      Right(Not(Missing("name"))),
    )

  test("text is sought by substring, excluded or matched exactly, or any"):
    List(
      "my book"      -> Contains("name", "my book"),
      "!edition"     -> Not(Contains("name", "edition")),
      "=Alpha"       -> Compare("name", Equal, Value.Text("Alpha")),
      "!=Alpha"      -> Compare("name", Unequal, Value.Text("Alpha")),
      "alpha | beta" -> Or(List(
        Contains("name", "alpha"),
        Contains("name", "beta"),
      )),
    ).foreach((input, filter) =>
      assertEquals(text(input), Right(filter), input),
    )

  test("a number is matched exactly, by an operator, in a range or by several"):
    List(
      "50"       -> Compare("rating", Equal, Value.Integer(50)),
      "-5"       -> Compare("rating", Equal, Value.Integer(-5)),
      ">=50 <80" -> And(List(
        Compare("rating", AtLeast, Value.Integer(50)),
        Compare("rating", Less, Value.Integer(80)),
      )),
      // A range is inclusive at both ends.
      "50..80" -> And(List(
        Compare("rating", AtLeast, Value.Integer(50)),
        Compare("rating", AtMost, Value.Integer(80)),
      )),
      // An integer too large for a double is read exactly.
      "9007199254740993" -> Compare(
        "rating",
        Equal,
        Value.Integer(9007199254740993L),
      ),
      ">=9007199254740993" -> Compare(
        "rating",
        AtLeast,
        Value.Integer(9007199254740993L),
      ),
    ).foreach((input, filter) =>
      assertEquals(integer(input), Right(filter), input),
    )
    // Each operator is read by its longest symbol.
    Operator
      .values
      .foreach(operator =>
        assertEquals(
          integer(s"${ operator.symbol }50"),
          Right(Compare("rating", operator, Value.Integer(50))),
        ),
      )
    assertEquals(
      real("50.5"),
      Right(Compare("score", Equal, Value.Real(50.5))),
    )

  test("a backward or open range, or a value of another kind, is refused"):
    List(
      integer("80..50"),
      real("1...2"),
      integer("..80"),
      integer("50 .. 80"),
      integer("50.5"),
      integer("abc"),
      flag("maybe"),
    ).foreach(parsed => assert(parsed.isLeft, parsed))

  test("truth values are read in several spellings"):
    assertEquals(
      flag("Yes"),
      Right(Compare("inPrint", Equal, Value.Flag(true))),
    )
    assertEquals(
      flag("0"),
      Right(Compare("inPrint", Equal, Value.Flag(false))),
    )
