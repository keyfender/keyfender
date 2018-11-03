(* Database interface to Etcd *)

open Lwt.Infix
open Util

module Make
    (Client: Cohttp_lwt.S.Client)
    (Enc: S.Encryptor)
    (Conf: S.EtcdConf)
= functor (Val: S.SexpConvertable) -> struct
  type v = Val.t
  let host = Conf.host
  module Etcd = Etcd_client.Make(Client)

  (* let id  = ref 0 *)
  let rec new_id () =
    let n = 20 in
    let alphanum =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" in
    let len = String.length alphanum in
    let id = Bytes.create n in
    for i=0 to pred n do
      Bytes.set id i alphanum.[Random.int len]
    done;
    let id' = Bytes.to_string id in
    Etcd.get host ["keys"; id'] >>= function
      | Ok _ -> new_id ()
      | Error _ -> Lwt.return id'

  let get id =
    Etcd.get host ["keys"; id] >>= function
    | Error e -> begin match e.Protocol_j.errorCode with
      | 100 -> Lwt.return_none
      | _ -> Lwt.fail_with (Protocol_j.string_of_error e)
      end
    | Ok r -> match Protocol_j.(r.node.value) with
      | None -> Lwt.return_none
      | Some v -> Enc.decrypt v |> Sexplib.Sexp.of_string |> Val.t_of_sexp
        |> Lwt.return_some

  let get_all () =
    Etcd.list ~r:true host ["keys"] () >>= function
    | Error e -> begin match e.Protocol_j.errorCode with
      | 100 -> Lwt.return []
      | _ -> Lwt.fail_with (Protocol_j.string_of_error e)
      end
    | Ok r -> match Protocol_j.(r.node.nodes) with
      | None -> Lwt.return []
      | Some l ->
        let rec f acc elem = match elem.Protocol_j.dir with
          | Some true -> begin match elem.Protocol_j.nodes with
            | None -> acc
            | Some l -> List.fold_left f acc l
            end
          | _ ->
            let k = elem.Protocol_j.key in
            String.sub k 6 (String.length k - 6) :: acc
        in
        let l = List.fold_left f [] l in
        Lwt.return l

  let add id value =
    begin
    match id with
      | Some id ->
        begin Etcd.get host ["keys"; id] >>= function
          | Ok _ -> failwith_desc "key id already exists"
          | Error e -> match e.Protocol_j.errorCode with
            | 100 -> Lwt.return id
            | _ -> failwith_json_string (Protocol_j.string_of_error e)
        end
      | None -> new_id ()
    end
    >>= fun id ->
    let v = Val.sexp_of_t value |> Sexplib.Sexp.to_string |> Enc.encrypt in
    Etcd.put host ["keys"; id] v >>= function
    | Error e -> failwith_json_string (Protocol_j.string_of_error e)
    | Ok _ -> Lwt.return id

  let put id value =
    Etcd.get host ["keys"; id] >>= function
    | Error e -> failwith_json_string (Protocol_j.string_of_error e)
    | Ok _ ->
    let v = Val.sexp_of_t value |> Sexplib.Sexp.to_string |> Enc.encrypt in
    Etcd.put host ["keys"; id] v >>= function
    | Error e -> failwith_json_string (Protocol_j.string_of_error e)
    | Ok _ -> Lwt.return true

  let delete id =
    Etcd.delete host ["keys"; id]>>= function
    | Error e -> begin match e.Protocol_j.errorCode with
      | 100 -> Lwt.return_false
      | _ -> Lwt.fail_with (Protocol_j.string_of_error e)
      end
    | Ok _ -> Lwt.return_true
end
