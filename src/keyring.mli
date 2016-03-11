type pub
(** public key representation *)

module Padding : sig
  type t =
    | None
    | PKCS1
    (* | OAEP *)
    (* | PSS *)
end

val json_of_pub : pub -> Yojson.Basic.json

val pem_of_pub : pub -> string

type storage
(** storage for keys *)

val create : unit -> storage
(** create a storage *)

val add : storage -> key:Yojson.Basic.json -> string Lwt.t
(** add key to storage *)

val put : storage -> id:string -> key:Yojson.Basic.json -> bool Lwt.t
(** update a key in storage *)

val del : storage -> id:string -> bool Lwt.t
(** delete a key from storage *)

val get : storage -> id:string -> pub option Lwt.t
(** retreive a public key from storage *)

val get_all : storage -> (string * pub) list Lwt.t
(** retrieve all public keys from storage *)

val decrypt : storage -> id:string -> padding:Padding.t ->
  data:Yojson.Basic.json -> Yojson.Basic.json Lwt.t

val sign : storage -> id:string -> padding:Padding.t ->
  data:Yojson.Basic.json -> Yojson.Basic.json Lwt.t
