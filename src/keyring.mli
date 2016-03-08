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
(** create a storage *)

val add : storage -> priv -> string Lwt.t
(** add key to storage *)

val put : storage -> string -> priv -> bool Lwt.t
(** update a key in storage *)

val del : storage -> string -> bool Lwt.t
(** delete a key from storage *)

val get : storage -> string -> pub option Lwt.t
(** retreive a public key from storage *)

val get_all : storage -> (string * pub) list Lwt.t
(** retrieve all public keys from storage *)

val decrypt : storage -> string -> Yojson.Basic.json -> Yojson.Basic.json Lwt.t

val pkcs1_decrypt : storage -> string -> Yojson.Basic.json -> Yojson.Basic.json Lwt.t

val pkcs1_sign : storage -> string -> Yojson.Basic.json -> Yojson.Basic.json Lwt.t
