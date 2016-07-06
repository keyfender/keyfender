open Mirage

(* Use `FS` to set the underlying filesystem:
   FS=crunch (or nothing): use static filesystem via crunch
   FS=fat: use FAT and block device (run ./make-fat-images.sh)
 *)

let address addr nm gw =
  let f = Ipaddr.V4.of_string_exn in
  { address = f addr ; netmask = f nm ; gateways = [f gw] }

let static_ip_conf = address "192.168.2.5" "255.255.255.0" "192.168.2.1"

let mode =
  try match String.lowercase (Unix.getenv "FS") with
    | "fat"     -> `Fat
    | "archive" -> `Archive
    | _         -> `Crunch
  with Not_found ->
    `Crunch

let fat_ro dir =
  kv_ro_of_fs (fat_of_files ~dir ())

let fs = match mode with
  | `Fat    -> fat_ro "./htdocs"
  | `Crunch -> crunch "./htdocs"
  | `Archive -> archive_of_files ~dir:"./htdocs" ()

let net =
  try match Sys.getenv "NET" with
    | "direct" -> `Direct
    | "socket" -> `Socket
    | _        -> `Direct
  with Not_found -> `Direct

let dhcp =
  try match Sys.getenv "DHCP" with
    | "" -> false
    | _  -> true
  with Not_found -> false

let stack console =
  match net, dhcp with
  | `Direct, true  -> direct_stackv4_with_dhcp console tap0
  | `Direct, false -> direct_stackv4_with_static_ipv4 console tap0 static_ip_conf
  | `Socket, _     -> socket_stackv4 console [Ipaddr.V4.any]

let http_srv = http_server (conduit_direct ~tls:true (stack default_console))

let main =
  foreign "Hsmdispatch.Main" (console @-> kv_ro @-> http @-> job)

let () =
  add_to_ocamlfind_libraries ["re.str"; "magic-mime"; "webmachine"; "yojson"];
  add_to_opam_packages ["re"; "magic-mime"; "webmachine"; "yojson"];

  register "nethsm" [
    main $ default_console $ fs $ http_srv
  ]
