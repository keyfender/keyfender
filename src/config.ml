open Mirage

let stack = generic_stackv4 default_network
let data = generic_kv_ro "htdocs"
(* set ~tls to false to get a plain-http server *)
let https_srv = http_server @@ conduit_direct ~tls:true stack

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
  Key.abstract Key.(create "admin_password" Arg.(opt ~stage:`Both string "" doc))

let main =
  let packages = [
    package "uri";
    package "magic-mime";
    package "yojson";
    (* https://github.com/inhabitedtype/ocaml-webmachine/issues/73 *)
    package ~min:"0.3.2" ~max:"0.4.0" "webmachine";
  ] in
  let keys = [ http_port; https_port; admin_password ] in
  foreign
    ~packages ~keys
    "Hsm.HTTPS" (pclock @-> kv_ro @-> kv_ro @-> http @-> job)

let () =
  register "keyfender" [main $ default_posix_clock $ data $ certs $ https_srv]
