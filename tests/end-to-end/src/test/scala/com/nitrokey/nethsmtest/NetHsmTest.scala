package com.nitrokey.nethsmtest

import akka.actor.ActorSystem
import Crypto._
import org.scalatest.{FeatureSpec, GivenWhenThen, BeforeAndAfterAll, BeforeAndAfter}
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
import collection.mutable.ListBuffer

class NetHsmTest extends FeatureSpec with LazyLogging with ScalaFutures with GivenWhenThen with BeforeAndAfter {

  //Load settings
  //TODO: Handle IOException
  val conf = ConfigFactory.parseFile(new File("settings.conf"))
  val settings = new Settings(conf)
  val host = settings.fullHost
  val apiLocation = host + settings.prefix
  
  //Spray needs an implicit ActorSystem and ExecutionContext
  implicit val system = ActorSystem("restClient")
  import system.dispatcher

  //Define the timeout which needs to be longer than default for remote web service calls
  //and generation of 4096 bit keys.
  val timeout = 10.seconds
  implicit val defaultPatience =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  //Define values for the test scenarios
  val rand = new scala.util.Random
  val keyLengths = List(1024, 2048, 3072, 4096)
  val keyEnvelopes = new ListBuffer[PublicKeyEnvelope]
  val adminPassword = "super secret"
  val userPassword = "secret too"
  
  val logRequest: HttpRequest => HttpRequest = { r => logger.debug(r.toString); r }
  val logResponse: HttpResponse => HttpResponse = { r => logger.debug(r.toString); r }
  //private val defaultPipeline = defaultRequest ~> logRequest ~> sendReceive ~> logResponse

  feature("NetHSM tells it's system status") {
    scenario("NetHSM is up and running") {
      val pipeline: HttpRequest => Future[SimpleResponse] = sendReceive ~> unmarshal[SimpleResponse]
      val responseF = pipeline(Get(s"$apiLocation/system/status"))
      //responseF onComplete {
      //whenReady(responseF) { response =>
     assert(responseF.futureValue.status === "ok")
    }
  }
  
  feature("Basic password management") {
    ignore("Change default admin password") {
      val request = PasswordChange(adminPassword)
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        addCredentials(BasicHttpCredentials("admin", ""))
        ~> sendReceive
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Post(s"$apiLocation/passwords/admin", request))
      val response = Await.result(f, timeout)
      assert(response.status === "ok")
    }

