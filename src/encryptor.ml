module GCM = Nocrypto.Cipher_block.AES.GCM
module Rng = Nocrypto.Rng
module YB = Yojson.Basic

let cs_of_json_val json key =
  YB.Util.member key json
  |> YB.Util.to_string
  |> B64.decode ~alphabet:B64.uri_safe_alphabet
  |> Cstruct.of_string

let b64_of_cs cs = Cstruct.to_string cs
  |> B64.encode ~alphabet:B64.uri_safe_alphabet

module Make
  (K : S.EncKey)
  : S.Encryptor =
struct
  let key = GCM.of_secret K.key

  let decrypt s =
    let json = YB.from_string s in
    let iv = cs_of_json_val json "iv" in
    let tag = cs_of_json_val json "tag" in
    let data = cs_of_json_val json "data" in
    let res = GCM.decrypt ~key ~iv data in
    match (Cstruct.equal tag res.GCM.tag) with
    | false -> raise (Failure "AES/GCM authentication failed")
    | true -> Cstruct.to_string res.GCM.message

  let encrypt s =
    let iv = Rng.generate 12 in
    let res = GCM.encrypt ~key ~iv (Cstruct.of_string s) in
    let json = (`Assoc [
      ("iv", `String (b64_of_cs iv));
      ("tag", `String (b64_of_cs res.GCM.tag));
      ("data", `String (b64_of_cs res.GCM.message))
    ]) in
    YB.to_string json
end

module Null : S.Encryptor = struct
  let encrypt s = s
  let decrypt s = s
end
