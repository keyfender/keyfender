open Lwt.Infix
open Protocol_j

module Make(Client: Cohttp_lwt.S.Client) = struct

  type result = (response, error) Result.result
  type key = string list

  let path_string path =
    String.concat "/" path

  let make_path key =
    let base = "/v2/keys" in
    let kp = path_string key in
    Fmt.strf "%s/%s" base kp

  let make_uri host key =
    let path = make_path key in
    Printf.eprintf "Uri: %s\n" (host ^ path);
    Uri.of_string (host ^ path)

  let ttl_param ttl =
    match ttl with
    | Some x ->
      [( "ttl", [(string_of_int x)] )  ]
    | _ -> []

  let decode_response body =
    Cohttp_lwt.Body.to_string body >>= fun s ->
    let f () =
      let x = response_of_string s in
      Result.Ok x |> Lwt.return 
    in
    Lwt.catch (fun () -> f ()) (fun _ -> Result.Error (error_of_string s) |> Lwt.return )

  let follow_redirect f uri =
    f uri >>= fun (rep, body) ->
    let is_redir =
      rep
      |> Cohttp.Response.status
      |> Cohttp.Code.code_of_status
      |> Cohttp.Code.is_redirection
    in
    if is_redir then
      let h = Cohttp.Response.headers rep in 
      let loc =  Cohttp.Header.get h "location"  in
      match loc with
      | Some u ->
        print_endline "redirecting";
        let nuri = Uri.of_string u |> Uri.resolve "http" uri in
        f nuri 
      | None -> Lwt.fail_with "Couldn't follow redirect"
    else
      Lwt.return (rep, body)

  let put host key ?ttl v =
    let uri = make_uri host key in
    let ttl_p = ttl_param ttl in
    let params = [("value", [v])] @ ttl_p in
    let headers = Cohttp.Header.init_with
      "content-type" "application/x-www-form-urlencoded" in
    let body = Cohttp_lwt.Body.of_string (Uri.encoded_of_query params) in
    let f u = Client.put ~chunked:false ~body ~headers u in
    follow_redirect f uri >>= fun (_, body) ->
    decode_response body

  let get host key =
    let uri = make_uri host key in
    Client.get uri >>= fun (_, body) ->
    decode_response body

  let delete host key =
    let base = make_uri host key in
    let params = [("dir", ["true"]); ("recursive", ["true"])] in
    let uri = Uri.with_query base params in
    let f u = Client.delete u in
    follow_redirect f uri >>= fun (_, body) ->
    decode_response body

  let create_dir host key ?ttl () =
    let base = make_uri host key in
    print_endline (Uri.to_string base);
    let ttl_p = ttl_param ttl in
    let params =
      [
        ("dir", ["true"]) 
      ] @ ttl_p
    in
    let uri = Uri.with_query base params in
    let f url = Client.put url in
    follow_redirect f uri >>= fun (_, body) ->
    decode_response body

  let refresh host key ~ttl () =
    let path = make_path key in
    let uri = Uri.make ~host ~path () in
    let ttl_p = string_of_int ttl in
    let params =
      [
        ("refresh", ["true"]);
        ("ttl", [ttl_p]);
        ("prevExist", ["true"])
      ]
    in
    Client.post_form uri ~params >>= fun (_, body) ->
    decode_response body

  let list host key ?r:(r=false) () =
    let base = make_uri host key in  
    let query =
      [ ("recursive", [ (string_of_bool r) ] ) ]
    in
    let uri = Uri.with_query base query in
    Client.get uri >>= fun (_, body) ->
    decode_response body 

  let rm_dir host key =
    let base = make_uri host key in
    let query = [("dir", ["true"])] in
    let uri = Uri.with_query base query in
    Client.delete uri >>= fun (_, body) ->
    decode_response body

  let watch host key cb ?times:(ct = 1) () =
    let make_uri i () = 
      let base = make_uri host key in
      let common_q = [  ("wait", ["true"])  ] in  

      let query =
        match i with
        | Some x ->
          let index_q =
            [
              ("waitIndex", [   (string_of_int x) ]  )
            ]
          in 
          common_q @ index_q

        | None -> common_q 
      in
      Uri.with_query base query
    in
    let rec handle_watch ctr ?i () =
      if ctr > 1 then
        let url = make_uri i () in
        Client.get url >>= fun (_, body) ->
        decode_response body >>= fun res ->
        match res with
        | Result.Ok rep ->
          let i0 = rep.node.modifiedIndex in
          let (i1, ctr0) = (i0 + 1), (ctr - 1) in
          cb rep >>= fun _ ->
          handle_watch ctr0 ~i:i1 ()
        | Result.Error e ->
          let emsg = Fmt.strf "Error %s the cause was %s " e.message e.cause in 
          Lwt.fail_with emsg
      else
        let url = make_uri i () in
        Client.get url >>= fun (_, body) ->
        decode_response body >>= fun res ->
        match res with
        | Result.Ok rep -> cb rep
        | Result.Error e ->
          let emsg = Fmt.strf "Error %s the cause was %s " e.message e.cause in 
          Lwt.fail_with emsg
    in  
    handle_watch ct ()

end
