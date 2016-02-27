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

val add : storage -> priv -> int Lwt.t

val put : storage -> int -> priv -> bool Lwt.t

val del : storage -> int -> bool Lwt.t

val get : storage -> int -> pub option Lwt.t

val get_all : storage -> (int * pub) list Lwt.t
