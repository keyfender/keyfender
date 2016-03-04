package com.nitrokey.nethsmtest

import akka.actor.ActorSystem
import Crypto._
import org.scalatest.FeatureSpec
import org.scalatest.concurrent.ScalaFutures
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
  val keyPair = generateRSACrtKeyPair(4096)
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
  import org.scalatest.time.Span
  import org.scalatest.time.Seconds
  implicit val defaultPatience = PatienceConfig(timeout =  Span(4, Seconds))
      
  feature("Setup and basic use cases") {

    scenario("NetHSM is up and running") {
      val pipeline: HttpRequest => Future[SimpleResponse] = sendReceive ~> unmarshal[SimpleResponse]
      val responseF = pipeline(Get(s"$apiLocation/system/status"))
      //responseF onComplete {
      //ScalaFutures.whenReady(responseF) { response => 
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
    
    scenario("Generate RSA key") {
      val request = KeyGeneration("signing", "RSA", 2048)
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive 
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Put(s"$apiLocation/keys/0", request))
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
      ScalaFutures.whenReady(responseF) { response => 
        val location = response.headers.toList.filter(_.is("location"))
        assert( location.nonEmpty )
        newKeyLocation = location.map(_.value).head
        assert(response.status.intValue === 303) //Redirection
      }
    }

    scenario("Retrieve public RSA key") {
      //To allow proper comparison the leading zero of values is dropped.
      val trimmedPubKey = NkPublicRsaKey(dropLeadingZero(keyPair.publicKey.modulus), dropLeadingZero(keyPair.publicKey.publicExponent))
      
      val pipeline: HttpRequest => Future[NkPublicKey] = sendReceive ~> unmarshal[NkPublicKey]
      val responseF: Future[NkPublicKey] = pipeline(Get(s"$host$newKeyLocation"))
      ScalaFutures.whenReady(responseF) { response => 
        assert(response.publicKey === trimmedPubKey )
      }
    }

    scenario("List existing keys") {
      val pipeline: HttpRequest => Future[List[PublicKeyEnvelope]] = (
        //TODO addCredentials(BasicHttpCredentials("admin", adminPassword))
        sendReceive 
        ~> unmarshal[List[PublicKeyEnvelope]]
      )
      val responseF: Future[List[PublicKeyEnvelope]] = pipeline(Get(s"$apiLocation/keys"))
      //ScalaFutures.whenReady(responseF) { response => 
      assert(responseF.futureValue.length > 0)
      assert(responseF.futureValue.exists(x => x.location === newKeyLocation))
    }

    scenario("Decrypt message (RSA, no padding)") {
      //implicit val encoding = base64Url.copy(strictPadding=true)
      val message = "Secure your digital life".getBytes
      val request = DecryptRequest( encrypt(message, "RSA/NONE/NoPadding", keyPair.publicKey) )
      val pipeline = sendReceive ~> unmarshal[DecryptResponse]
      val responseF: Future[DecryptResponse] = pipeline(Post(s"$host$newKeyLocation/actions/decrypt", request))
      assert(responseF.futureValue.status === "ok") 
      assert(trimPrefix(responseF.futureValue.decrypted) === message)
    }

    scenario("Decrypt message (RSA, PKCS#1 padding)") {
      //implicit val encoding = base64Url.copy(strictPadding=true)
      val message = "Secure your digital life".getBytes
      val request = DecryptRequest( encrypt(message, "RSA/NONE/PKCS1Padding", keyPair.publicKey) )
      val pipeline = sendReceive ~> unmarshal[DecryptResponse]
      val responseF: Future[DecryptResponse] = pipeline(Post(s"$host$newKeyLocation/actions/pkcs1/decrypt", request))
      assert(responseF.futureValue.status === "ok") 
      assert(trimPrefix(responseF.futureValue.decrypted) === message)
    }

    scenario("Sign message (RSA, PKCS#1 padding)") {
      val message = "Secure your digital life".getBytes
      val request = SignRequest(message)
      val pipeline = logRequest ~> sendReceive ~> logResponse ~> unmarshal[SignResponse]
      val responseF: Future[SignResponse] = pipeline(Post(s"$host$newKeyLocation/actions/pkcs1/sign", request))
      assert(responseF.futureValue.status === "ok") 
      assert(verifySignature(message, responseF.futureValue.signedMessage, keyPair.publicKey, "NONEwithRSA"))
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
  
  /**
   * Remove "zero" elements from the beginning of the array. This is required when a message, 
   * shorter than the key length, is encrypted without padding. Otherwise comparing the message would fail.
   */
  def trimPrefix(a: Seq[Byte]): Seq[Byte] =
    a.toList.reverse.takeWhile(_ != 0).reverse
}

