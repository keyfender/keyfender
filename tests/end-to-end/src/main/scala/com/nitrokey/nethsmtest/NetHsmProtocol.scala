package com.nitrokey.nethsmtest

import spray.json.DefaultJsonProtocol
import spray.json.JsonFormat
import spray.json.RootJsonFormat
import spray.json.JsString
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsValue
import spray.json.JsArray
import spray.json.deserializationError
import Base64._
import java.math.BigInteger
import java.security.spec.RSAPublicKeySpec
import java.security.PublicKey
import java.security.KeyFactory
//import Crypto.dropLeadingZero

/**
 * The JSON classes we use with the API.
 * Notice that we can use the same object and the same JSON mapping as we defined in the API.
 */
object NetHsmProtocol extends DefaultJsonProtocol {

  implicit val encoding = base64Url.copy(strictPadding=true)

/*TODO: Get this generic conversion working and replace the objects below with a single-line jsonFormatX.
     implicit object SeqByte extends JsonFormat[Seq[Byte]] {
      def write(c: Seq[Byte]) = JsString(c.toBase64)

      def read(value: JsValue) = value match {
        case JsString(number) => number.getBytes.toArray
        case _ => deserializationError("Seq[Byte] expected")
      }
    } */

  case class JsendResponse(status: String, data: JsValue)
  implicit val JsendResponseFormat = jsonFormat2(JsendResponse)

  case class NkPublicRsaKey(modulus: Seq[Byte], publicExponent: Seq[Byte]) {
    def javaPublicKey: PublicKey = {
      val publicKeySpec = new RSAPublicKeySpec(new BigInteger(1, modulus.toArray), new BigInteger(1, publicExponent.toArray))
      val kf = KeyFactory.getInstance("RSA")
      kf.generatePublic(publicKeySpec)
    }
  }

  /**
   * This object converts between NkPublicRsaKey and JSON by using Base64.
   */
  implicit object NkPublicRsaKey extends RootJsonFormat[NkPublicRsaKey] {
    def write(x: NkPublicRsaKey) = JsObject(
      "modulus" -> JsString(x.modulus.toBase64),
      "publicExponent" -> JsString(x.publicExponent.toBase64)
    )

    def read(value: JsValue) = {
      value.asJsObject.getFields("modulus", "publicExponent") match {
        case Seq(JsString(modulus), JsString(publicExponent)) =>
          new NkPublicRsaKey(Crypto.dropLeadingZero(modulus.toByteArray), Crypto.dropLeadingZero(publicExponent.toByteArray))
        case _ => deserializationError("PublicRSAKey expected")
      }
    }
  }

  case class NkPrivateRsaKey(primeP: Seq[Byte], primeQ: Seq[Byte], publicExponent: Seq[Byte])
  /**
   * This object converts between NkPrivateRsaKey and JSON by using Base64.
   */
  implicit object NkPrivateRsaKey extends RootJsonFormat[NkPrivateRsaKey] {
    def write(x: NkPrivateRsaKey) = JsObject(
      "primeP" -> JsString(x.primeP.toBase64),
      "primeQ" -> JsString(x.primeQ.toBase64),
      "publicExponent" -> JsString(x.publicExponent.toBase64)
    )

    def read(value: JsValue) = {
      value.asJsObject.getFields("primeP", "primeQ", "publicExponent") match {
        case Seq(JsString(primeP), JsString(primeQ), JsString(publicExponent)) =>
          new NkPrivateRsaKey(primeP.toByteArray, primeQ.toByteArray, publicExponent.toByteArray)
        case _ => deserializationError("PrivateRSAKey expected")
      }
    }
  }

  case class NkPublicKey(id: String, purpose: String, algorithm: String, publicKey: NkPublicRsaKey)
  implicit val PublicKeyFormat = jsonFormat4(NkPublicKey)

