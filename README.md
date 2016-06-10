[![Build Status](https://travis-ci.org/Nitrokey/nitrokey-nethsm.svg?branch=master)](https://travis-ci.org/Nitrokey/nitrokey-nethsm)

On Unix, do:

```
$ make configure
$ make depend
$ make build
$ make run
```

This will run the HSM on localhost on port 8080, so you should be
able to access [http://localhost:8080/api/v1](http://localhost:8080/api/v1).

For DEBUG output start NetHSM by executing `DEBUG=1 ./src/mir-nethsm`.

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

The API is described in "docs" folder. The HTML is generate from the RAML file. To update follow these steps:

1. sudo npm i -g git+https://git@github.com/raml2html/raml2html.git\#raml1.0
2. raml2html -i nethsm-api.raml -o nethsm-api.html
