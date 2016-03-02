package com.nitrokey.nethsmtest

import spray.json._
import NetHsmProtocol._
import Crypto._
import Base64._

object Main extends App {
  println("Please start this program by executing \"sbt test\"")
  
/*  //From JSON to objects
  val source = """[
  {
    "location": "/keys/1",
    "key": {
      "purpose": "decrypt",
      "algorithm": "RSA",
      "publicKey": {
        "modulus":
          "r5JrMu80IEJoyM-9utzBs64Her9-VkjYhTU9a5ZrQ0zbECFYpdcTScRrWkZHy0Of6OLXumHHK_Krikmq1m53iw88iTVB_Up8oREkZt2szWifJlAVse9vfzERC_VmIFVqqZgmY1JopygVJ5_MMniOe8fN3iZAf-33ZB1aL14f0Y4m6xGXSN8er_q1yxevWy5oUVyF8Zl7r3ATERAX_9lsuLTZN9tAEBFqq4naH9mSsEsyRljybSuhX411CWUE4cj8JXf9qKumoN7duYNTjipSZqLauJ56txn5zTKDMGKvpcxB5jlQ_0ltVcGEayIjkXhJFR_dM2uwG4cQSmC4Bqn-yQ==",
        "publicExponent": "AQAB"
      }
    }
  },
  {
    "location": "/keys/2",
    "key": {
      "purpose": "provided",
      "algorithm": "RSA",
      "publicKey": {
        "modulus":
          "4n3PH830GffQRtrfYZ6qkXppBt9lPQSRWVM0RKG9n_zKOe4sRCwAsvNIJCaZqCanO_7FYY6ZCheYi1wOkuhtL4KBt282gRCj1THbGP5DBpf0diTzGxLV1cqYtptuFlPX2zscPipOCGfKdjRNViGdaoBHYKyTKlNXVXFwxQb9vv1jo5VrcQTPCT-HFdawomH7G4dV6UHc6aYNBh6-TL7Q0kcEOSJB2l5maDFVARKoRkj8vh2MQnIk_xdnnIekzrShgoCKX_1jSb-hTGg2jKzP0QqfblaHVTzTtEgcZNcC-00C2K7et4wkiBRz0hDfGEHhABUc7wIDaq9oTVoIOr_zMQ==",
        "publicExponent": "AQAB"
      }
    }
  }
]

"""
  //val envelopeFromSource = source.parseJson.convertTo[List[PublicKeyEnvelope]]
  
  //From objects to JSon
  val keyPair = generateRSACrtKeyPair(2048)
  val publicRsaKey = PublicRSAKey(keyPair.modulus, keyPair.publicExponent)
  val privKey: PrivateRSAKey = keyPair.privateKey
  val key = MyPublicKey("signing", "RSA", publicRsaKey)
  val envelopeFromObj = PublicKeyEnvelope("/my/location", key)
  val list = List(envelopeFromObj)
  println(list.toJson.prettyPrint)
  val newList = privKey.toJson.prettyPrint.parseJson.convertTo[PrivateRSAKey]
  println(newList)
   
  implicit val encoding = base64Url.copy(strictPadding=true)
  val s = "Secure your digital life"
  println(s + " -> " + s.getBytes.toBase64) */
}