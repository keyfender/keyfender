module GCM = Nocrypto.Cipher_block.AES.GCM
module Rng = Nocrypto.Rng

type encData = {
  iv: Cstruct.t;
  data: Cstruct.t;
} [@@deriving sexp]

module Make
  (K : S.EncKey)
  : S.Encryptor =
struct
  let key = GCM.of_secret K.key

  let decrypt s =
    let { iv ; data } = (Sexplib.Sexp.of_string s |> encData_of_sexp) in
    let res = GCM.decrypt ~key ~iv data in
    let plain = res.GCM.message in
    Cstruct.to_string plain

  let encrypt s =
    let iv = Rng.generate 12 in
    let res = GCM.encrypt ~key ~iv (Cstruct.of_string s) in
    let data = res.GCM.message in
    sexp_of_encData { iv; data } |> Sexplib.Sexp.to_string
end

module Null : S.Encryptor = struct
  let encrypt s = s
  let decrypt s = s
end
