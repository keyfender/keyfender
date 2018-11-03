exception Failure_exn of Yojson.Basic.json

let failwith_json j =
  raise (Failure_exn j)

let failwith_desc desc =
  failwith_json (`Assoc [
    ("description", `String desc)
  ])

let failwith_json_string s =
  let json = Yojson.Basic.from_string s in
  failwith_json json

let failwith_missing keys =
  let l = List.map (fun x -> `String x) keys in
  failwith_json (`Assoc [
    ("description", `String "JSON keys are missing");
    ("missing", `List l)
  ])

let rem_opt = function
  | Some x -> x
  | None -> assert false

let b64_encode = B64.encode ~alphabet:B64.uri_safe_alphabet

let b64_decode = B64.decode ~alphabet:B64.uri_safe_alphabet

let b64_of_z z =
  b64_encode (Cstruct.to_string (Nocrypto.Numeric.Z.to_cstruct_be z))

let z_of_b64 s =
  Nocrypto.Numeric.Z.of_cstruct_be (Cstruct.of_string (b64_decode s))

let string_of_json_key json key =
  try
    Yojson.Basic.Util.member key json |> Yojson.Basic.Util.to_string
  with
    Yojson.Basic.Util.Type_error _ -> failwith_missing [key]
