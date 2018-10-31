(* Irmin implementation of the KV constructor *)

module Make
    (KV: Irmin.KV_MAKER)
    (Enc: S.Encryptor)
    (Conf: S.IrminConf)
    : S.KV_Maker
