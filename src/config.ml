open Mirage

let stack = generic_stackv4 default_network
let data = generic_kv_ro "htdocs"

(* set ~tls to false to get a plain-http server *)
let con = conduit_direct ~tls:true stack
let https_srv = Mirage.http_server @@ con

let http_port =
  let doc = Key.Arg.info ~doc:"Listening HTTP port." ["http"] in
  Key.abstract Key.(create "http_port" Arg.(opt int 8080 doc))

(* some defaults are included here, but you can replace them with your own. *)
let certs = generic_kv_ro "tls"

let https_port =
  let doc = Key.Arg.info ~doc:"Listening HTTPS port." ["https"] in
  Key.abstract Key.(create "https_port" Arg.(opt int 4433 doc))

let admin_password =
  let doc = Key.Arg.info ~doc:"Initial admin password." ["password"] in
  Key.abstract Key.(create "admin_password" 
    Arg.(opt ~stage:`Both string "" doc))

let irmin_url =
  let doc = Key.Arg.info ~doc:"URL of remote Irmin server." ["irmin-url"] in
  Key.abstract Key.(create "irmin_url"
    Arg.(opt ~stage:`Both string "" doc))

let nameserver =
  let doc = Key.Arg.info ~doc:"Address of DNS server." ["nameserver"] in
  Key.abstract Key.(create "nameserver"
    Arg.(opt ~stage:`Both string "8.8.8.8" doc))

let main =
  let packages = [
    package "uri";
    package "magic-mime";
    package "yojson";
    package "webmachine";
    package "irmin-mem";
    package "irmin-http";
    package "ppx_sexp_conv";
  ] in
  let keys = [ http_port; https_port; admin_password; irmin_url; nameserver ] in
  foreign
    ~packages ~keys
    "Hsm.HTTPS" (pclock @-> kv_ro @-> kv_ro @-> http @-> stackv4 @-> conduit
      @-> job)

let () =
  register "keyfender" [main $ default_posix_clock $ data $ certs $ https_srv $
    stack $ con]
