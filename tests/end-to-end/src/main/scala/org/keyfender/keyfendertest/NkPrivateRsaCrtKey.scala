package org.keyfender.keyfendertest

import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.KeyFactory
import java.math.BigInteger
import KeyfenderProtocol._

case class NkPrivateRsaCrtKey(
  modulus: Seq[Byte],
  publicExponent: Seq[Byte],
  privateExponent: Seq[Byte],
  primeP: Seq[Byte],
  primeQ: Seq[Byte],
  primeExponentP: Seq[Byte],
  primeExponentQ: Seq[Byte],
  crtCoefficient: Seq[Byte]) {
  
  def javaPrivateKey: RSAPrivateCrtKey = {
  	val privateKeySpec: RSAPrivateCrtKeySpec = new RSAPrivateCrtKeySpec(
  	    new BigInteger(modulus.toArray), 
  	    new BigInteger(publicExponent.toArray), 
  	    new BigInteger(privateExponent.toArray),
  	    new BigInteger(primeP.toArray),
  	    new BigInteger(primeQ.toArray),
  	    new BigInteger(primeExponentP.toArray),
  	    new BigInteger(primeExponentQ.toArray),
  	    new BigInteger(crtCoefficient.toArray))
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePrivate(privateKeySpec).asInstanceOf[RSAPrivateCrtKey]
  }
  
  def publicKey: NkPublicRsaKey = {
    NkPublicRsaKey(modulus, publicExponent)
  }

  def privateKey: NkPrivateRsaKey = {
    NkPrivateRsaKey(primeP, primeQ, publicExponent)
  }
}

object NkPrivateRsaCrtKey {
  def apply(priv: RSAPrivateCrtKey) = {
    new NkPrivateRsaCrtKey(
        priv.getModulus.toByteArray,
        priv.getPublicExponent.toByteArray,
        priv.getPrivateExponent.toByteArray,
        priv.getPrimeP.toByteArray,
        priv.getPrimeQ.toByteArray,
        priv.getPrimeExponentP.toByteArray,
        priv.getPrimeExponentQ.toByteArray,
        priv.getCrtCoefficient.toByteArray
     )
  }

}
