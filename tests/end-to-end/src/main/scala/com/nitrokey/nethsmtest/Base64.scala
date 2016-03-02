package com.nitrokey.nethsmtest

import scala.collection.immutable.HashMap
/**
 * Base64 encoder
 * @author Mark Lister
 *         This software is distributed under the 2-Clause BSD license. See the
 *         LICENSE file in the root of the repository.
 *
 *         Copyright (c) 2014 - 2015 Mark Lister
 *
 *         The repo for this Base64 encoder lives at  https://github.com/marklister/base64
 *         Please send your issues, suggestions and pull requests there.
 *
 */

object Base64 {
  private[this] val zero = Array(0, 0).map(_.toByte)

  case class B64Scheme(encodeTable: IndexedSeq[Char], strictPadding: Boolean = true) {
    lazy val decodeTable = HashMap(encodeTable.zipWithIndex: _ *)
  }

  lazy val base64 = new B64Scheme(('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9') ++ Seq('+', '/'))
  lazy val base64Url = new B64Scheme(base64.encodeTable.dropRight(2) ++ Seq('-', '_'), false)

  implicit class SeqEncoder(s: Seq[Byte]) {

    lazy val pad = (3 - s.length % 3) % 3

    def toBase64(implicit scheme: B64Scheme = base64): String = {
      def sixBits(x: Seq[Byte]): Array[Int] = {
        val a = (x(0) & 0xfc) >> 2
        val b = ((x(0) & 0x3) << 4) | ((x(1) & 0xf0) >> 4)
        val c = ((x(1) & 0xf) << 2) | ((x(2) & 0xc0) >> 6)
        val d = (x(2)) & 0x3f
        Array(a, b, c, d)
      }
      ((s ++ zero.take(pad)).grouped(3)
        .flatMap(sixBits)
        .map(scheme.encodeTable)
        .toArray
        .dropRight(pad) :+ "=" * pad)
        .mkString
    }
  }

  implicit class Encoder(b:Array[Byte]) {
    lazy val encoder = new SeqEncoder(b)
    def toBase64 (implicit scheme: B64Scheme = base64) = encoder.toBase64(scheme)
  }

  implicit class Decoder(s: String) {
    lazy val cleanS = s.reverse.dropWhile(_ == '=').reverse
    lazy val pad = s.length - cleanS.length
    lazy val computedPad = (4 - (cleanS.length % 4)) % 4

    def toByteArray(implicit scheme: B64Scheme = base64): Array[Byte] = {
      def threeBytes(s: String): Array[Byte] = {
        val r = s.map(scheme.decodeTable(_)).foldLeft(0)((a, b) => (a << 6) | b)
        Array((r >> 16).toByte, (r >> 8).toByte, r.toByte)
      }
      if (scheme.strictPadding) {
        if (pad > 2) throw new java.lang.IllegalArgumentException("Invalid Base64 String: (excessive padding) " + s)
        if (s.length % 4 != 0) throw new java.lang.IllegalArgumentException("Invalid Base64 String: (padding problem) " + s)
      }
      if (computedPad == 3) throw new java.lang.IllegalArgumentException("Invalid Base64 String: (string length) " + s)
      try {
        (cleanS + "A" * computedPad)
          .grouped(4)
          .map(threeBytes)
          .flatten
          .toArray
          .dropRight(computedPad)
      } catch {
        case e: NoSuchElementException => throw new java.lang.IllegalArgumentException("Invalid Base64 String: (invalid character)" + e.getMessage +s)
      }
    }
  }

}