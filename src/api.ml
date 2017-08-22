open Lwt.Infix

module YB = Yojson.Basic

(* Logging *)
let api_src = Logs.Src.create "api" ~doc:"HSM REST API"
module Api_log = (val Logs.src_log api_src : Logs.LOG)

let api_prefix = "/api/v0"

module Hash = struct
  let paths = [
    "md5", `MD5;
    "sha1", `SHA1;
    "sha224", `SHA224;
    "sha256", `SHA256;
    "sha384", `SHA384;
    "sha512", `SHA512;
  ]
end

module Padding = struct
  type t =
    | None
    | PKCS1
    | OAEP
    | PSS
  let paths = [
    "pkcs1", PKCS1;
    "oaep", OAEP;
    "pss", PSS;
  ]
end

module Action = struct
  type t =
    | Decrypt
    | Sign
  let paths = [
    "decrypt", Decrypt ;
    "sign", Sign ;
  ]
end

module Config = struct
  type t =
    | Pass_admin
    | Pass_user
  let settings = [
    Pass_admin, ref "";
    Pass_user, ref "";
  ]
  let set k v =
    try let r = List.assoc k settings in
      r := v ; true
    with Not_found -> false
  let get k =
    try Some !(List.assoc k settings)
    with Not_found -> None
end

let jsend_success data =
  let l = match data with
    | `Null -> []
    | d -> ["data", d]
  in
  `Assoc (("status", `String "success") :: l)

let jsend_failure data =
  let l = match data with
    | `Null -> []
    | d -> ["data", d]
  in
  `Assoc (("status", `String "failure") :: l)

