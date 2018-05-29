module Sexp
  ( T : S.SexpConvertable )
  : Irmin.Contents.S with type t = T.t

module EncryptedSexp
  ( T : S.SexpConvertable )
  ( E : S.Encryptor)
  : Irmin.Contents.S with type t = T.t
