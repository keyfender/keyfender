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

  case class NkPublicRsaKey(modulus: Seq[Byte], publicExponent: Seq[Byte]) {
    def javaPublicKey: PublicKey = {
      val publicKeySpec = new RSAPublicKeySpec(new BigInteger(modulus.toArray), new BigInteger(publicExponent.toArray))
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
          NkPublicRsaKey(modulus.toByteArray, publicExponent.toByteArray)
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

  case class NkPublicKey(purpose: String, algorithm: String, publicKey: NkPublicRsaKey)
  implicit val PublicKeyFormat = jsonFormat3(NkPublicKey)
  
  case class PublicKeyEnvelope(location: String, key: NkPublicKey)
  implicit val PublicKeyEnvelopeFormat = jsonFormat2(PublicKeyEnvelope)
  
  case class PasswordChange(newPassword: String)
  implicit val PasswordChangeFormat = jsonFormat1(PasswordChange)
  
  case class SimpleResponse(status: String)
  implicit val SystemStatusFormat = jsonFormat1(SimpleResponse)

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

  case class DecryptResponse(status: String, decrypted: Seq[Byte])
  implicit object DecryptResponse extends RootJsonFormat[DecryptResponse] {
    def write(x: DecryptResponse) = JsObject(
      "status" -> JsString(x.status), 
      "decrypted" -> JsString(x.decrypted.toBase64)
    )

    def read(value: JsValue) = {
      value.asJsObject.getFields("status", "decrypted") match {
        case Seq(JsString(status), JsString(decrypted)) =>
          new DecryptResponse(status, decrypted.toByteArray)
        case _ => deserializationError("DecryptResponse expected")
      }
    }
  }

  case class KeyImport(purpose: String, algorithm: String, privateKey: NkPrivateRsaKey)
  implicit val KeyImportFormat = jsonFormat3(KeyImport)
  
  case class PrivateKeyOperation(operation: String, blob: String, padding: Option[String] = None, hashAlgorithm: Option[String] = None)
  implicit val privateKeyOperationFormat = jsonFormat4(PrivateKeyOperation)
  
  case class KeyGeneration(purpose: String, algorithm: String, length: Int)
  implicit val keyGenerationFormat = jsonFormat3(KeyGeneration)
  
  case class PrivateKeyOperationResponse(blob: Seq[Byte])
  implicit val privateKeyOperationResponseFormat = jsonFormat1(PrivateKeyOperationResponse)
  
}