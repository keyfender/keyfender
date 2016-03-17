package com.nitrokey.nethsmtest

import akka.actor.ActorSystem
import Crypto._
import org.scalatest.FeatureSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Span, Seconds, Millis}
import com.typesafe.scalalogging.LazyLogging
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.client.pipelining._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import NetHsmProtocol._
import com.typesafe.config.ConfigFactory
import java.io.File

class NetHsmTest extends FeatureSpec with LazyLogging with ScalaFutures {

  //Load settings
  //TODO: Handle IOException
  val conf = ConfigFactory.parseFile(new File("settings.conf"))
  val settings = new Settings(conf)

  var host: String = ""
  if(settings.tls) {
    host = "https://" + settings.host + ":" + settings.port
  } else {
    host = "http://" + settings.host + ":" + settings.port
  }
  val apiLocation = host + settings.prefix

  //Define values for the test scenarios
  val rand = new scala.util.Random
  val keyPair = generateRSACrtKeyPair(2048)
  val timeout = 5.seconds
  val adminPassword = "super secret"
  val userPassword = "super secret too"
  var newKeyLocation: String = ""

  //Spray needs an implicit ActorSystem and ExecutionContext
  implicit val system = ActorSystem("restClient")
  import system.dispatcher

  val logRequest: HttpRequest => HttpRequest = { r => logger.debug(r.toString); r }
  val logResponse: HttpResponse => HttpResponse = { r => logger.debug(r.toString); r }
  //private val defaultPipeline = defaultRequest ~> logRequest ~> sendReceive ~> logResponse