    ignore("Change default user password") {
      val request = PasswordChange(userPassword)
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Post(s"$apiLocation/passwords/user", request))
      val response = Await.result(f, timeout)
      assert(response.status === "ok")
    }
  }

  feature("RSA key import") {
    val purpose = "encryption"
    keyLengths.map{ keyLength =>
  
      scenario(s"Importing a RSA-$keyLength key for $purpose") {
        //Given(s"A RSA-$keyLength key for $purpose")
        val keyPair = generateRSACrtKeyPair(keyLength)
        val request = KeyImport(purpose, "RSA", keyPair.privateKey)
        //publicKey = keyPair.publicKey :: publicKey
        
        //When("Key is imported")
        val pipeline: HttpRequest => Future[HttpResponse] = (
          //TODO: addCredentials(BasicHttpCredentials("admin", ""))
          sendReceive
          //~> unmarshal[SimpleResponse]
        )
        val responseF: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
        whenReady(responseF) { response =>
          //Then("Redirect is returned")
          val location = response.headers.toList.filter(_.is("location"))
          assert( location.nonEmpty )
          assert(response.status.intValue === 303) //Redirection to imported key
          
          val keyLocation: String = location.map(_.value).head
          //val f = fixture
          //fixture.keyEnvelopes = PublicKeyEnvelope(keyLocation, NkPublicKey(purpose, "RSA", keyPair.publicKey)) :: fixture.keyEnvelopes //Use this key for subsequent tests

          //To allow proper comparison the leading zero of values is dropped.
          val trimmedPubKey = NkPublicRsaKey(dropLeadingZero(keyPair.publicKey.modulus), dropLeadingZero(keyPair.publicKey.publicExponent))
          keyEnvelopes += PublicKeyEnvelope(keyLocation, NkPublicKey(purpose, "RSA", trimmedPubKey)) //Use this key for subsequent tests
      
          val pipeline: HttpRequest => Future[NkPublicKeyResponse] = sendReceive ~> unmarshal[NkPublicKeyResponse]
          val responseF: Future[NkPublicKeyResponse] = pipeline(Get(s"$host$keyLocation"))
          whenReady(responseF) { response =>
            assert(response.status === "success")
            assert(response.data.publicKey === trimmedPubKey )
          }
        }
      }
    }
    
    scenario(s"Importing a RSA key for encryption") (pending)
    
    scenario(s"Importing a RSA key for authentication") (pending)
  }
  
  feature("Generate RSA keys") {
    keyLengths.map{ keyLength =>
      scenario(s"Generate RSA-$keyLength key") {
        //Given("Key generation request")
        val request = KeyGeneration("signing", "RSA", keyLength)
        val pipeline: HttpRequest => Future[HttpResponse] = (
          //TODO: addCredentials(BasicHttpCredentials("admin", ""))
          sendReceive
          //~> unmarshal[SimpleResponse]
        )
        //When("Key is generated")
        val responseF: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
        whenReady(responseF) { response =>
          val location = response.headers.toList.filter(_.is("location"))
          assert( location.nonEmpty )
          val keyLocation = location.map(_.value).head
          assert(response.status.intValue === 303) //Redirection to new key
    
          //Then("Retrieve new public key and verify its length")
          val pipeline: HttpRequest => Future[NkPublicKeyResponse] = sendReceive ~> unmarshal[NkPublicKeyResponse]
          val responseF: Future[NkPublicKeyResponse] = pipeline(Get(s"$host$keyLocation"))
          whenReady(responseF) { response =>
            assert(response.status === "success")
            assert(response.data.publicKey.modulus.length*8 === keyLength)
            keyEnvelopes += PublicKeyEnvelope(keyLocation, NkPublicKey("signing", "RSA", //response.publicKey))
                NkPublicRsaKey(dropLeadingZero(response.data.publicKey.modulus), dropLeadingZero(response.data.publicKey.publicExponent))))
          }
        }
      }
    }
  }
  
  feature("List existing keys") {
    scenario("List existing keys and check for given keys") {
      keyEnvelopes.map{ keyEnvelope =>
        info("Check for key " + keyEnvelope.location)
        val pipeline: HttpRequest => Future[PublicKeyEnvelopeResponse] = (
          //TODO addCredentials(BasicHttpCredentials("admin", adminPassword))
          sendReceive
          ~> unmarshal[PublicKeyEnvelopeResponse]
        )
        val responseF: Future[PublicKeyEnvelopeResponse] = pipeline(Get(s"$apiLocation/keys"))
        whenReady(responseF) { response =>
          assert(response.status === "success")
          assert(response.data.length > 0)
          assert(response.data.exists(x => x.location === keyEnvelope.location))
        }
      }
    }
    scenario("Test pagination") (pending)
  }

  feature("Decrypt message") {
    scenario("Decrypt message (RSA, no padding)") {
      keyEnvelopes.filter(x => x.key.purpose == "encryption").map{ keyEnvelope =>
        decryptionTest(keyEnvelope, "", "RSA/NONE/NoPadding")
      }
    }
    
    scenario("Decrypt message (RSA, PKCS#1 padding)") {
      keyEnvelopes.filter(x => x.key.purpose == "encryption").map{ keyEnvelope =>
        decryptionTest(keyEnvelope, "/pkcs1", "RSA/NONE/PKCS1Padding")
      }
    }
    scenario("Decrypt message (RSA, OAEP)") {
      keyEnvelopes.filter(x => x.key.purpose == "encryption").map{ keyEnvelope =>
        decryptionTest(keyEnvelope, "/oaep/sha1", "RSA/None/OAEPWithSHA1AndMGF1Padding")
      }      
    }
  }
  
  feature("Sign message") {
    scenario("Sign message (RSA, PKCS#1 padding)") {
      keyEnvelopes.filter(x => x.key.purpose == "signing").map{ keyEnvelope =>
        signatureTest(keyEnvelope, "/pkcs1", "NONEwithRSA")
      }
    }
    scenario("Sign message (RSA, PSS padding)") {
      keyEnvelopes.filter(x => x.key.purpose == "signing").map{ keyEnvelope =>
        signatureTest(keyEnvelope, "/pss/sha1", "RSASSA-PSS")
      }      
    }
        
    ignore("Perform SHA256 signature with imported key") {
    /*  val message = "secure your digital life"
      val request = PrivateKeyOperation("signature", message, Some("SHA-256"), Some("PKCS#1"))
      val pipeline: HttpRequest => Future[PrivateKeyOperationResponse] = (
        //addCredentials(BasicHttpCredentials("user", userPassword))
        sendReceive
        ~> unmarshal[PrivateKeyOperationResponse]
      )
      val location = keyEnvelope.location
      val f: Future[PrivateKeyOperationResponse] = pipeline(Post(s"$host$location", request))
      val response = Await.result(f, timeout)
      assert(verifySignature(message.getBytes, response.blob, keyEnvelope.key.publicKey, "SHA256WithRSA")) */
    }
    */
  }
  
  feature("Delete RSA key") {
    scenario("Delete RSA key") {
      keyEnvelopes.map{ keyEnvelope =>
        val keyLength = keyEnvelope.key.publicKey.modulus.length*8
        info(s"A RSA key with $keyLength bit in NetHSM")
        val pipeline: HttpRequest => Future[HttpResponse] = (
          //TODO: addCredentials(BasicHttpCredentials("admin", ""))
          sendReceive
          //~> unmarshal[SimpleResponse]
        )
        
        //When("Key is deleted")
        val location = keyEnvelope.location
        val responseF: Future[HttpResponse] = pipeline(Delete(s"$host$location"))
        whenReady(responseF) { response =>
          //Then("Key doesn't exist anymore")
          val pipeline2: HttpRequest => Future[HttpResponse] = sendReceive
          val responseF2: Future[HttpResponse] = pipeline2(Get(s"$host$location"))
          assert(responseF2.futureValue.status.intValue === 404 )
        }
      }
    }
  }
  
  feature("Performance tests") {
    val publicKey = null //keyEnvelopes.head.key.publicKey
    val keyLocation = null //keyEnvelopes.head.location
    //TODO generate new key

    ignore("Sign 100 messages (RSA, PKCS#1 padding)") {
      val rounds = 100

      //Create 100 requests
      val requests = (0 to rounds).map(counter => {
        SignRequest( s"Secure your digital life $counter".getBytes )
      })

      val pipeline = sendReceive

      //create 100 response-futures
      val responsesF = (0 to rounds).map(counter => {
        pipeline(Post(s"$host$keyLocation/actions/pkcs1/sign", requests.seq(counter)))
      })

      //execute
      val responses = time { Future.sequence(responsesF).futureValue }

      //test
      responses.map(response =>
        assert(response.status.isSuccess)
      )
    }

    ignore("Decrypt 100 messages (RSA, PKCS#1 padding)") {
      val rounds = 100

      //Create 100 messages
      val messages = (0 to rounds).map(counter => {
        s"Secure your digital life $counter".getBytes
      })

      //Create 100 requests
      val requests = (0 to rounds).map(counter => {
        DecryptRequest( encrypt(messages(counter), "RSA/NONE/PKCS1Padding", publicKey) )
      })

      val pipeline = sendReceive ~> unmarshal[DecryptResponse]

      //create 100 response-futures
      val responsesF = (0 to rounds).map(counter => {
        pipeline(Post(s"$host$keyLocation/actions/pkcs1/decrypt", requests.seq(counter)))
      })

      //execute
      val responses = time { Future.sequence(responsesF).futureValue }

      //test
      responses.zipWithIndex.map{case (response, i) => {
        assert(response.status == "ok")
        assert(trimPrefix(response.data.decrypted) === messages(i) )
      }}
    }

  }

  def decryptionTest(keyEnvelope: PublicKeyEnvelope, parameter: String, cipherSuite: String) = {
    val keyLength = keyEnvelope.key.publicKey.modulus.length*8
    info(s"A RSA key with $keyLength bit")
    
    //implicit val encoding = base64Url.copy(strictPadding=true)
    //Given("An encrypted message")
    val message = "Secure your digital life".getBytes
    val request = DecryptRequest( encrypt(message, cipherSuite, keyEnvelope.key.publicKey) )
    
    //When("Message is decrypted")
    val pipeline = sendReceive ~> unmarshal[DecryptResponse]
    val location = keyEnvelope.location
    val responseF: Future[DecryptResponse] = pipeline(Post(s"$host$location/actions$parameter/decrypt", request))
    
    //Then("Decrypted message is identical")
    whenReady(responseF) { response =>
      assert(response.status === "success")
      assert(trimPrefix(response.data.decrypted) === message)
    }
  }
  
  def signatureTest(keyEnvelope: PublicKeyEnvelope, parameter: String, cipherSuite: String) = {
    val keyLength = keyEnvelope.key.publicKey.modulus.length*8
    info(s"A RSA key with $keyLength bit")
  
    //Given("A message")
    val message = "Secure your digital life".getBytes
    
    //When("Message is signed")
    val request = SignRequest(message)
    val pipeline = sendReceive ~> unmarshal[SignResponse]
    val location = keyEnvelope.location
    val responseF: Future[SignResponse] = pipeline(Post(s"$host$location/actions$parameter/sign", request))
    
    //Then("Signature is correct")
    whenReady(responseF) { response =>
      assert(response.status === "success")
      assert(verifySignature(message, response.data.signedMessage, keyEnvelope.key.publicKey, cipherSuite))
    }
  }
  
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