
module Raw (T : sig type t end) =
struct
  type t = T.t
  let to_raw (x:t) = Marshal.to_string x []
  let of_raw x : t = Marshal.from_string x 0
  let t = Irmin.Type.(like string) of_raw to_raw
  let merge = Irmin.Merge.idempotent (Irmin.Type.option t)
end

module Sexp
  (T : S.SexpConvertable)
: Irmin.Contents.S with type t = T.t =
struct
  include Raw(T)
  let of_string s =
    Ok (T.t_of_sexp @@ Sexplib.Sexp.of_string s)
  let to_string x =
    Sexplib.Sexp.to_string @@ T.sexp_of_t x
  let pp ppf x =
    Fmt.pf ppf "%s" (to_string x)
end

module EncryptedSexp
  (T : S.SexpConvertable)
  (E : S.Encryptor)
: Irmin.Contents.S with type t = T.t =
struct
  include Raw(T)
  let of_string s =
    Ok (E.decrypt s |> Sexplib.Sexp.of_string |> T.t_of_sexp)
  let to_string x =
    T.sexp_of_t x |> Sexplib.Sexp.to_string |> E.encrypt
  let pp ppf x =
    Fmt.pf ppf "%s" (to_string x)
end