let jsend_error msg =
  `Assoc [
    ("status", `String "error");
    ("message", `String msg)
  ]

let jsend = function
  | Keyring.Ok json -> jsend_success json
  | Keyring.Failure json -> jsend_failure json

module Dispatch (H:Cohttp_lwt.Server) = struct

  (* Apply the [Webmachine.Make] functor to the Lwt_unix-based IO module
   * exported by cohttp. For added convenience, include the [Rd] module
   * as well so you don't have to go reaching into multiple modules to
   * access request-related information. *)
  module Wm = struct
    module Rd = Webmachine.Rd
    include Webmachine.Make(H.IO)
  end

  let has_valid_credentials ~admin rd =
    try
      match Cohttp.Header.get_authorization rd.Wm.Rd.req_headers with
        | None -> raise Not_found
        | Some credentials ->
      match credentials with
        | `Other _ -> raise Not_found
        | `Basic (id, pass) ->
      if match (admin, id) with
        | _, "admin" -> pass <> "" && Some pass = Config.get Config.Pass_admin
        | false, _ -> pass <> "" && Some pass = Config.get Config.Pass_user
        | true, _ -> false
      then `Authorized
      else raise (Failure "wrong password")
    with _ -> `Basic "keyfender"

  let is_authorized_as_user rd = has_valid_credentials ~admin:false rd
  let is_authorized_as_admin rd = has_valid_credentials ~admin:true rd

  let add_common_headers (headers: Cohttp.Header.t): Cohttp.Header.t =
    Cohttp.Header.add_list headers [
      ("access-control-allow-origin", "*");
      ("access-control-allow-headers", "Accept, Content-Type, Authorization");
      ("access-control-allow-methods", "GET, HEAD, POST, DELETE, OPTIONS, PUT, PATCH")
    ]


  (** A resource for querying all the keys in the database via GET and creating
      a new key via POST. Check the [Location] header of a successful POST
      response for the URI of the key. *)
  class keys keyring = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private to_json rd =
      Keyring.get_all keyring
      >|= List.map (fun id -> `Assoc [
          ("id", `String id);
          ("location", `String (api_prefix ^ "/keys/" ^ Uri.pct_encode id))
        ])
      >>= fun json_l ->
        let json_s = jsend_success (`List json_l)
          |> YB.pretty_to_string ~std:true in
        Wm.continue (`String json_s) rd

    method! allowed_methods rd =
      Wm.continue [`GET; `HEAD; `POST] rd

    method! is_authorized rd =
      let result = match rd.Wm.Rd.meth with
        | `GET -> is_authorized_as_user rd
        | _ -> is_authorized_as_admin rd
      in
      Wm.continue result rd

    method content_types_provided rd =
      Wm.continue [
        "application/json", self#to_json
      ] rd

    method content_types_accepted rd =
      Wm.continue [] rd

    method! process_post rd =
      try
        Cohttp_lwt_body.to_string rd.Wm.Rd.req_body >>= fun body ->
        let key = YB.from_string body in
        Keyring.add keyring ~key
        >>= function
          | Keyring.Ok new_id ->
            let resp_body = `String (jsend_success (`Assoc [
                ("id", `String new_id);
                ("location", `String (api_prefix ^ "/keys/" ^ Uri.pct_encode new_id))
              ]) |> YB.pretty_to_string ~std:true)
            in
            Wm.continue true { rd with Wm.Rd.resp_body }
          | Keyring.Failure json ->
            let body =
              `String (jsend_failure json |> YB.pretty_to_string ~std:true) in
            Wm.respond ~body 400 rd
      with
        | e ->
          let json = Printexc.to_string e |> jsend_error in
          let resp_body = `String (YB.pretty_to_string ~std:true json) in
          Wm.continue false { rd with Wm.Rd.resp_body }
  end

  (** A resource for querying an individual key in the database by id via GET,
      modifying an key via PUT, and deleting an key via DELETE. *)
  class key keyring = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private of_json rd =
      begin try
        Cohttp_lwt_body.to_string rd.Wm.Rd.req_body
        >>= fun body ->
          let key = YB.from_string body in
          let id = self#id rd in
          Keyring.put keyring ~id ~key
        >|= function
          | Keyring.Ok true -> jsend_success `Null
          | Keyring.Ok false -> assert false (* can't happen, because of
                                                resource_exists *)
          | Keyring.Failure json -> jsend_failure json
      with
        | e -> Lwt.return (Printexc.to_string e |> jsend_error)
      end
      >>= fun jsend ->
      let resp_body =
        `String (YB.pretty_to_string ~std:true jsend)
      in
      Wm.continue true { rd with Wm.Rd.resp_body }

    method private to_json rd =
      let id = self#id rd in
      Keyring.get keyring ~id
      >>= function
        | None     -> assert false
        | Some key -> let json = Keyring.json_of_pub id key in
          let json_s = jsend_success json |> YB.pretty_to_string ~std:true in
          Wm.continue (`String json_s) rd

    method private to_pem rd =
      let id = self#id rd in
      Keyring.get keyring ~id
      >>= function
        | None     -> assert false
        | Some key -> let pem = Keyring.pem_of_pub key in
          Wm.continue (`String pem) rd

    method! allowed_methods rd =
      Wm.continue [`GET; `HEAD; `PUT; `DELETE] rd

    method! resource_exists rd =
      let id = self#id rd in
      Keyring.get keyring ~id
      >>= function
        | None   -> Wm.continue false rd
        | Some _ -> Wm.continue true rd

    method! is_authorized rd =
      let result = match rd.Wm.Rd.meth with
        | `GET -> is_authorized_as_user rd
        | _ -> is_authorized_as_admin rd
      in
      Wm.continue result rd

    method content_types_provided rd =
      Wm.continue [
        "application/json", self#to_json;
        "application/x-pem-file", self#to_pem
      ] rd

    method content_types_accepted rd =
      Wm.continue [
        "application/json", self#of_json
      ] rd

    method! delete_resource  rd =
      let id = self#id rd in
      Keyring.del keyring ~id
      >>= fun deleted ->
        let resp_body =
          if deleted
            then `String (jsend_success `Null |> YB.pretty_to_string ~std:true)
            else assert false (* can't happen, because of resource_exists *)
        in
        Wm.continue deleted { rd with Wm.Rd.resp_body }

    method private id rd =
      Uri.pct_decode (Wm.Rd.lookup_path_info_exn "id" rd)
  end

  (** A resource for querying an individual key in the database by id via GET,
      modifying an key via PUT, and deleting an key via DELETE. *)
  class pem_key keyring = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private to_pem rd =
      let id = self#id rd in
      Keyring.get keyring ~id
      >>= function
        | None            -> assert false
        | Some key -> let pem = Keyring.pem_of_pub key in
          Wm.continue (`String pem) rd

    method! allowed_methods rd =
      Wm.continue [`GET] rd

    method! resource_exists rd =
      let id = self#id rd in
      Keyring.get keyring ~id
      >>= function
        | None   -> Wm.continue false rd
        | Some _ -> Wm.continue true rd

    method! is_authorized rd =
      let result = match rd.Wm.Rd.meth with
        | `GET -> is_authorized_as_user rd
        | _ -> is_authorized_as_admin rd
      in
      Wm.continue result rd

    method content_types_provided rd =
      Wm.continue [
        "application/x-pem-file", self#to_pem
      ] rd

    method content_types_accepted rd =
      Wm.continue [] rd

    method private id rd =
      Uri.pct_decode (Wm.Rd.lookup_path_info_exn "id" rd)
  end

  (** A resource for executing actions on keys via POST. Parameters for the
      actions are sent in a JSON body, and the result is returned with a JSON
      body as well. *)
  class key_actions keyring = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method! allowed_methods rd =
      Wm.continue [`POST] rd

    method content_types_provided rd =
      Wm.continue [
        "application/json", Wm.continue (`Empty);
      ] rd

    method content_types_accepted rd =
      Wm.continue [] rd

    method! resource_exists rd =
      let id = self#id rd in
      Keyring.get keyring ~id
      >>= function
      | None   -> Wm.continue false rd
      | Some _ ->
      try
        let _ = self#action_dispatch_exn rd in
        Wm.continue true rd
      with
      | Failure msg ->
        let resp_body = `String (jsend_error msg |> YB.pretty_to_string ~std:true) in
        Wm.continue false { rd with Wm.Rd.resp_body }
      | e ->
        let resp_body = `String begin
          Printexc.to_string e
          |> jsend_error
          |> YB.pretty_to_string ~std:true
        end in
        Wm.continue false { rd with Wm.Rd.resp_body }

    method! is_authorized rd =
      Wm.continue (is_authorized_as_user rd) rd

    method! process_post rd =
      begin try
        Cohttp_lwt_body.to_string rd.Wm.Rd.req_body
        >>= fun body ->
        let data = YB.from_string body in
        self#action_dispatch_exn rd ~data
        >|= jsend
      with
        | e -> Lwt.return (Printexc.to_string e |> jsend_error)
      end
      >>= fun jsend ->
      let resp_body = `String (YB.pretty_to_string ~std:true jsend) in
      Wm.continue true { rd with Wm.Rd.resp_body }

    method private action_dispatch_exn rd =
      let id = self#id rd in
      let action = self#action rd in
      let padding = self#padding rd in
      let hash_type = self#hash_type rd in
      match (action, padding, hash_type) with
        | Action.Decrypt, Padding.None,   `None ->
          let padding = Keyring.Padding.None in
          Keyring.decrypt keyring ~id ~padding
        | Action.Decrypt, Padding.PKCS1,  `None ->
          let padding = Keyring.Padding.PKCS1 in
          Keyring.decrypt keyring ~id ~padding
        | Action.Sign,    Padding.PKCS1,  `None ->
          let padding = Keyring.Padding.PKCS1 in
          Keyring.sign keyring ~id ~padding
        | Action.Decrypt, Padding.OAEP,   (#Nocrypto.Hash.hash as h) ->
          let padding = Keyring.Padding.OAEP h in
          Keyring.decrypt keyring ~id ~padding
        | Action.Sign,    Padding.PSS,    (#Nocrypto.Hash.hash as h) ->
          let padding = Keyring.Padding.PSS h in
          Keyring.sign keyring ~id ~padding
        | _, _, #Nocrypto.Hash.hash
        | _, _, `None
          -> raise @@ Failure "invalid action resource"

    method private id rd =
      Uri.pct_decode (Wm.Rd.lookup_path_info_exn "id" rd)

    method private action rd =
      let action_str = Wm.Rd.lookup_path_info_exn "action" rd in
      try List.assoc action_str Action.paths
      with Not_found
        -> raise @@ Failure ("invalid action: " ^ action_str)

    method private padding rd =
      match Wm.Rd.lookup_path_info_exn "padding" rd with
      | exception _ -> Padding.None (* no padding path *)
      | padding_str ->
      try List.assoc padding_str Padding.paths
      with Not_found
        -> raise @@ Failure ("invalid padding: " ^ padding_str)

    method private hash_type rd =
      match Wm.Rd.lookup_path_info_exn "hash_type" rd with
      | exception _ -> `None
      | hash_type_str ->
      try List.assoc hash_type_str Hash.paths
      with Not_found
        -> raise @@ Failure ("invalid hash type: " ^ hash_type_str)

  end (* key actions *)

  (** A resource for passwords *)
  class passwords = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private of_json rd =
      begin try
        Cohttp_lwt_body.to_string rd.Wm.Rd.req_body
        >>= fun body ->
          let new_password = YB.from_string body
            |> YB.Util.member "newPassword"
            |> YB.Util.to_string
          in
          let uid = self#uid rd in
          if match uid with
            | "admin" -> Config.set Config.Pass_admin new_password
            | _ -> Config.set Config.Pass_user new_password
          then Lwt.return (jsend_success `Null)
          else assert false (* can't happen *)
      with
        | _ -> Lwt.return (jsend_failure (`Assoc [
          ("description", `String "JSON keys are missing");
          ("missing", `List [`String "newPassword"])
        ]))
      end
      >>= fun jsend ->
      let resp_body =
        `String (YB.pretty_to_string ~std:true jsend)
      in
      Wm.continue true { rd with Wm.Rd.resp_body }

    method! allowed_methods rd =
      Wm.continue [`PUT] rd

    method! is_authorized rd =
      let uid = self#uid rd in
      let pw_set = Some "" <> Config.get Config.Pass_admin in
      let result = match (uid, pw_set) with
        | "admin", true -> is_authorized_as_admin rd
        | "admin", false -> `Authorized
        | _, _ -> is_authorized_as_user rd
      in
      Wm.continue result rd

    method content_types_provided rd =
      Wm.continue [
        "application/json", Wm.continue (`Empty);
      ] rd

    method content_types_accepted rd =
      Wm.continue [
        "application/json", self#of_json
      ] rd

    method private uid rd =
      Wm.Rd.lookup_path_info_exn "uid" rd
  end

  (** A resource for querying system status *)
  class status = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private to_json rd =
      Wm.continue (`String "{\n  \"status\": \"ok\"\n}\n") rd

    method! allowed_methods rd =
      Wm.continue [`GET] rd

    method content_types_provided rd =
      Wm.continue [
        "application/json", self#to_json
      ] rd

    method content_types_accepted rd =
      Wm.continue [] rd
  end

  (** A resource for providing product information *)
  class information = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private to_json rd =
      Wm.continue (`String "{\"vendor\":\"keyfender\",\"product\":\"keyfender\",\"version\":\"0.1\"}") rd

    method! allowed_methods rd =
      Wm.continue [`GET] rd

    method content_types_provided rd =
      Wm.continue [
        "application/json", self#to_json
      ] rd

    method content_types_accepted rd =
      Wm.continue [] rd
  end

  let dispatcher keyring request body =
    let open Cohttp in
    (* Perform route dispatch. If [None] is returned, then the URI path did
    not match any of the route patterns. In this case the server should
    return a 404 [`Not_found]. *)
    let routes = [
      (api_prefix ^ "/keys", fun () -> new keys keyring) ;
      (api_prefix ^ "/keys/:id", fun () -> new key keyring) ;
      (api_prefix ^ "/keys/:id/public", fun () -> new key keyring) ;
      (api_prefix ^ "/keys/:id/public.pem", fun () -> new pem_key keyring) ;
      (api_prefix ^ "/keys/:id/actions/:action",
        fun () -> new key_actions keyring) ;
      (api_prefix ^ "/keys/:id/actions/:padding/:action",
        fun () -> new key_actions keyring) ;
      (api_prefix ^ "/keys/:id/actions/:padding/:hash_type/:action",
        fun () -> new key_actions keyring) ;
        (api_prefix ^ "/system/status", fun () -> new status) ;
        (api_prefix ^ "/system/information", fun () -> new information) ;
      (api_prefix ^ "/system/passwords/:uid", fun () -> new passwords) ;
    ] in
    let meth = Request.meth request in
    begin match meth with
      | `OPTIONS -> Lwt.return (Some (`OK, Header.init (), `Empty, [])) (* OPTIONS always ok *)
      | _ -> Wm.dispatch' routes ~body ~request
    end
    >|= begin function
      | None        -> (`Not_found, Header.init (), `String "Not found", [])
      | Some result -> result
    end
    >>= fun (status, headers, body, path) ->
      let headers = add_common_headers headers in
      Api_log.info (fun f -> f "%d - %s %s"
        (Code.code_of_status status)
        (Code.string_of_method (Request.meth request))
        (Uri.path (Request.uri request)));
      Api_log.debug (fun f ->
        f "Webmachine path: %s" (String.concat ", " path));
      Api_log.debug (fun f ->
        f "Response header:\n%s" (Header.to_string headers));
      Api_log.debug (fun f ->
        let resp_body = match body with
          | `Empty | `String _ | `Strings _ as x -> Body.to_string x
          | `Stream _ -> "__STREAM__"
        in
        f "Response body:\n%s" resp_body);
      (* Finally, send the response to the client *)
      H.respond ~headers ~body ~status ()
end