  case class NkPublicKeyResponse(status: String, data: NkPublicKey)
  implicit val PublicKeyResponseFormat = jsonFormat2(NkPublicKeyResponse)

  case class PublicKeyLocation(location: String)
  implicit val PublicKeyLocationFormat = jsonFormat1(PublicKeyLocation)

  case class PublicKeyListResponse(status: String, data: List[PublicKeyLocation])
  implicit val PublicKeyListResponseFormat = jsonFormat2(PublicKeyListResponse)

  case class PasswordChange(newPassword: String)
  implicit val PasswordChangeFormat = jsonFormat1(PasswordChange)

  case class SimpleResponse(status: String)
  implicit val SystemStatusFormat = jsonFormat1(SimpleResponse)

  case class SystemInformationResponse(vendor: String, product: String, version: String)
  implicit val SystemInformationResponseFormat = jsonFormat3(SystemInformationResponse)

  case class DecryptRequest(encrypted: Seq[Byte])
  implicit object DecryptRequest extends RootJsonFormat[DecryptRequest] {
    def write(x: DecryptRequest) = JsObject(
      "encrypted" -> JsString(x.encrypted.toBase64)
    )
    def read(value: JsValue) = {
      value.asJsObject.getFields("encrypted") match {
        case Seq(JsString(encrypted)) =>
          new DecryptRequest(encrypted.toByteArray)
        case _ => deserializationError("DecryptRequest expected")
      }
    }
  }

  case class Decrypted(decrypted: Seq[Byte])
  implicit object Decrypted extends RootJsonFormat[Decrypted] {
    def write(x: Decrypted) = JsObject(
      "decrypted" -> JsString(x.decrypted.toBase64)
    )
    def read(value: JsValue) = {
      value.asJsObject.getFields("decrypted") match {
        case Seq(JsString(decrypted)) =>
          new Decrypted(decrypted.toByteArray)
        case _ => deserializationError("Decrypted expected")
      }
    }
  }

  case class DecryptResponse(status: String, data: Decrypted)
  implicit val DecryptResponseFormat = jsonFormat2(DecryptResponse)

  case class SignRequest(message: Seq[Byte])
  implicit object SignRequest extends RootJsonFormat[SignRequest] {
    def write(x: SignRequest) = JsObject(
      "message" -> JsString(x.message.toBase64)
    )
    def read(value: JsValue) = {
      value.asJsObject.getFields("message") match {
        case Seq(JsString(message)) =>
          new SignRequest(message.toByteArray)
        case _ => deserializationError("SignRequest expected")
      }
    }
  }

  case class Signed(signedMessage: Seq[Byte])
  implicit object Signed extends RootJsonFormat[Signed] {
    def write(x: Signed) = JsObject(
      "signedMessage" -> JsString(x.signedMessage.toBase64)
    )
    def read(value: JsValue) = {
      value.asJsObject.getFields("signedMessage") match {
        case Seq(JsString(signedMessage)) =>
          new Signed(signedMessage.toByteArray)
        case _ => deserializationError("Signed expected")
      }
    }
  }
  case class SignResponse(status: String, data: Signed)
  implicit val SignResponseFormat = jsonFormat2(SignResponse)

  case class KeyImport(purpose: String, algorithm: String, privateKey: NkPrivateRsaKey)
  implicit val KeyImportFormat = jsonFormat3(KeyImport)

  case class KeyImportWithId(purpose: String, algorithm: String, privateKey: NkPrivateRsaKey, id: String)
  implicit val KeyImportWithIdFormat = jsonFormat4(KeyImportWithId)

  case class KeyGeneration(purpose: String, algorithm: String, length: Int)
  implicit val keyGenerationFormat = jsonFormat3(KeyGeneration)

  case class KeyGenerationWithId(purpose: String, algorithm: String, length: Int, id: String)
  implicit val keyGenerationWithIdFormat = jsonFormat4(KeyGenerationWithId)

}
