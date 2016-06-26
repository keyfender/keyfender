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
import collection.mutable.ListBuffer
import spray.httpx.unmarshalling._
import java.io.{ByteArrayInputStream, BufferedReader, Reader, InputStreamReader}
import java.security.interfaces.RSAPublicKey
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMReader

class NetHsmTest extends FeatureSpec with LazyLogging with ScalaFutures {

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
  val hashAlgorithms = List("md5", "sha1", "sha224", "sha256", "sha384", "sha512")
  val keyEnvelopes = new ListBuffer[PublicKeyEnvelope]
  val tempAdminPassword = "bla"
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

  feature("Password management") {
    scenario("Set blank admin password") {
      val request = PasswordChange(tempAdminPassword)
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        sendReceive
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Put(s"$apiLocation/system/passwords/admin", request))
      val response = Await.result(f, timeout)
      assert(response.status === "success") //TODO: Change to "ok" to be consistent
    }

    scenario("Changing admin password requires authentication") {
      val request = PasswordChange(tempAdminPassword)
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      val f: Future[HttpResponse] = pipeline(Put(s"$apiLocation/system/passwords/admin", request))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401)  // unauthorized
    }

    scenario("Changing admin password again") {
      val request = PasswordChange(adminPassword)
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        addCredentials(BasicHttpCredentials("admin", tempAdminPassword))
        ~> sendReceive
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Put(s"$apiLocation/system/passwords/admin", request))
      val response = Await.result(f, timeout)
      assert(response.status === "success") //TODO: Change to "ok" to be consistent
    }

    scenario("Set blank user password") {
      val request = PasswordChange(userPassword)
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Put(s"$apiLocation/system/passwords/user", request))
      val response = Await.result(f, timeout)
      assert(response.status === "success") //TODO: Change to "ok" to be consistent
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
          addCredentials(BasicHttpCredentials("admin", adminPassword))
          ~> sendReceive
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

          val pipeline: HttpRequest => Future[NkPublicKeyResponse] = (
            addCredentials(BasicHttpCredentials("user", userPassword))
            ~> sendReceive ~> unmarshal[NkPublicKeyResponse]
          )
          val responseF: Future[NkPublicKeyResponse] = pipeline(Get(s"$host$keyLocation"))
          whenReady(responseF) { response =>
            assert(response.status === "success")
            assert(response.data.publicKey === trimmedPubKey )
          }
        }
      }
    }

    scenario("Importing a RSA key for encryption") (pending)

    scenario("Importing a RSA key for authentication") (pending)

    scenario("Importing unsupported ECC key fails") {
      val keyPair = generateRSACrtKeyPair(2048)
      val request = KeyImport("signing", "ECC", keyPair.privateKey)
      val pipeline: HttpRequest => Future[JsendResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive ~> unmarshal[JsendResponse]
      )
      val responseF: Future[JsendResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        assert(response.status === "failure")
      }
    }

    ignore("Importing RSA key for unsupported purpose 'payment' fails") {
      val keyPair = generateRSACrtKeyPair(2048)
      val request = KeyImport("payment", "RSA", keyPair.privateKey)
      val pipeline: HttpRequest => Future[JsendResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[JsendResponse]
      )
      val responseF: Future[JsendResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        assert(response.status === "error")
        //assert(response.status.intValue !== 303 ) See FromResponseUnmarshaller
      }
    }
  }

  feature("Generate RSA keys") {

    keyLengths.map{ keyLength =>
      scenario(s"Generate RSA-$keyLength key") {
        //Given("Key generation request")
        val request = KeyGeneration("signing", "RSA", keyLength)
        val pipeline: HttpRequest => Future[HttpResponse] = (
          addCredentials(BasicHttpCredentials("admin", adminPassword))
          ~> sendReceive
        )
        //When("Key is generated")
        val responseF: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
        whenReady(responseF) { response =>
          val location = response.headers.toList.filter(_.is("location"))
          assert( location.nonEmpty )
          val keyLocation = location.map(_.value).head
          assert(response.status.intValue === 303) //Redirection to new key

          //Then("Retrieve new public key and verify its length")
          val pipeline: HttpRequest => Future[NkPublicKeyResponse] = (
            addCredentials(BasicHttpCredentials("user", userPassword))
            ~> sendReceive
            ~> unmarshal[NkPublicKeyResponse]
          )
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

    scenario("Generating unsupported ECC key fails") {
      val request = KeyGeneration("signing", "ECC", 2048)
      val pipeline: HttpRequest => Future[JsendResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[JsendResponse]
      )
      val responseF: Future[JsendResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        assert(response.status === "failure")
      }
    }

    ignore("Generating RSA key for unsupported purpose 'payment' fails") {
      val request = KeyGeneration("payment", "RSA", 2048)
      val pipeline: HttpRequest => Future[JsendResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[JsendResponse]
      )
      val responseF: Future[JsendResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        assert(response.status === "error")
        //assert(response.status.intValue !== 303 ) See FromResponseUnmarshaller
      }
    }
  }

  feature("List existing keys") {
    scenario("List existing keys and check for given keys") {
      keyEnvelopes.map{ keyEnvelope =>
        info("Check for key " + keyEnvelope.location)
        val pipeline: HttpRequest => Future[PublicKeyEnvelopeResponse] = (
          addCredentials(BasicHttpCredentials("user", userPassword))
          ~> sendReceive
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

    scenario("Retrieve public keys in PEM format") {
      keyEnvelopes.map{ keyEnvelope =>
        info("Check for key " + keyEnvelope.location)
        val pipeline: HttpRequest => Future[HttpResponse] = (
          addCredentials(BasicHttpCredentials("user", userPassword))
          ~> sendReceive
        )
        val responseF: Future[HttpResponse] = pipeline(Get(host + keyEnvelope.location + "/public.pem"))
        whenReady(responseF) { response =>
          val pubKey = response.entity.asString //Extracting the HTTP response body

          //Check PEM public key
          Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
          val tube: ByteArrayInputStream = new ByteArrayInputStream(pubKey.getBytes())
          val fRd: Reader = new BufferedReader(new InputStreamReader(tube))
          val pr: PEMReader = new PEMReader(fRd) //BC 1.48 introduces PEMParser instead of PEMReader
          val o = pr.readObject()
          assert(o.isInstanceOf[RSAPublicKey])
        }
      }
    }
  }

  /*
  ignore("Overwrite existing keys") {
    scenario("Overwrite every second key") {
      Iterator.from(1, 2).takeWhile(_ < keyEnvelopes.size).map(keyEnvelopes(_))foreach{ keyEnvelope =>
        //TODO overwrite...
      }
      /*
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
        keyEnvelopes = PublicKeyEnvelope(keyLocation, NkPublicKey(purpose, "RSA", trimmedPubKey)) //Use this key for subsequent tests
        val pipeline: HttpRequest => Future[NkPublicKeyResponse] = sendReceive ~> unmarshal[NkPublicKeyResponse]
        val responseF: Future[NkPublicKeyResponse] = pipeline(Get(s"$host$keyLocation"))
        whenReady(responseF) { response =>
          assert(response.status === "success")
          assert(response.data.publicKey === trimmedPubKey )
        }
      } */
    }
  }
*/
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
    hashAlgorithms.map{ hash =>
      scenario("Decrypt message (RSA, OAEP, "+hash+")") {
        keyEnvelopes.filter(x => x.key.purpose == "encryption").map{ keyEnvelope =>
          if( !(keyEnvelope.key.publicKey.modulus.length == 128 && hash == "sha512")) //Exception because SHA512 is too large for 1024 bit RSA keys
            decryptionTest(keyEnvelope, "/oaep/"+hash, "RSA/None/OAEPWith"+hash+"AndMGF1Padding")
        }
      }
    }

    ignore("Decryption fails with signing key") {
      val message = "Secure your digital life".getBytes
      keyEnvelopes.filter(x => x.key.purpose != "encryption").map{ keyEnvelope =>
        val keyLength = keyEnvelope.key.publicKey.modulus.length*8
        info("RSA-" + keyLength + " key for " + keyEnvelope.key.purpose)

        val request = DecryptRequest( encrypt(message, "RSA/NONE/NoPadding", keyEnvelope.key.publicKey) )
        val pipeline = (
          addCredentials(BasicHttpCredentials("user", userPassword))
          ~> sendReceive //~> unmarshal[JsendResponse]
        )
        val location = keyEnvelope.location
        val responseF: Future[HttpResponse] = pipeline(Post(s"$host$location/actions/decrypt", request))
        whenReady(responseF) { response =>
          assert(response.status.intValue === 405 )
          //assert((response ~> unmarshal[JsendResponse]).status === "error")
        }
      }
    }
  }

  feature("Sign message") {

    scenario("Sign message (RSA, PKCS#1 padding)") {
      keyEnvelopes.filter(x => x.key.purpose == "signing").map{ keyEnvelope =>
        signatureTest(keyEnvelope, "/pkcs1", "NONEwithRSA")
      }
    }

    hashAlgorithms.filter(x => x != "md5").map{ hash => //MD5 doesn't work with SUN and BouncyCastle Security Provider
      scenario("Sign message (RSA, PSS, "+hash+")") {
        keyEnvelopes.filter(x => x.key.purpose == "signing").map{ keyEnvelope =>
          if( !(keyEnvelope.key.publicKey.modulus.length == 128 && hash == "sha512")) //Exception because SHA512 is too large for 1024 bit RSA keys
            signatureTest(keyEnvelope, "/pss/"+hash, hash+"withRSAandMGF1") //similar to "RSASSA-PSS"
        }
      }
    }

    ignore("Signing fails with encryption key") {
      val message = "Secure your digital life".getBytes
      keyEnvelopes.filter(x => x.key.purpose != "signing").map{ keyEnvelope =>
        val keyLength = keyEnvelope.key.publicKey.modulus.length*8
        info("RSA-" + keyLength + " key for " + keyEnvelope.key.purpose)

        val request = SignRequest(message)
        val pipeline = (
          addCredentials(BasicHttpCredentials("user", userPassword))
          ~> sendReceive //~> unmarshal[JsendResponse]
        )
        val location = keyEnvelope.location
        val responseF: Future[HttpResponse] = pipeline(Post(s"$host$location/actions/pkcs1/sign", request))
        whenReady(responseF) { response =>
          assert(response.status.intValue === 405 )
          //assert((response ~> unmarshal[JsendResponse]).status === "error")
        }
      }
    }

    ignore("Client performs SHA256 hash and NetHSM signs the hash") {
      val cleartextMessage = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet."
      val messageHash = hash(cleartextMessage, "SHA256")
      val request = SignRequest(messageHash)
      val pipeline = (
        addCredentials(BasicHttpCredentials("user", userPassword))
        ~> sendReceive ~> unmarshal[SignResponse]
      )
      val keyEnvelope = keyEnvelopes.head
      val location = keyEnvelope.location
      val responseF: Future[SignResponse] = pipeline(Post(s"$host$location/actions/pss/sign", request))
      whenReady(responseF) { response =>
        assert(response.status === "success")
        assert(verifySignature(messageHash, response.data.signedMessage, keyEnvelope.key.publicKey, "NONEwithRSAandMGF1"))
      }
    }
  }

  feature("Delete RSA key") {
    scenario("Delete RSA key") {
      keyEnvelopes.map{ keyEnvelope =>
        val keyLength = keyEnvelope.key.publicKey.modulus.length*8
        info(s"A RSA key with $keyLength bit in NetHSM")
        val pipeline: HttpRequest => Future[HttpResponse] = (
          addCredentials(BasicHttpCredentials("admin", adminPassword))
          ~> sendReceive
          //~> unmarshal[SimpleResponse]
        )

        //When("Key is deleted")
        val location = keyEnvelope.location
        val responseF: Future[HttpResponse] = pipeline(Delete(s"$host$location"))
        whenReady(responseF) { response =>
          //Then("Key doesn't exist anymore")
          val pipeline2: HttpRequest => Future[HttpResponse] = (
            addCredentials(BasicHttpCredentials("user", userPassword))
            ~> sendReceive
          )
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

      //val pipeline = sendReceive

      //create 100 response-futures
      val responsesF = (0 to rounds).map(counter => {
        val pipeline: HttpRequest => Future[HttpResponse] = (
          addCredentials(BasicHttpCredentials("user", userPassword))
          ~> sendReceive
        )
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

      val pipeline = (
        addCredentials(BasicHttpCredentials("user", userPassword))
        ~> sendReceive
        ~> unmarshal[DecryptResponse]
      )
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

    //Given("An encrypted message")
    val message = "secure your digital life".getBytes
    val request = DecryptRequest( encrypt(message, cipherSuite, keyEnvelope.key.publicKey) )

    //When("Message is decrypted")
    val pipeline = (
      addCredentials(BasicHttpCredentials("user", userPassword))
      ~> sendReceive
      ~> unmarshal[DecryptResponse]
    )
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
    //val message = "Secure your digital life".getBytes
    val cleartextMessage = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet."

    val message: Seq[Byte] = cipherSuite match {
      case "NONEwithRSA" => hash(cleartextMessage, "sha256")
      case _ => cleartextMessage.getBytes
    }

    //When("Message is signed")
    val request = SignRequest(message)
    val pipeline = (
      addCredentials(BasicHttpCredentials("user", userPassword))
      ~> sendReceive
      ~> unmarshal[SignResponse]
    )
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
