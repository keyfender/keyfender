package org.keyfender.keyfendertest

import akka.actor.ActorSystem
import akka.io.IO
import collection.mutable.ListBuffer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import Crypto._
import java.io.File
import java.io.{ByteArrayInputStream, BufferedReader, Reader, InputStreamReader}
import java.security.interfaces.RSAPublicKey
import java.security.Security
import java.security.cert.X509Certificate
import javax.net.ssl.{KeyManager, SSLContext, X509TrustManager}
import KeyfenderProtocol._
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMReader
import org.scalatest.FeatureSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.time.{Span, Seconds, Millis}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import spray.can.Http
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling._

/**
 * These tests are highly dependent and all together represent an entire lifecycle of a keyfender.
 * In case of error, focus on the first error because it may cause subsequent errors.
 */
class KeyfenderTest extends FeatureSpec with LazyLogging with ScalaFutures with IntegrationPatience {

  //Load settings
  //TODO: Handle IOException
  val conf = ConfigFactory.parseFile(new File("settings.conf"))
  val settings = new Settings(conf)
  val host = settings.fullHost
  val apiLocation = host + settings.prefix

  implicit val mySSLContext: SSLContext = {
      object WideOpenX509TrustManager extends X509TrustManager {
        override def checkClientTrusted(chain: Array[X509Certificate], authType: String) = ()
        override def checkServerTrusted(chain: Array[X509Certificate], authType: String) = ()
        override def getAcceptedIssuers = Array[X509Certificate]()
      }

      val context = SSLContext.getInstance("TLS")
      context.init(Array[KeyManager](), Array(WideOpenX509TrustManager), null)
      context
  }

  //Spray needs an implicit ActorSystem and ExecutionContext
  implicit val system = ActorSystem("restClient")
  import system.dispatcher

  //Define the timeout which needs to be longer than default for remote web service calls
  //and generation of 4096 bit keys.
  val timeout = 10.seconds
  //implicit val defaultPatience =
  //  PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  //Define values for the test scenarios
  val rand = new scala.util.Random

  //These key lengths are going to be used for key generation
  val keyLengths = List(1024, 2048, 3072, 4096)

  //These hash algorithms are used in conjunction with signing
  val hashAlgorithms = List("md5", "sha1", "sha224", "sha256", "sha384", "sha512")

  //This list keeps all keys being generated and imported which are used for most subsequent tests
  val pubKeys = new ListBuffer[NkPublicKey]
  val tempAdminPassword = "bla"
  val adminPassword = "super secret"
  val userPassword = "secret too"

  val logRequest: HttpRequest => HttpRequest = { r => logger.debug(r.toString); r }
  val logResponse: HttpResponse => HttpResponse = { r => logger.debug(r.toString); r }
  //private val defaultPipeline = defaultRequest ~> logRequest ~> sendReceive ~> logResponse

  IO(Http) ! Http.HostConnectorSetup(settings.host, settings.port, sslEncryption = settings.tls)

  feature("keyfender tells it's system information") {

    scenario("keyfender system information") {
      val pipeline: HttpRequest => Future[SystemInformationResponse] = sendReceive ~> unmarshal[SystemInformationResponse]
      val responseF = pipeline(Get(s"$apiLocation/system/information"))
      whenReady(responseF) { response =>
        assert(response.vendor === "keyfender")
        assert(response.product === "keyfender")
        assert(response.version.nonEmpty)
      }
    }

    scenario("keyfender's status is up and running") {
      val pipeline: HttpRequest => Future[SimpleResponse] = sendReceive ~> unmarshal[SimpleResponse]
      val responseF = pipeline(Get(s"$apiLocation/system/status"))
      //responseF onComplete {
      //whenReady(responseF) { response =>
     assert(responseF.futureValue.status === "ok")
    }

  }

