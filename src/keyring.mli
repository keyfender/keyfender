type priv
(** private key representation *)

type pub
(** public key representation *)

val json_of_pub : pub -> Yojson.Basic.json

val priv_of_json : Yojson.Basic.json -> priv

val pem_of_pub : pub -> string

type storage
(** storage for keys *)

val create : unit -> storage

val add : storage -> priv -> string Lwt.t

val put : storage -> string -> priv -> bool Lwt.t

val del : storage -> string -> bool Lwt.t

val get : storage -> string -> pub option Lwt.t

val get_all : storage -> (string * pub) list Lwt.t

val decrypt : storage -> string -> Yojson.Basic.json -> Yojson.Basic.json Lwt.t
