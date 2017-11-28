module Sexp(
  T : S.Sexp
) : Irmin.Contents.S with type t = T.t =
struct
  type t = T.t
  let to_raw (x:t) = Marshal.to_string x []
  let of_raw x : t = Marshal.from_string x 0
  let t = Irmin.Type.(like string) of_raw to_raw
  let merge = Irmin.Merge.idempotent (Irmin.Type.option t)
  let of_string s =
    Ok (T.t_of_sexp @@ Sexplib.Sexp.of_string s)
  let to_string x =
    Sexplib.Sexp.to_string @@ T.sexp_of_t x
  let pp ppf x =
    Fmt.pf ppf "%s" (to_string x)
end
