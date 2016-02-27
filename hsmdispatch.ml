open Lwt.Infix

module YB = Yojson.Basic

module Main (C:V1_LWT.CONSOLE) (FS:V1_LWT.KV_RO) (H:Cohttp_lwt.Server) = struct

  (* Apply the [Webmachine.Make] functor to the Lwt_unix-based IO module
   * exported by cohttp. For added convenience, include the [Rd] module
   * as well so you don't have to go reaching into multiple modules to
   * access request-related information. *)
  module Wm = struct
    module Rd = Webmachine.Rd
    include Webmachine.Make(H.IO)
  end

  (** A resource for querying all the keys in the database via GET and creating
      a new key via POST. Check the [Location] header of a successful POST
      response for the URI of the key. *)
  class keys keyring = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private to_json rd =
      Keyring.get_all keyring
      >|= List.map (fun (id, key) -> `Assoc [
          ("location", `String ("/keys/" ^ (string_of_int id)));
          ("key", Keyring.json_of_pub key)
        ])
      >>= fun json_l ->
        let json_s = YB.pretty_to_string (`List json_l) in
        Wm.continue (`String json_s) rd

    method allowed_methods rd =
      Wm.continue [`GET; `HEAD; `POST] rd

    method content_types_provided rd =
      Wm.continue [
        "application/json", self#to_json
      ] rd

    method content_types_accepted rd =
      Wm.continue [] rd

    method process_post rd =
      Cohttp_lwt_body.to_string rd.Wm.Rd.req_body >>= fun body ->
      let json = YB.from_string body in
      let key = Keyring.priv_of_json json in
      Keyring.add keyring key >>= fun new_id ->
      let rd' = Wm.Rd.redirect ("/keys/" ^ (string_of_int new_id)) rd in
      Wm.continue true rd'
  end

  (** A resource for querying an individual key in the database by id via GET,
      modifying an key via PUT, and deleting an key via DELETE. *)
  class key keyring = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private of_json rd =
      Cohttp_lwt_body.to_string rd.Wm.Rd.req_body
      >>= fun body ->
        let json = YB.from_string body in
        let key = Keyring.priv_of_json json in
        Keyring.put keyring (self#id rd) key
      >>= fun modified ->
        let resp_body =
          if modified
            then `String "{\"status\":\"ok\"}"
            else `String "{\"status\":\"not found\"}"
        in
        Wm.continue modified { rd with Wm.Rd.resp_body }

    method private to_json rd =
      Keyring.get keyring (self#id rd)
      >>= function
        | None            -> assert false
        | Some key -> let json = Keyring.json_of_pub key in
          let json_s = YB.pretty_to_string json in
          Wm.continue (`String json_s) rd

    method private to_pem rd =
      Keyring.get keyring (self#id rd)
      >>= function
        | None            -> assert false
        | Some key -> let pem = Keyring.pem_of_pub key in
          Wm.continue (`String pem) rd


    method allowed_methods rd =
      Wm.continue [`GET; `HEAD; `PUT; `DELETE] rd

    method resource_exists rd =
      Keyring.get keyring (self#id rd)
      >>= function
        | None   -> Wm.continue false rd
        | Some _ -> Wm.continue true rd

    method content_types_provided rd =
      Wm.continue [
        "application/json", self#to_json;
        "application/x-pem-file", self#to_pem
      ] rd

    method content_types_accepted rd =
      Wm.continue [
        "application/json", self#of_json
      ] rd

    method delete_resource rd =
      Keyring.del keyring (self#id rd)
      >>= fun deleted ->
        let resp_body =
          if deleted
            then `String "{\"status\":\"ok\"}"
            else `String "{\"status\":\"not found\"}"
        in
        Wm.continue deleted { rd with Wm.Rd.resp_body }

    method private id rd =
      int_of_string (Wm.Rd.lookup_path_info_exn "id" rd)
  end

  (** A resource for querying an individual key in the database by id via GET,
      modifying an key via PUT, and deleting an key via DELETE. *)
  class pem_key keyring = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private to_pem rd =
      Keyring.get keyring (self#id rd)
      >>= function
        | None            -> assert false
        | Some key -> let pem = Keyring.pem_of_pub key in
          Wm.continue (`String pem) rd


    method allowed_methods rd =
      Wm.continue [`GET] rd

    method resource_exists rd =
      Keyring.get keyring (self#id rd)
      >>= function
        | None   -> Wm.continue false rd
        | Some _ -> Wm.continue true rd

    method content_types_provided rd =
      Wm.continue [
        "application/x-pem-file", self#to_pem
      ] rd

    method content_types_accepted rd =
      Wm.continue [] rd

    method private id rd =
      int_of_string (Wm.Rd.lookup_path_info_exn "id" rd)
  end

  (** A resource for querying system config *)
  class status = object(self)
    inherit [Cohttp_lwt_body.t] Wm.resource

    method private to_json rd =
      Wm.continue (`String "{\"status\":\"ok\"}") rd

    method allowed_methods rd =
      Wm.continue [`GET] rd

    method content_types_provided rd =
      Wm.continue [
        "application/json", self#to_json
      ] rd

    method content_types_accepted rd =
      Wm.continue [] rd
  end

  let start c fs http =
    (* initialize random number generator *)
    let _ = Nocrypto_entropy_lwt.initialize () in
    (* listen on port 8080 *)
    let port = 8080 in
    (* create the database *)
    let keyring = Keyring.create () in
    (* the route table *)
    let routes = [
      ("/keys", fun () -> new keys keyring) ;
      ("/keys/:id", fun () -> new key keyring) ;
      ("/keys/:id/public", fun () -> new key keyring) ;
      ("/keys/:id/public.pem", fun () -> new pem_key keyring) ;
      ("/system/status", fun () -> new status) ;
    ] in
    let callback conn_id request body =
      let open Cohttp in
      (* Perform route dispatch. If [None] is returned, then the URI path did not
       * match any of the route patterns. In this case the server should return a
       * 404 [`Not_found]. *)
      Wm.dispatch' routes ~body ~request
      >|= begin function
        | None        -> (`Not_found, Header.init (), `String "Not found", [])
        | Some result -> result
      end
      >>= fun (status, headers, body, path) ->
        (* If you'd like to see the path that the request took through the
         * decision diagram, then run this example with the [DEBUG_PATH]
         * environment variable set. This should suffice:
         *
         *  [$ DEBUG_PATH= ./crud_lwt.native]
         *
         *)
        let path =
          match Sys.getenv "DEBUG_PATH" with
          | _ -> Printf.sprintf " - %s" (String.concat ", " path)
          | exception Not_found   -> ""
        in
        Printf.eprintf "%d - %s %s%s\n"
          (Code.code_of_status status)
          (Code.string_of_method (Request.meth request))
          (Uri.path (Request.uri request))
          path;
        (* Finally, send the response to the client *)
        H.respond ~headers ~body ~status ()
    in
    (* create the server and handle requests with the function defined above *)
    let conn_closed (_,conn_id) =
      let cid = Cohttp.Connection.to_string conn_id in
      C.log c (Printf.sprintf "conn %s closed" cid)
    in
    http (`TCP port) (H.make ~conn_closed ~callback ())
end
