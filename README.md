[![Build Status](https://travis-ci.org/Nitrokey/nitrokey-nethsm.svg?branch=master)](https://travis-ci.org/Nitrokey/nitrokey-nethsm)

On Unix, do:

```
$ make configure
$ make depend
$ make build
$ make run
```

This will run the HSM on localhost on port 8080, so you should be
able to access [http://localhost:8080/api/v0](http://localhost:8080/api/v0).

For debug output start NetHSM by executing `MIRAGE_LOGS=debug ./src/nethsm`.

For a Xen DHCP kernel, do:

```
$ DHCP=true MODE=xen NET=direct make configure
$ make build
```

edit `nethsm.xl` to add a VIF, e.g. via:

```
vif = ['bridge=xenbr0']
```

And then run the VM via `xl create -c nethsm.xl`

# API

The API is described in "docs" folder. You can view it in the browser [here](https://www.nitrokey.com/sites/default/files/nethsm/api.html).

# Tutorial

First, let's see what we have here:

```
$ curl -i -w "\n" -X GET localhost:8080/api/v0/system/information
HTTP/1.1 200 OK
{"vendor":"Nitrokey","product":"NetHSM","version":"0.1"}
```

See what the device's status is:

```
$ curl -i -w "\n" -X GET localhost:8080/api/v0/system/status

HTTP/1.1 200 OK
{"status":"ok"}
```

Does it has some keys on it?

```
$ curl -i -w "\n" -X GET localhost:8080/api/v0/keys

HTTP/1.1 401 Unauthorized
```

Ohh, NetHSM seems to have access control. In fact is has an Admin password and a User password. The Admin password is used to authenticate any kind of changes of the system, settings and keys. The User password is required to authenticate the usage of NetHSM without any modification.

Before you can do anything with the system, the Admin password needs to be defined first. It doesn't has a default value.

```
$ curl -i -w "\n" -X PUT localhost:8080/api/v0/system/passwords/admin -H "content-type: application/json" -d '{ newPassword: "secret" }'

HTTP/1.1 200 OK
{ "status": "success" }
```

If you want to change the Admin password again, you need to authenticate:

```
$ curl -i -w "\n" -X PUT http://admin:secret@localhost:8080/api/v0/system/passwords/admin -H "content-type: application/json" -d '{ newPassword: "supersecret" }'

HTTP/1.1 200 OK
{ "status": "success" }
```


Define a User password:

```
$ curl -i -w "\n" -X PUT http://admin:supersecret@localhost:8080/api/v0/system/passwords/user -H "content-type: application/json" -d '{ newPassword: "usersecret" }'

HTTP/1.1 200 OK
{ "status": "success" }
```

You can generate RSA keys:

```
$ curl -i -w "\n" -X POST http://admin:supersecret@localhost:8080/api/v0/keys -H "content-type: application/json" -d '{"purpose":"signing", "algorithm":"RSA", "length":4096}'

HTTP/1.1 303 See Other
location: /api/v0/keys/cphQSDP1n2q4BxnPVI4y
{ "status": "success" }
```

Here you got a 303 redirect to the newly generated key. The last part of the URL is the key ID: cphQSDP1n2q4BxnPVI4y


Instead of dealing with generated key IDs, you can specify the key ID yourself:

```
$ curl -i -w "\n" -X POST http://admin:supersecret@localhost:8080/api/v0/keys -H "content-type: application/json" -d '{"purpose":"authentication", "algorithm":"RSA", "length":2048, "id":"myKey"}'

HTTP/1.1 303 See Other
location: /api/v0/keys/myAutKey
{ "status": "success" }
```

You can also import existing keys:

```
$ curl -i -w "\n" -X POST http://admin:supersecret@localhost:8080/api/v0/keys -d '{"purpose":"encryption", "algorithm":"RSA", "privateKey":{"publicExponent":"AQAB","primeP":"4P7TWJety3bZ47tp_WnB8BEbBX9kd_ONa6bOnPd2nxfXmLl1W61yQbZAw8bTReBfYsre8wYe8jVSs-nNGgR19-FPnXMg8xAgFrdcVvfj8OverK-q3MJhZTT2X-ZAhN5H-wWf_xXPJPMtPsPXXs914fU7WchZoBIVcarQq0eGHMM=","primeQ":"x8QUQ4aPrh33oBip_PBpzRHMRtg4isr8CwXQq8ijSd8dvYjaC8mTYPB0Nytsi047XjXBLq0HyvpjxpcVWYBzqrPKFFcafTdk80SQNtD5EUyGy_rFRbowDaG5UoMVSL1VrJLx6xI8OToUP2J1ZiuZG0I-Ms2YQcanZzYRANppLYM="}}'

HTTP/1.1 303 See Other
location: /api/v0/keys/kfG8H2z2cddUMXeiK5Ky
{ "status": "success" }
```

You can overwrite an existing key with PUT or delete with DELETE.

Now we are going to perform key operations. For this we don't need the Admin password anymore but can use the User password instead.
What we have got?

```
$ curl -i -w "\n" -X GET http://user:usersecret@localhost:8080/api/v0/keys

HTTP/1.1 200 OK
content-length: 199
content-type: application/json
vary: Accept, Accept-Encoding, Accept-Charset, Accept-Language

{
  "status": "success",
  "data": [
    { "location": "/api/v0/keys/cphQSDP1n2q4BxnPVI4y" },
    { "location": "/api/v0/keys/kfG8H2z2cddUMXeiK5Ky" },
    { "location": "/api/v0/keys/myKey" }
  ]
}
```

Here is how you get a public key:
```
$ curl -i -w "\n" -X GET http://user:usersecret@localhost:8080/api/v0/keys/kfG8H2z2cddUMXeiK5Ky
HTTP/1.1 200 OK
content-length: 558
content-type: application/json
vary: Accept, Accept-Encoding, Accept-Charset, Accept-Language

{
  "status": "success",
  "data": {
    "id": "kfG8H2z2cddUMXeiK5Ky",
    "purpose": "encryption",
    "algorithm": "RSA",
    "publicKey": {
      "modulus":
        "r5JrMu80IEJoyM-9utzBs64Her9-VkjYhTU9a5ZrQ0zbECFYpdcTScRrWkZHy0Of6OLXumHHK_Krikmq1m53iw88iTVB_Up8oREkZt2szWifJlAVse9vfzERC_VmIFVqqZgmY1JopygVJ5_MMniOe8fN3iZAf-33ZB1aL14f0Y4m6xGXSN8er_q1yxevWy5oUVyF8Zl7r3ATERAX_9lsuLTZN9tAEBFqq4naH9mSsEsyRljybSuhX411CWUE4cj8JXf9qKumoN7duYNTjipSZqLauJ56txn5zTKDMGKvpcxB5jlQ_0ltVcGEayIjkXhJFR_dM2uwG4cQSmC4Bqn-yQ==",
      "publicExponent": "AQAB"
    }
  }
}
```

You can get it also in PEM format:

```
$ curl -i -w "\n" -X GET http://user:usersecret@localhost:8080/api/v0/keys/kfG8H2z2cddUMXeiK5Ky/public.pem

HTTP/1.1 200 OK
content-length: 451
content-type: application/x-pem-file
vary: Accept, Accept-Encoding, Accept-Charset, Accept-Language

-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAr5JrMu80IEJoyM+9utzB
s64Her9+VkjYhTU9a5ZrQ0zbECFYpdcTScRrWkZHy0Of6OLXumHHK/Krikmq1m53
iw88iTVB/Up8oREkZt2szWifJlAVse9vfzERC/VmIFVqqZgmY1JopygVJ5/MMniO
e8fN3iZAf+33ZB1aL14f0Y4m6xGXSN8er/q1yxevWy5oUVyF8Zl7r3ATERAX/9ls
uLTZN9tAEBFqq4naH9mSsEsyRljybSuhX411CWUE4cj8JXf9qKumoN7duYNTjipS
ZqLauJ56txn5zTKDMGKvpcxB5jlQ/0ltVcGEayIjkXhJFR/dM2uwG4cQSmC4Bqn+
yQIDAQAB
-----END PUBLIC KEY-----
```

With each key you can execute decrypt and signing operations (Technical restriction to the designated key purpose is not enforced yet.) Signing can invoke hashing, or you send a hash instead.

```
$ curl -i -w "\n" -X POST -d '{"message":"DOTvDL7e547MJ5tTWqjU5W3-wDFFh0f-g4GHbdgl7iPh6wQe53JV25nxDWgEi3HJcw5YkoBGIbj1XfRbTZbsI77lfIK_lhpf5XVqeKrU0YCRPYDZ2qDFdJyMajyjDieUwTmyxLdrJ_UrwdyFtNPQ27XvjUUF71DLTNMrbKnRNeqVoAWy3PK3Asqo62DRAwLvwRuuz6UhmoDNdJdVzHCi8KJdNQHI5Q8Nhn2SAwVO85IRceOrzIoU00l2QmR0WGNtTwli1lWqfvtE21wExA9ys7mqvJpUCUzPamlsESBveh7c3FboTkekUzZlB6YOUhoWmaV8gxaMBzRFKqKBulbJ8Q=="}' http://user:usersecret@localhost:8080/api/v0/keys/myKey/actions/pkcs1/sign
```

Decrypting data is similarly easy:

```
$ curl -i -w "\n" -X POST -d '{"encrypted":"DOTvDL7e547MJ5tTWqjU5W3-wDFFh0f-g4GHbdgl7iPh6wQe53JV25nxDWgEi3HJcw5YkoBGIbj1XfRbTZbsI77lfIK_lhpf5XVqeKrU0YCRPYDZ2qDFdJyMajyjDieUwTmyxLdrJ_UrwdyFtNPQ27XvjUUF71DLTNMrbKnRNeqVoAWy3PK3Asqo62DRAwLvwRuuz6UhmoDNdJdVzHCi8KJdNQHI5Q8Nhn2SAwVO85IRceOrzIoU00l2QmR0WGNtTwli1lWqfvtE21wExA9ys7mqvJpUCUzPamlsESBveh7c3FboTkekUzZlB6YOUhoWmaV8gxaMBzRFKqKBulbJ8Q=="}' http://user:usersecret@localhost:8080/api/v0/keys/myKey/actions/decrypt
```

Available key actions:
* decrypt
* pkcs1/decrypt
* oaep/md5/decrypt
* oaep/sha1/decrypt
* oaep/sha224/decrypt
* oaep/sha256/decrypt
* oaep/sha384/decrypt
* oaep/sha512/decrypt
* pkcs1/sign
* pss/sha1/sign
* pss/sha224/sign
* pss/sha256/sign
* pss/sha384/sign
* pss/sha512/sign