  feature("Password management") {

    scenario("Any operation requires set password") {
      val pipeline: HttpRequest => Future[HttpResponse] = (
        addCredentials(BasicHttpCredentials("user", ""))
        ~> sendReceive
      )
      val f: Future[HttpResponse] = pipeline(Get(s"$apiLocation/keys"))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    scenario("Set temporary admin password") {
      val request = PasswordChange(tempAdminPassword) //New password
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        addCredentials(BasicHttpCredentials("admin", ""))
        ~> sendReceive
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Put(s"$apiLocation/system/passwords/admin", request))
      val response = Await.result(f, timeout)
      assert(response.status === "success")
    }

    scenario("Changing admin password requires authentication") {
      val request = PasswordChange(tempAdminPassword)
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      val f: Future[HttpResponse] = pipeline(Put(s"$apiLocation/system/passwords/admin", request))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
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
      assert(response.status === "success")
    }

    scenario("Set user password") {
      val request = PasswordChange(userPassword)
      val pipeline: HttpRequest => Future[SimpleResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[SimpleResponse]
      )
      val f: Future[SimpleResponse] = pipeline(Put(s"$apiLocation/system/passwords/user", request))
      val response = Await.result(f, timeout)
      assert(response.status === "success")
    }
  }

  feature("RSA key import") {

    scenario("Key import requires authentication") {
      val keyPair = generateRSACrtKeyPair(1024)
      val request = KeyImport("signing", "RSA", keyPair.privateKey)
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      val f: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    scenario("Key import requires admin authentication") {
      val keyPair = generateRSACrtKeyPair(1024)
      val request = KeyImport("signing", "RSA", keyPair.privateKey)
      val pipeline: HttpRequest => Future[HttpResponse] = (
        addCredentials(BasicHttpCredentials("user", userPassword))
        ~> sendReceive
      )
      val f: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    val purpose = "encryption"
    keyLengths.map{ keyLength =>

      scenario(s"Importing a RSA-$keyLength key for $purpose") {
        //Given(s"A RSA-$keyLength key for $purpose")
        val keyPair = generateRSACrtKeyPair(keyLength)
        val request = KeyImport(purpose, "RSA", keyPair.privateKey)
        //publicKey = keyPair.publicKey :: publicKey

        //When("Key is imported")
        val pipeline: HttpRequest => Future[KeyLocationResponse] = (
          addCredentials(BasicHttpCredentials("admin", adminPassword))
          ~> sendReceive
          ~> unmarshal[KeyLocationResponse]
        )
        val responseF: Future[KeyLocationResponse] = pipeline(Post(s"$apiLocation/keys", request))
        whenReady(responseF) { response =>
          //Then("OK is returned")
          val keyLocation = response.data.location
          assert(keyLocation.nonEmpty)
          assert(response.status === "success")

          //val f = fixture
          //fixture.keyEnvelopes = PublicKeyEnvelope(keyLocation, NkPublicKey(purpose, "RSA", keyPair.publicKey)) :: fixture.keyEnvelopes //Use this key for subsequent tests

          //To allow proper comparison the leading zero of values is dropped.
          val trimmedPubKey = NkPublicRsaKey(dropLeadingZero(keyPair.publicKey.modulus), dropLeadingZero(keyPair.publicKey.publicExponent))
          pubKeys += NkPublicKey(idFromLocation(keyLocation), purpose, "RSA", trimmedPubKey) //Use this key for subsequent tests

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

    scenario(s"Importing a RSA-2048 key with given ID") {
      //Given(s"A RSA-$keyLength key for $purpose")
      val keyLength = 2048
      val id = "myId123"
      val keyPair = generateRSACrtKeyPair(keyLength)
      val request = KeyImportWithId(purpose, "RSA", keyPair.privateKey, id)
      //publicKey = keyPair.publicKey :: publicKey

      //When("Key is imported")
      val pipeline: HttpRequest => Future[KeyLocationResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[KeyLocationResponse]
      )
      val responseF: Future[KeyLocationResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        //Then("OK is returned")
        val keyLocation = response.data.location
        assert(response.status === "success")

        assert( keyLocation.endsWith(id) )
        //val f = fixture
        //fixture.keyEnvelopes = PublicKeyEnvelope(keyLocation, NkPublicKey(purpose, "RSA", keyPair.publicKey)) :: fixture.keyEnvelopes //Use this key for subsequent tests

        //To allow proper comparison the leading zero of values is dropped.
        val trimmedPubKey = NkPublicRsaKey(dropLeadingZero(keyPair.publicKey.modulus), dropLeadingZero(keyPair.publicKey.publicExponent))
        pubKeys += NkPublicKey(idFromLocation(keyLocation), purpose, "RSA", trimmedPubKey) //Use this key for subsequent tests

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

    scenario(s"Importing two keys with same ID fails") {
      //Given(s"A RSA-$keyLength key for $purpose")
      val keyLength = 2048
      val id = "myId123"
      val keyPair = generateRSACrtKeyPair(keyLength)
      val request = KeyImportWithId(purpose, "RSA", keyPair.privateKey, id)

      //When("Key exists")
      val pipeline: HttpRequest => Future[HttpResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        //~> unmarshal[SimpleResponse]
      )
      val responseF: Future[HttpResponse] = pipeline(Get(s"$apiLocation/keys/$id"))
      whenReady(responseF) { response =>
        //Then("OK is returned")
        assert(response.status.intValue === 200)

        //When("Key is imported")
        val pipeline: HttpRequest => Future[JsendResponse] = (
          addCredentials(BasicHttpCredentials("admin", adminPassword))
          ~> sendReceive ~> unmarshalFailure[JsendResponse](400)
        )
        val responseF: Future[JsendResponse] = pipeline(Post(s"$apiLocation/keys", request))
        whenReady(responseF) { response =>
          assert(response.status === "failure")
        }
      }
    }

    scenario(s"Importing a RSA-2048 key with an ID containing slash and umlaut") {
      //Given(s"A RSA-$keyLength key for $purpose")
      val keyLength = 2048
      val id = "project1/Schlüssel2"
      val keyPair = generateRSACrtKeyPair(keyLength)
      val request = KeyImportWithId(purpose, "RSA", keyPair.privateKey, id)
      //publicKey = keyPair.publicKey :: publicKey

      //When("Key is imported")
      val pipeline: HttpRequest => Future[KeyLocationResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[KeyLocationResponse]
      )
      val responseF: Future[KeyLocationResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        //Then("OK is returned")
        val keyLocation = response.data.location
        assert(response.status === "success")

        //The provided keyID contains special characters which should have been encoded by HSM
        import java.net.URLEncoder
        val encodedId: String = URLEncoder.encode(id, "UTF-8")
          .replaceAll("\\+", "%20")
          .replaceAll("\\%21", "!")
          .replaceAll("\\%27", "'")
          .replaceAll("\\%28", "(")
          .replaceAll("\\%29", ")")
          .replaceAll("\\%7E", "~")
        assert( keyLocation.endsWith(encodedId) )

        //val f = fixture
        //fixture.keyEnvelopes = PublicKeyEnvelope(keyLocation, NkPublicKey(purpose, "RSA", keyPair.publicKey)) :: fixture.keyEnvelopes //Use this key for subsequent tests

        //To allow proper comparison the leading zero of values is dropped.
        val trimmedPubKey = NkPublicRsaKey(dropLeadingZero(keyPair.publicKey.modulus), dropLeadingZero(keyPair.publicKey.publicExponent))
        pubKeys += NkPublicKey(idFromLocation(keyLocation), purpose, "RSA", trimmedPubKey) //Use this key for subsequent tests

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

    scenario("Importing unsupported ECC key fails") {
      val keyPair = generateRSACrtKeyPair(2048)
      val request = KeyImport("signing", "ECC", keyPair.privateKey)
      val pipeline: HttpRequest => Future[JsendResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive ~> unmarshalFailure[JsendResponse](400)
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

    scenario("Key generation requires authentication") {
      val request = KeyGeneration("signing", "RSA", 1024)
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      val f: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    scenario("Key generation requires admin authentication") {
      val request = KeyGeneration("signing", "RSA", 1024)
      val pipeline: HttpRequest => Future[HttpResponse] = (
        addCredentials(BasicHttpCredentials("user", userPassword))
        ~> sendReceive
      )
      val f: Future[HttpResponse] = pipeline(Post(s"$apiLocation/keys", request))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    keyLengths.map{ keyLength =>
      scenario(s"Generate RSA-$keyLength key") {
        //Given("Key generation request")
        val request = KeyGeneration("signing", "RSA", keyLength)
        val pipeline: HttpRequest => Future[KeyLocationResponse] = (
          addCredentials(BasicHttpCredentials("admin", adminPassword))
          ~> sendReceive
          ~> unmarshal[KeyLocationResponse]
        )
        //When("Key is generated")
        val responseF: Future[KeyLocationResponse] = pipeline(Post(s"$apiLocation/keys", request))
        whenReady(responseF) { response =>
          val keyLocation = response.data.location
          assert( keyLocation.nonEmpty )
          assert(response.status === "success")

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
            pubKeys += NkPublicKey(idFromLocation(keyLocation), "signing", "RSA", //response.publicKey))
                NkPublicRsaKey(dropLeadingZero(response.data.publicKey.modulus), dropLeadingZero(response.data.publicKey.publicExponent)))
          }
        }
      }
    }

    scenario(s"Generate RSA-2048 key with given ID") {
      //Given("Key generation request")
      val keyLength = 3072
      val id = "1isAnotherId2"
      val request = KeyGenerationWithId("signing", "RSA", keyLength, id)
      val pipeline: HttpRequest => Future[KeyLocationResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[KeyLocationResponse]
      )
      //When("Key is generated")
      val responseF: Future[KeyLocationResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        val keyLocation = response.data.location
        assert( keyLocation.nonEmpty )
        assert(response.status === "success")

        assert( keyLocation.endsWith(id) )

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
          pubKeys += NkPublicKey(idFromLocation(keyLocation), "signing", "RSA", //response.publicKey))
              NkPublicRsaKey(dropLeadingZero(response.data.publicKey.modulus), dropLeadingZero(response.data.publicKey.publicExponent)))
        }
      }
    }

    scenario("Generating unsupported ECC key fails") {
      val request = KeyGeneration("signing", "ECC", 2048)
      val pipeline: HttpRequest => Future[JsendResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshalFailure[JsendResponse](400)
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

    scenario("List existing keys requires (user) authentication") {
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      val f: Future[HttpResponse] = pipeline(Get(s"$apiLocation/keys"))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    scenario("List existing keys and check for given keys") {
      pubKeys.map{ pubKey =>
        info("Check for key " + pubKey.id)
        val pipeline: HttpRequest => Future[KeyListResponse] = (
          addCredentials(BasicHttpCredentials("user", userPassword))
          ~> sendReceive
          ~> unmarshal[KeyListResponse]
        )
        val responseF: Future[KeyListResponse] = pipeline(Get(s"$apiLocation/keys"))
        whenReady(responseF) { response =>
          assert(response.status === "success")
          assert(response.data.length > 0)
          assert(response.data.exists(x => x.location === "/api/v0/keys/"+pubKey.id))
        }
      }
    }

    scenario("Retrieving public key requires (user) authentication") {
        val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
        val f: Future[HttpResponse] = pipeline(Get(host + "/api/v0/keys/" + pubKeys.head.id + "/public.pem"))
        val response = Await.result(f, timeout)
        assert(response.status.intValue === 401) // unauthorized
      }

    scenario("Retrieve public keys in PEM format") {
      pubKeys.map{ pubKey =>
        info("Check for key " + pubKey.id)
        val pipeline: HttpRequest => Future[HttpResponse] = (
          addCredentials(BasicHttpCredentials("user", userPassword))
          ~> sendReceive
        )
        val responseF: Future[HttpResponse] = pipeline(Get(host + "/api/v0/keys/" + pubKey.id + "/public.pem"))
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

  feature("Decrypt message") {

    scenario("Decryption requires (user) authentication") {
      val message = "secure your digital life".getBytes
      val request = DecryptRequest( encrypt(message, "RSA/NONE/NoPadding", pubKeys.head.publicKey) )
      val location = "/api/v0/keys/" + pubKeys.head.id
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      val f: Future[HttpResponse] = pipeline(Post(s"$host$location/actions/decrypt", request))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    scenario("Decrypt message (RSA, no padding)") {
      pubKeys.filter(x => x.purpose == "encryption").map{ pubKey =>
        decryptionTest(pubKey, "", "RSA/NONE/NoPadding")
      }
    }

    scenario("Decrypt message (RSA, PKCS#1 padding)") {
      pubKeys.filter(x => x.purpose == "encryption").map{ pubKey =>
        decryptionTest(pubKey, "/pkcs1", "RSA/NONE/PKCS1Padding")
      }
    }
    hashAlgorithms.map{ hash =>
      scenario("Decrypt message (RSA, OAEP, "+hash+")") {
        pubKeys.filter(x => x.purpose == "encryption").map{ pubKey =>
          if( !(pubKey.publicKey.modulus.length == 128 && hash == "sha512")) //Exception because SHA512 is too large for 1024 bit RSA keys
            decryptionTest(pubKey, "/oaep/"+hash, "RSA/None/OAEPWith"+hash+"AndMGF1Padding")
        }
      }
    }

    ignore("Decryption fails with signing key") {
      val message = "Secure your digital life".getBytes
      pubKeys.filter(x => x.purpose != "encryption").map{ pubKey =>
        val keyLength = pubKey.publicKey.modulus.length*8
        info("RSA-" + keyLength + " key for " + pubKey.purpose)

        val request = DecryptRequest( encrypt(message, "RSA/NONE/NoPadding", pubKey.publicKey) )
        val pipeline = (
          addCredentials(BasicHttpCredentials("user", userPassword))
          ~> sendReceive //~> unmarshal[JsendResponse]
        )
        val location = "/api/v0/keys/" + pubKey.id
        val responseF: Future[HttpResponse] = pipeline(Post(s"$host$location/actions/decrypt", request))
        whenReady(responseF) { response =>
          assert(response.status.intValue === 405 )
          //assert((response ~> unmarshal[JsendResponse]).status === "error")
        }
      }
    }
  }

  feature("Sign message") {

    scenario("Signing requires (user) authentication") {
      val cleartextMessage = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet."
      val request = SignRequest(hash(cleartextMessage, "sha256"))
      val location = "/api/v0/keys/" + pubKeys.head.id
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      val f: Future[HttpResponse] = pipeline(Post(s"$host$location/actions/pkcs1/sign", request))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    scenario("Sign message (RSA, PKCS#1 padding)") {
      pubKeys.filter(x => x.purpose == "signing").map{ pubKey =>
        signatureTest(pubKey, "/pkcs1", "NONEwithRSA")
      }
    }

    hashAlgorithms.filter(x => x != "md5").map{ hash => //MD5 doesn't work with SUN and BouncyCastle Security Provider
      scenario("Sign message (RSA, PSS, "+hash+")") {
        pubKeys.filter(x => x.purpose == "signing").map{ pubKey =>
          if( !(pubKey.publicKey.modulus.length == 128 && hash == "sha512")) //Exception because SHA512 is too large for 1024 bit RSA keys
            signatureTest(pubKey, "/pss/"+hash, hash+"withRSAandMGF1") //similar to "RSASSA-PSS"
        }
      }
    }

    ignore("Signing fails with encryption key") {
      val message = "Secure your digital life".getBytes
      pubKeys.filter(x => x.purpose != "signing").map{ pubKey =>
        val keyLength = pubKey.publicKey.modulus.length*8
        info("RSA-" + keyLength + " key for " + pubKey.purpose)

        val request = SignRequest(message)
        val pipeline = (
          addCredentials(BasicHttpCredentials("user", userPassword))
          ~> sendReceive //~> unmarshal[JsendResponse]
        )
        val location = "/api/v0/keys/" + pubKey.id
        val responseF: Future[HttpResponse] = pipeline(Post(s"$host$location/actions/pkcs1/sign", request))
        whenReady(responseF) { response =>
          assert(response.status.intValue === 405 )
          //assert((response ~> unmarshal[JsendResponse]).status === "error")
        }
      }
    }

    ignore("Client performs SHA256 hash and keyfender signs the hash") {
      val cleartextMessage = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet."
      val messageHash = hash(cleartextMessage, "SHA256")
      val request = SignRequest(messageHash)
      val pipeline = (
        addCredentials(BasicHttpCredentials("user", userPassword))
        ~> sendReceive ~> unmarshal[SignResponse]
      )
      val pubKey = pubKeys.head
      val location = "/api/v0/keys/" + pubKey.id
      val responseF: Future[SignResponse] = pipeline(Post(s"$host$location/actions/pss/sign", request))
      whenReady(responseF) { response =>
        assert(response.status === "success")
        assert(verifySignature(messageHash, response.data.signedMessage, pubKey.publicKey, "NONEwithRSAandMGF1"))
      }
    }
  }

  feature("Overwrite existing keys") {
    scenario("Overwrite every second key") {
      Iterator.from(1, 2).takeWhile(_ < pubKeys.size).map(pubKeys(_))foreach{ pubKey =>
        info("Overwriting key " + pubKey.id)

        //Given(s"A new RSA-$keyLength key for $purpose")
        val keyPair = generateRSACrtKeyPair(2048) // fixed size results in changing key size
        val request = KeyImport("encryption", "RSA", keyPair.privateKey) // fixed purpose results in overwriting purpose

        //When("Key is imported")
        val pipeline: HttpRequest => Future[HttpResponse] = (
          addCredentials(BasicHttpCredentials("admin", adminPassword))
          ~> sendReceive
        )

        val responseF: Future[HttpResponse] = pipeline(Put(host + "/api/v0/keys/" + pubKey.id, request))
        whenReady(responseF) { response =>
          assert(response.status.intValue === 200) // OK

          //To allow proper comparison the leading zero of values is dropped.
          val trimmedPubKey = NkPublicRsaKey(dropLeadingZero(keyPair.publicKey.modulus), dropLeadingZero(keyPair.publicKey.publicExponent))

          val pipeline: HttpRequest => Future[NkPublicKeyResponse] = (
            addCredentials(BasicHttpCredentials("user", userPassword))
            ~> sendReceive
            ~> unmarshal[NkPublicKeyResponse]
          )

          val responseF: Future[NkPublicKeyResponse] = pipeline(Get(host + "/api/v0/keys/" + pubKey.id))
          whenReady(responseF) { response =>
            assert(response.status === "success")
            assert(response.data.publicKey === trimmedPubKey )
          }
        }
      }
    }
  }

  feature("Delete RSA key") {

    scenario("Deleting key requires authentication") {
      val location = "/api/v0/keys/" + pubKeys.head.id
      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
      val f: Future[HttpResponse] = pipeline(Delete(s"$host$location"))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    scenario("Deleting key requires admin authentication") {
      val location = "/api/v0/keys/" + pubKeys.head.id
      val pipeline: HttpRequest => Future[HttpResponse] = (
        addCredentials(BasicHttpCredentials("user", userPassword))
        ~> sendReceive
      )
      val f: Future[HttpResponse] = pipeline(Delete(s"$host$location"))
      val response = Await.result(f, timeout)
      assert(response.status.intValue === 401) // unauthorized
    }

    scenario("Delete RSA key") {
      pubKeys.map{ pubKey =>
        val keyLength = pubKey.publicKey.modulus.length*8
        info(s"A RSA key with $keyLength bit in keyfender")
        val pipeline: HttpRequest => Future[HttpResponse] = (
          addCredentials(BasicHttpCredentials("admin", adminPassword))
          ~> sendReceive
        )

        //When("Key is deleted")
        val location = "/api/v0/keys/" + pubKey.id
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
    ignore("Sign 100 messages (RSA, PKCS#1 padding)") {
      val rounds = 100

      //Generate key for this performance test
      val request = KeyGeneration("signing", "RSA", 2048)
      val pipeline: HttpRequest => Future[KeyLocationResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[KeyLocationResponse]
      )
      val responseF: Future[KeyLocationResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        assert(response.status === "success")
        val keyLocation = response.data.location
        assert( keyLocation.nonEmpty )

        //Create 100 requests before executing those
        val requests = (0 to rounds).map(counter => {
          SignRequest( s"Secure your digital life $counter".getBytes )
        })

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
    }

    ignore("Decrypt 100 messages (RSA, PKCS#1 padding)") {
      val rounds = 100

      //Generate key for this performance test
      val keyPair = generateRSACrtKeyPair(2048)
      val publicKey = keyPair.publicKey
      val request = KeyImport("signing", "RSA", keyPair.privateKey)
      val pipeline: HttpRequest => Future[KeyLocationResponse] = (
        addCredentials(BasicHttpCredentials("admin", adminPassword))
        ~> sendReceive
        ~> unmarshal[KeyLocationResponse]
      )
      val responseF: Future[KeyLocationResponse] = pipeline(Post(s"$apiLocation/keys", request))
      whenReady(responseF) { response =>
        assert(response.status === "success")
        val keyLocation = response.data.location
        assert( keyLocation.nonEmpty )

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
          assert(response.status == "success")
          assert(trimPrefix(response.data.decrypted) === messages(i) )
        }}
      }
    }
  }

  scenario("Void admin password as preparation for next test run.") {
    val request = PasswordChange("")
    val pipeline: HttpRequest => Future[SimpleResponse] = (
      addCredentials(BasicHttpCredentials("admin", adminPassword))
      ~> sendReceive
      ~> unmarshal[SimpleResponse]
    )
    val f: Future[SimpleResponse] = pipeline(Put(s"$apiLocation/system/passwords/admin", request))
    val response = Await.result(f, timeout)
  }

  def decryptionTest(pubKey: NkPublicKey, parameter: String, cipherSuite: String) = {
    val keyLength = pubKey.publicKey.modulus.length*8
    info(s"A RSA key with $keyLength bit")

    //Given("An encrypted message")
    val message = "secure your digital life".getBytes
    val request = DecryptRequest( encrypt(message, cipherSuite, pubKey.publicKey) )

    //When("Message is decrypted")
    val pipeline = (
      addCredentials(BasicHttpCredentials("user", userPassword))
      ~> sendReceive
      ~> unmarshal[DecryptResponse]
    )
    val id = pubKey.id
    val responseF: Future[DecryptResponse] = pipeline(Post(s"$host/api/v0/keys/$id/actions$parameter/decrypt", request))

    //Then("Decrypted message is identical")
    whenReady(responseF) { response =>
      assert(response.status === "success")
      assert(trimPrefix(response.data.decrypted) === message)
    }
  }

  def signatureTest(pubKey: NkPublicKey, parameter: String, cipherSuite: String) = {
    val keyLength = pubKey.publicKey.modulus.length*8
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
    val id = pubKey.id
    val responseF: Future[SignResponse] = pipeline(Post(s"$host/api/v0/keys/$id/actions$parameter/sign", request))

    //Then("Signature is correct")
    whenReady(responseF) { response =>
      assert(response.status === "success")
      assert(verifySignature(message, response.data.signedMessage, pubKey.publicKey, cipherSuite))
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
    info("Elapsed time: " + (t1 - t0) + "ns = " + (t1 - t0)/1000000 + "ms = " + (t1 - t0)/1000000000 + "s")
    info("Time per operation: " + (t1 - t0)/100 + "ns = " + (t1 - t0)/100000000 + "ms")
    info(1.0/((t1 - t0)/1000000000.0)*100.0 + " operations per second")
    result
  }

  def idFromLocation(location: String): String = {
    import java.lang.String
    val start: Int = location.lastIndexOf("/")
    location.substring(start+1)
  }

  def unmarshalFailure[T: FromResponseUnmarshaller](code: Int): HttpResponse ⇒ T = {
    import spray.httpx.unmarshalling._
    import spray.httpx.{PipelineException, UnsuccessfulResponseException}
    response ⇒
      assert(response.status.intValue === code)
      response.as[T] match {
        case Right(value) ⇒ value
        case Left(error: MalformedContent) ⇒
          throw new PipelineException(error.errorMessage, error.cause.orNull)
        case Left(error) ⇒ throw new PipelineException(error.toString)
      }
  }
}
