package sttp.client4.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class WwwAuthHeaderParserTest extends AnyFlatSpec with Matchers {
  it should "parse header with only quoted values" in {
    WwwAuthHeaderParser
      .parse(
        "Digest realm=\"Digest WF Realm\", qop=\"auth\", nonce=\"MTU3MzQ5MTE3MjQ3NzphY2I5NDgxNjdmODdiZGIwMzU1YTk5OTIxNDU1MmY0ZQ==\""
      )
      .values shouldBe Map(
      "realm" -> "Digest WF Realm",
      "qop" -> "auth",
      "nonce" -> "MTU3MzQ5MTE3MjQ3NzphY2I5NDgxNjdmODdiZGIwMzU1YTk5OTIxNDU1MmY0ZQ=="
    )
  }

  it should "parse parse header with mixed qouted and unquoted values" in {
    WwwAuthHeaderParser
      .parse(
        "Digest realm=\"me@kennethreitz.com\", " +
          "nonce=\"399b4061bd576c9d9a22b698bd3f9367\", " +
          "qop=\"auth\", " +
          "opaque=\"47e2037ead3fd3dfe6260991da9e5db7\", " +
          "algorithm=MD5, " +
          "stale=FALSE"
      )
      .values shouldBe Map(
      "realm" -> "me@kennethreitz.com",
      "nonce" -> "399b4061bd576c9d9a22b698bd3f9367",
      "qop" -> "auth",
      "opaque" -> "47e2037ead3fd3dfe6260991da9e5db7",
      "algorithm" -> "MD5",
      "stale" -> "FALSE"
    )
  }

  it should "parse header without spaces" in {
    WwwAuthHeaderParser
      .parse(
        "Digest realm=\"Digest WF Realm\",qop=\"auth\",nonce=\"MTU3MzQ5MTE3MjQ3NzphY2I5NDgxNjdmODdiZGIwMzU1YTk5OTIxNDU1MmY0ZQ==\""
      )
      .values shouldBe Map(
      "realm" -> "Digest WF Realm",
      "qop" -> "auth",
      "nonce" -> "MTU3MzQ5MTE3MjQ3NzphY2I5NDgxNjdmODdiZGIwMzU1YTk5OTIxNDU1MmY0ZQ=="
    )
  }
}