  //Define the timeout which needs to be longer than default for remote web service calls
  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(4, Seconds), interval = Span(100, Millis))

  feature("Setup and basic use cases") {

    scenario("NetHSM is up and running") {
      val pipeline: HttpRequest => Future[SimpleResponse] = sendReceive ~> unmarshal[SimpleResponse]
      val responseF = pipeline(Get(s"$apiLocation/system/status"))
      //responseF onComplete {
      //whenReady(responseF) { response =>
     assert(responseF.futureValue.status === "ok")
    }

/*    scenario("Change default admin password") {
      val request = PasswordChange(adminPassword)
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        addCredentials(BasicHttpCredentials("admin", ""))
        ~> sendReceive
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Post(s"$apiLocation/passwords/admin", request))
      pipeline.shouldEqual(OK)
      val response = Await.result(f, timeout)
      assert(response.status === "ok")
    }

    scenario("Change default user password") {
      val request = PasswordChange(userPassword)
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Post(s"$apiLocation/passwords/user", request))
      pipeline.shouldEqual(OK)
      val response = Await.result(f, timeout)
      assert(response.status === "ok")
    }
    */

    scenario("Import RSA key") {
      val request = KeyImport("signing", "RSA", keyPair.privateKey)
      val pipeline: HttpRequest => Future[HttpResponse] = (
        //TODO: addCredentials(BasicHttpCredentials("admin", ""))
        sendReceive
        //~> unmarshal[SimpleResponse]
      )
      val responseF: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        val location = response.headers.toList.filter(_.is("location"))
        assert( location.nonEmpty )
        newKeyLocation = location.map(_.value).head
        assert(response.status.intValue === 303) //Redirection to imported key
      }
    }

    ignore("Retrieve public RSA key") {
      //To allow proper comparison the leading zero of values is dropped.
      val trimmedPubKey = NkPublicRsaKey(dropLeadingZero(keyPair.publicKey.modulus), dropLeadingZero(keyPair.publicKey.publicExponent))

      val pipeline: HttpRequest => Future[NkPublicKey] = sendReceive ~> unmarshal[NkPublicKey]
      val responseF: Future[NkPublicKey] = pipeline(Get(s"$host$newKeyLocation"))
      whenReady(responseF) { response =>
        assert(response.publicKey === trimmedPubKey )
      }
    }

    ignore("List existing keys") {
      val pipeline: HttpRequest => Future[List[PublicKeyEnvelope]] = (
        //TODO addCredentials(BasicHttpCredentials("admin", adminPassword))
        sendReceive
        ~> unmarshal[List[PublicKeyEnvelope]]
      )
      val responseF: Future[List[PublicKeyEnvelope]] = pipeline(Get(s"$apiLocation/keys"))
      //whenReady(responseF) { response =>
      assert(responseF.futureValue.length > 0)
      assert(responseF.futureValue.exists(x => x.location === newKeyLocation))
    }

    ignore("Decrypt message (RSA, no padding)") {
      //implicit val encoding = base64Url.copy(strictPadding=true)
      val message = "Secure your digital life".getBytes
      val request = DecryptRequest( encrypt(message, "RSA/NONE/NoPadding", keyPair.publicKey) )
      val pipeline = sendReceive ~> unmarshal[DecryptResponse]
      val responseF: Future[DecryptResponse] = pipeline(Post(s"$host$newKeyLocation/actions/decrypt", request))
      assert(responseF.futureValue.status === "ok")
      assert(trimPrefix(responseF.futureValue.decrypted) === message)
    }

    ignore("Decrypt message (RSA, PKCS#1 padding)") {
      //implicit val encoding = base64Url.copy(strictPadding=true)
      val message = "Secure your digital life".getBytes
      val request = DecryptRequest( encrypt(message, "RSA/NONE/PKCS1Padding", keyPair.publicKey) )
      val pipeline = sendReceive ~> unmarshal[DecryptResponse]
      val responseF: Future[DecryptResponse] = pipeline(Post(s"$host$newKeyLocation/actions/pkcs1/decrypt", request))
      assert(responseF.futureValue.status === "ok")
      assert(responseF.futureValue.decrypted === message)
    }

    ignore("Sign message (RSA, PKCS#1 padding)") {
      val message = "Secure your digital life".getBytes
      val request = SignRequest(message)
      val pipeline = sendReceive ~> unmarshal[SignResponse]
      val responseF: Future[SignResponse] = pipeline(Post(s"$host$newKeyLocation/actions/pkcs1/sign", request))
      assert(responseF.futureValue.status === "ok")
      assert(verifySignature(message, responseF.futureValue.signedMessage, keyPair.publicKey, "NONEwithRSA"))
    }

    scenario("Delete RSA key") {
      val pipeline: HttpRequest => Future[HttpResponse] = (
        //TODO: addCredentials(BasicHttpCredentials("admin", ""))
        sendReceive
        //~> unmarshal[SimpleResponse]
      )
      val responseF: Future[HttpResponse] = pipeline(Delete(s"$host$newKeyLocation"))
      whenReady(responseF) { response =>
        val pipeline2: HttpRequest => Future[HttpResponse] = sendReceive
        val responseF2: Future[HttpResponse] = pipeline2(Get(s"$host$newKeyLocation"))
        assert(responseF2.futureValue.status.intValue === 404 )
      }
    }

    scenario("Generate RSA key") {
      val request = KeyGeneration("signing", "RSA", 2048)
      val pipeline: HttpRequest => Future[HttpResponse] = (
        //TODO: addCredentials(BasicHttpCredentials("admin", ""))
        logRequest ~>
        sendReceive ~>
        logResponse
        //~> unmarshal[SimpleResponse]
      )
      val responseF: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        val location = response.headers.toList.filter(_.is("location"))
        assert( location.nonEmpty )
        newKeyLocation = location.map(_.value).head
        assert(response.status.intValue === 303) //Redirection to new key
      }
    }

    /*
    scenario("Perform SHA256 signature with imported key") {
      val request = PrivateKeyOperation("signature", message, Some("SHA-256"), Some("PKCS#1"))
      val pipeline: HttpRequest => Future[PrivateKeyOperationResponse] = (
        addCredentials(BasicHttpCredentials("user", userPassword))
        ~> sendReceive
        ~> unmarshal[PrivateKeyOperationResponse]
      )
      val f: Future[PrivateKeyOperationResponse] = pipeline(Post(s"$apiLocation/keys/$keyID/secret", request))
      pipeline.shouldEqual(OK)
      val response = Await.result(f, timeout)
      assert(verifySignature(message, response.blob, keyPair.publicKey, "SHA256WithRSA"))
    }
    */
  }

  // feature("Performance tests") {
  //
  //   scenario("Import RSA key") {
  //     val request = KeyImport("signing", "RSA", keyPair.privateKey)
  //     val pipeline: HttpRequest => Future[HttpResponse] = (
  //       //TODO: addCredentials(BasicHttpCredentials("admin", ""))
  //       sendReceive
  //       //~> unmarshal[SimpleResponse]
  //     )
  //     val responseF: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
  //     whenReady(responseF) { response =>
  //       val location = response.headers.toList.filter(_.is("location"))
  //       assert( location.nonEmpty )
  //       newKeyLocation = location.map(_.value).head
  //       assert(response.status.intValue === 303) //Redirection to imported key
  //     }
  //   }
  //
  //   scenario("Sign 100 messages (RSA, PKCS#1 padding)") {
  //     val rounds = 100
  //
  //     //Create 100 requests
  //     val requests = (0 to rounds).map(counter => {
  //       SignRequest( s"Secure your digital life $counter".getBytes )
  //     })
  //
  //     val pipeline = sendReceive
  //
  //     //create 100 response-futures
  //     val responsesF = (0 to rounds).map(counter => {
  //       pipeline(Post(s"$host$newKeyLocation/actions/pkcs1/sign", requests.seq(counter)))
  //     })
  //
  //     //execute
  //     val responses = time { Future.sequence(responsesF).futureValue }
  //
  //     //test
  //     responses.map(response =>
  //       assert(response.status.isSuccess)
  //     )
  //   }
  //
  //   scenario("Decrypt 100 messages (RSA, PKCS#1 padding)") {
  //     val rounds = 100
  //
  //     //Create 100 messages
  //     val messages = (0 to rounds).map(counter => {
  //       s"Secure your digital life $counter".getBytes
  //     })
  //
  //     //Create 100 requests
  //     val requests = (0 to rounds).map(counter => {
  //       DecryptRequest( encrypt(messages(counter), "RSA/NONE/PKCS1Padding", keyPair.publicKey) )
  //     })
  //
  //     val pipeline = sendReceive ~> unmarshal[DecryptResponse]
  //
  //     //create 100 response-futures
  //     val responsesF = (0 to rounds).map(counter => {
  //       pipeline(Post(s"$host$newKeyLocation/actions/pkcs1/decrypt", requests.seq(counter)))
  //     })
  //
  //     //execute
  //     val responses = time { Future.sequence(responsesF).futureValue }
  //
  //     //test
  //     responses.zipWithIndex.map{case (response, i) => {
  //       assert(response.status == "ok")
  //       assert(trimPrefix(response.decrypted) === messages(i) )
  //     }}
  //   }
  //
  // }

  /**
   * Remove "zero" elements from the beginning of the array. This is required when a message,
   * shorter than the key length, is encrypted without padding. Otherwise comparing the message would fail.
   */
  def trimPrefix(a: Seq[Byte]): Seq[Byte] =
    a.toList.reverse.takeWhile(_ != 0).reverse

  /**
   * Measure execution time of an arbitrary function. Example: time{ println("Hello world") }
   */
  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) + "ns = " + (t1 - t0)/1000000 + "ms")
    result
  }
}
