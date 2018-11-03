module type SexpConvertable = sig
  type t
  val t_of_sexp : Sexplib.Sexp.t -> t
  val sexp_of_t : t -> Sexplib.Sexp.t
end

module type Encryptor = sig
  val encrypt : string -> string
  val decrypt : string -> string
end

module type EncKey = sig
  val key : Cstruct.t
end

module type IrminConf = sig
  val config : Irmin.config
end

module type EtcdConf = sig
  val host : string
end

module type Keyring = sig
  type pub
  (** public key representation *)

  type 'a result =
    | Ok of 'a
    | Failure of Yojson.Basic.json

  module Padding : sig
    type t =
      | None
      | PKCS1
      | OAEP of Nocrypto.Hash.hash
      | PSS of Nocrypto.Hash.hash
  end

  val json_of_pub : string -> pub -> Yojson.Basic.json

  val pem_of_pub : pub -> string

  val add : key:Yojson.Basic.json -> string result Lwt.t
  (** add key to storage *)

  val put : id:string -> key:Yojson.Basic.json -> bool result Lwt.t
  (** update a key in storage *)

  val del : id:string -> bool Lwt.t
  (** delete a key from storage *)

  val get : id:string -> pub option Lwt.t
  (** retreive a public key from storage *)

  val get_all : unit -> string list Lwt.t
  (** retrieve all key ids from storage *)

  val decrypt : id:string -> padding:Padding.t ->
    data:Yojson.Basic.json -> Yojson.Basic.json result Lwt.t

  val sign : id:string -> padding:Padding.t ->
    data:Yojson.Basic.json -> Yojson.Basic.json result Lwt.t
end (* Keyring *)

module type KV_Store = sig
  type v
  val get : string -> v option Lwt.t
  val get_all : unit -> string list Lwt.t
  val add : string option -> v -> string Lwt.t
  val put : string -> v -> bool Lwt.t
  val delete : string -> bool Lwt.t
end

module type KV_Maker = functor (Val : SexpConvertable) -> 
  KV_Store with type v = Val.t
