(* Database interface to Irmin *)

open Lwt.Infix
open Util

module Make
    (KV: Irmin.KV_MAKER)
    (Enc: S.Encryptor)
    (Conf: S.IrminConf)
= functor (Val: S.SexpConvertable) -> struct
  type v = Val.t 
  let config = Conf.config
  module Storage = KV(Contents.EncryptedSexp(Val)(Enc))
  let info _ = Irmin.Info.none

  let db_lwt =
    Random.init @@ Nocrypto.Rng.Int.gen_bits 32;
    Storage.Repo.v config >>= Storage.master

  (* let id  = ref 0 *)
  let rec new_id () =
    db_lwt >>= fun db ->
    let n = 20 in
    let alphanum =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" in
    let len = String.length alphanum in
    let id = Bytes.create n in
    for i=0 to pred n do
      Bytes.set id i alphanum.[Random.int len]
    done;
    let id' = Bytes.to_string id in
    Storage.mem db ["keys"; id'] >>= function
      | true -> new_id ()
      | false -> Lwt.return id'

  let get id =
    db_lwt >>= fun db ->
    Storage.find db ["keys"; id]

  let get_all () =
    db_lwt >>= fun db ->
    Storage.list db ["keys"]
    >|= fun l ->
    let f a e =
      match e with
      | (k, `Contents) -> k :: a
      | _ -> a
    in
    List.fold_left f [] l

  let add id e =
    db_lwt >>= fun db ->
    begin
    match id with
      | Some id ->
        begin Storage.mem db ["keys"; id] >>= function
          | true -> failwith_desc "key id already exists"
          | false -> Lwt.return id
        end
      | None -> new_id ()
    end
    >>= fun id ->
    Storage.set db ~info:(info "Adding key") ["keys"; id] e
    >>= fun () ->
    Lwt.return id

  let put id e =
    db_lwt >>= fun db ->
    Storage.mem db ["keys"; id] >>= function
      | false -> Lwt.return false
      | true ->
    Storage.set db ~info:(info "Updating key") ["keys"; id] e
    >>= fun () ->
    Lwt.return true

  let delete id =
    db_lwt >>= fun db ->
    Storage.mem db ["keys"; id] >>= function
      | false -> Lwt.return false
      | true ->
    Storage.remove db ~info:(info "Deleteing key") ["keys"; id]
    >>= fun _ ->
    Lwt.return true
end
